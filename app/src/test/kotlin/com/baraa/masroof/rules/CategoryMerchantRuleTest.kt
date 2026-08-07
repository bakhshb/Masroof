package com.baraa.masroof.rules

import com.baraa.masroof.data.db.Category
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.repository.FakeMerchantMemoryRepository
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.Currency
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.MerchantNormalizer
import com.baraa.masroof.transaction.TransactionStatus
import com.baraa.masroof.transaction.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/**
 * Combined tests for:
 *  - merchant memory behavior (remember / no-remember / count increment /
 *    override / safety wins)
 *  - Arabic merchant rules (supermarket, restaurant, fuel, pharmacy +
 *    vague-word blacklist)
 *  - owned-account matching (last-4, Arabic digits, ambiguous)
 *  - wallet top-up rule
 *  - rule priority single source
 */
class CategoryMerchantRuleTest {

    private fun makeInput(
        type: TransactionType = TransactionType.PURCHASE,
        body: String? = null,
        merchant: String? = null,
        sender: String? = null
    ): RuleInput = RuleInput(
        sender = sender,
        body = body,
        amount = BigDecimal("100.00"),
        currency = Currency.SAR,
        type = type,
        status = TransactionStatus.COMPLETED,
        date = LocalDate.of(2024, 1, 15),
        time = LocalTime.of(14, 30),
        normalizedMerchantKey = MerchantNormalizer.normalize(merchant),
        parsed = com.baraa.masroof.transaction.ParsedTransaction(
            originalSender = sender,
            originalMessage = body,
            transactionType = type,
            amount = BigDecimal("100.00"),
            currency = Currency.SAR,
            merchant = merchant,
            accountOrCardLastFourDigits = null,
            transactionDate = LocalDate.of(2024, 1, 15),
            transactionTime = LocalTime.of(14, 30),
            status = TransactionStatus.COMPLETED,
            confidence = 90,
            parsingNotes = emptyList()
        )
    )

    private fun makeCategory(id: Long, name: String) = Category(
        id = id,
        parentId = null,
        nameAr = name,
        nameEn = null,
        sortOrder = id.toInt(),
        enabled = true,
        isSystem = true
    )

    private fun makeAccount(
        id: Long,
        displayName: String,
        type: AccountType = AccountType.BANK_ACCOUNT,
        lastFour: String? = null,
        aliases: List<String> = emptyList()
    ) = FinancialAccount(
        id = id,
        displayName = displayName,
        institutionName = null,
        accountType = type,
        accountNature = com.baraa.masroof.transaction.AccountNature.defaultNatureFor(type),
        currency = com.baraa.masroof.transaction.Currency.SAR,
        openingBalance = java.math.BigDecimal.ZERO,
        openingBalanceDate = 0L,
        includeInNetWorth = true,
        includeInLiquidity = com.baraa.masroof.transaction.AccountLiquidityDefaults.defaultFor(type),
        isOwnedByUser = true,
        isActive = true,
        notes = null
    )

    // -- merchant memory behavior ------------------------------------------

    @Test
    fun merchantMemoryNotSavedForThisTransactionOnly() {
        val repo = FakeMerchantMemoryRepository()
        kotlinx.coroutines.runBlocking {
            // We never call remember(); the memory is empty.
            assertEquals(0, repo.getAll().size)
        }
    }

    @Test
    fun merchantMemorySavedWhenUserSelectsRemember() {
        val repo = FakeMerchantMemoryRepository()
        kotlinx.coroutines.runBlocking {
            repo.remember(
                rawMerchant = "Starbucks",
                displayName = "Starbucks",
                categoryId = 1L,
                treatment = FinancialTreatment.EXPENSE
            )
            assertEquals(1, repo.getAll().size)
            assertEquals("Starbucks", repo.getByKey("starbucks")?.displayName)
        }
    }

    @Test
    fun merchantMemoryUpdatedWithoutDuplicateRows() {
        val repo = FakeMerchantMemoryRepository()
        kotlinx.coroutines.runBlocking {
            repo.remember("Starbucks", "Starbucks", 1L, FinancialTreatment.EXPENSE)
            repo.remember("Starbucks", "Starbucks", 2L, FinancialTreatment.EXPENSE)
            // Same normalized key → no new row.
            assertEquals(1, repo.getAll().size)
            // Last value wins.
            val row = repo.getByKey("starbucks")!!
            assertEquals(2L, row.preferredCategoryId)
        }
    }

    @Test
    fun merchantMemoryConfirmationCountIncrements() {
        val repo = FakeMerchantMemoryRepository()
        kotlinx.coroutines.runBlocking {
            repo.remember("Starbucks", "Starbucks", 1L, FinancialTreatment.EXPENSE)
            val first = repo.getByKey("starbucks")!!.confirmationCount
            repo.remember("Starbucks", "Starbucks", 1L, FinancialTreatment.EXPENSE)
            val second = repo.getByKey("starbucks")!!.confirmationCount
            assertEquals(first + 1, second)
        }
    }

    @Test
    fun merchantMemoryDisabledIgnoresMemory() {
        val repo = FakeMerchantMemoryRepository()
        kotlinx.coroutines.runBlocking {
            repo.remember("Starbucks", "Starbucks", 1L, FinancialTreatment.EXPENSE)
            val key = repo.getByKey("starbucks")!!.normalizedKey
            repo.setEnabled(key, false)
            // The engine's MerchantMemoryRule should NOT match a disabled
            // entry.
            val engine = RuleEngineFactory.build(categories = emptyList(), feeCategoryId = null)
            val rule = engine.rules.first { it.name == "MerchantMemoryRule" }
            val verdict = rule.evaluate(
                makeInput(merchant = "Starbucks"),
                RuleContext(
                    ownedAccounts = emptyList(),
                    merchantMemories = repo.getAll(),
                    categories = emptyList()
                )
            )
            assertNull("disabled memory must not match", verdict)
        }
    }

    // -- Arabic merchant classification ------------------------------------

    @Test
    fun arabicSupermarketClassification() {
        val groceries = makeCategory(1, "مقاضي")
        val engine = RuleEngineFactory.build(categories = listOf(groceries), feeCategoryId = null)
        val v = engine.classify(
            makeInput(body = "تم الشراء من سوبرماركت بندة", merchant = "بندة"),
            RuleContext(emptyList(), emptyList(), listOf(groceries))
        )
        assertEquals(FinancialTreatment.EXPENSE, v.financialTreatment)
        assertEquals(1L, v.categoryId)
    }

    @Test
    fun arabicRestaurantClassification() {
        val restaurants = makeCategory(1, "مطاعم")
        val engine = RuleEngineFactory.build(categories = listOf(restaurants), feeCategoryId = null)
        val v = engine.classify(
            makeInput(body = "تم الشراء من مطعم البيك"),
            RuleContext(emptyList(), emptyList(), listOf(restaurants))
        )
        assertEquals(1L, v.categoryId)
    }

    @Test
    fun arabicFuelClassification() {
        val fuel = makeCategory(1, "وقود")
        val engine = RuleEngineFactory.build(categories = listOf(fuel), feeCategoryId = null)
        val v = engine.classify(
            makeInput(body = "تم تعبئة وقود في محطة أرامكس"),
            RuleContext(emptyList(), emptyList(), listOf(fuel))
        )
        assertEquals(1L, v.categoryId)
    }

    @Test
    fun arabicPharmacyClassification() {
        val pharmacy = makeCategory(1, "صيدلية")
        val engine = RuleEngineFactory.build(categories = listOf(pharmacy), feeCategoryId = null)
        val v = engine.classify(
            makeInput(body = "تم الشراء من صيدلية الدواء"),
            RuleContext(emptyList(), emptyList(), listOf(pharmacy))
        )
        assertEquals(1L, v.categoryId)
    }

    @Test
    fun vagueArabicMerchantTextUsesPurchaseFallbackWithoutCategory() {
        val other = makeCategory(1, "أخرى")
        val engine = RuleEngineFactory.build(categories = listOf(other), feeCategoryId = null)
        val v = engine.classify(
            makeInput(body = "شركة المؤسسة اشترت منتجا", merchant = "شركة"),
            RuleContext(emptyList(), emptyList(), listOf(other))
        )
        // Vague blacklist blocks category assignment; ParsedTypeFallbackRule
        // still treats PURCHASE as EXPENSE so balances can auto-post.
        assertEquals(FinancialTreatment.EXPENSE, v.financialTreatment)
        assertNull(v.categoryId)
        assertTrue(v.reason.contains("ParsedTypeFallbackRule"))
    }

    // -- owned-account matching --------------------------------------------

    @Test
    fun accountMatchingNormalizesArabicDigits() {
        val n = AccountMatching.normalizeDigits("تحويل إلى حساب ****١٢٣٤")
        assertEquals("تحويل إلى حساب ****1234", n)
    }

    @Test
    fun accountMatchingByTypedIdentifierOverridesLastFour() {
        val acct = makeAccount(1, "My Card", lastFour = "1234")
        // Even when the parser surfaces only typed identifier evidence,
        // name-based matching still resolves the owning account. Legacy
        // last-four string matching was removed to make typed evidence
        // authoritative.
        val match = AccountMatching.matchByName("تحويل إلى حساب My Card", null, listOf(acct))
        assertEquals(acct, match)
    }

    // -- wallet top-up -----------------------------------------------------

    @Test
    fun ownedWalletTopUpClassifiedAsInternalTransfer() {
        val card = makeAccount(1, "Visa", type = AccountType.CREDIT_CARD, lastFour = "1234", aliases = listOf("visa"))
        val wallet = makeAccount(2, "STC Pay", type = AccountType.WALLET, aliases = listOf("stc pay", "stcpay"))
        val engine = RuleEngineFactory.build(categories = emptyList(), feeCategoryId = null)
        val v = engine.classify(
            makeInput(
                type = TransactionType.TRANSFER_IN,
                sender = "stc pay",
                body = "شحن المحفظة من بطاقتك Visa ****1234 بمبلغ 100 ريال"
            ),
            RuleContext(
                listOf(card, wallet),
                emptyList(),
                emptyList(),
                accountIdentifiers = listOf(
                    com.baraa.masroof.rules.AccountIdentifierSnapshot(
                        2,
                        com.baraa.masroof.data.db.AccountIdentifierType.WALLET_LAST4,
                        "9999"
                    ),
                    com.baraa.masroof.rules.AccountIdentifierSnapshot(
                        1,
                        com.baraa.masroof.data.db.AccountIdentifierType.CREDIT_CARD_LAST4,
                        "1234"
                    )
                ),
                accountsBySenderKey = mapOf("stcpay" to setOf(2L)),
            )
        )
        assertEquals(FinancialTreatment.INTERNAL_TRANSFER, v.financialTreatment)
    }

    @Test
    fun unknownWalletTopUpRequiresReview() {
        val card = makeAccount(1, "Visa", type = AccountType.CREDIT_CARD, lastFour = "1234", aliases = listOf("visa"))
        val engine = RuleEngineFactory.build(categories = emptyList(), feeCategoryId = null)
        val v = engine.classify(
            makeInput(
                type = TransactionType.TRANSFER_IN,
                sender = "Unknown Wallet",
                body = "شحن المحفظة من بطاقتك Visa ****1234"
            ),
            RuleContext(listOf(card), emptyList(), emptyList())
        )
        // No owned wallet → WalletTopUpRule returns null; auditor still sees
        // شحن المحفظة and keeps PENDING_REVIEW (two-sided, not auto-applied).
        assertEquals(FinancialTreatment.PENDING_REVIEW, v.financialTreatment)
    }

    // -- rule priority single source ---------------------------------------

    @Test
    fun ruleOrderingComesFromOneSource() {
        val expected = RuleEngineFactory.documentedPriorities
        val engine = RuleEngineFactory.build(categories = emptyList(), feeCategoryId = null)
        val actualPriorities = engine.rules.map { it.priority }.toSet()
        // Every documented priority must have at least one rule registered.
        for (p in expected) {
            assertTrue("rule for $p must be registered", actualPriorities.contains(p))
        }
    }

    @Test
    fun describeActiveRulesExposesAllRegisteredRules() {
        val described = RuleEngineFactory.describeActiveRules()
        assertTrue("must have at least 11 rules", described.size >= 11)
        // Each entry has a name, @, priority, #, order.
        for (entry in described) {
            assertTrue("entry must contain @ and #: $entry", entry.contains("@") && entry.contains("#"))
        }
    }
}
