package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.SenderInstitutionMappingEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FinancialInstitutionResolverTest {
    private fun resolver(
        vararg seeded: SenderInstitutionMappingEntity
    ): FinancialInstitutionResolver = FinancialInstitutionResolver(FakeSenderInstitutionMappingDao().also { dao ->
        for (m in seeded) {
            kotlinx.coroutines.runBlocking { dao.insert(m.copy(id = 0)) }
        }
    })

    @Test fun savedMappingWinsForFutureMessages() = runBlocking {
        val r = resolver(SenderInstitutionMappingEntity(senderKey = "alrajhi-bk", institutionName = "مصرف الراجحي", isActive = true, confirmationCount = 1, lastConfirmedAt = 1L, createdAt = 1L))
        val v = r.resolve(sender = "AlRajhi-BK", parserIdentity = "AlRajhi")
        assertEquals("مصرف الراجحي", v.institutionDisplayName)
        assertEquals(InstitutionIdentificationSource.USER_CONFIRMED_MAPPING, v.source)
        assertFalse(v.requiresReview)
    }

    @Test fun parsedInstitutionUsedWhenNoSavedMapping() = runBlocking {
        val r = resolver()
        val v = r.resolve(sender = "unknown-sender", parsedInstitution = "بنك الرياض")
        assertEquals("بنك الرياض", v.institutionDisplayName)
        assertEquals(InstitutionIdentificationSource.PARSED_INSTITUTION, v.source)
    }

    @Test fun unknownSenderReturnsReviewableUnknown() = runBlocking {
        val r = resolver()
        val v = r.resolve(sender = "12345")
        assertEquals(InstitutionIdentificationSource.UNKNOWN, v.source)
        assertTrue(v.requiresReview)
        assertEquals("مرسل مالي غير معروف", v.institutionDisplayName)
    }

    @Test fun disabledMappingIsNotReused() = runBlocking {
        val r = resolver(SenderInstitutionMappingEntity(senderKey = "alrajhi", institutionName = "مصرف الراجحي", isActive = false, confirmationCount = 1, lastConfirmedAt = 1L, createdAt = 1L))
        val v = r.resolve(sender = "alrajhi")
        assertTrue(v.isUnknown)
    }

    @Test fun nullSenderFallsBackToParsedInstitution() = runBlocking {
        val r = resolver()
        val v = r.resolve(sender = null, parsedInstitution = "D360")
        assertEquals("D360", v.institutionDisplayName)
    }

    @Test fun sameNormalizedKeyMatchesDifferentCasing() = runBlocking {
        val r = resolver(SenderInstitutionMappingEntity(senderKey = "alrajhi", institutionName = "مصرف الراجحي", isActive = true, confirmationCount = 1, lastConfirmedAt = 1L, createdAt = 1L))
        val v1 = r.resolve(sender = "AlRajhi")
        val v2 = r.resolve(sender = "ALRAJHI")
        val v3 = r.resolve(sender = "  alrajhi  ")
        assertEquals("مصرف الراجحي", v1.institutionDisplayName)
        assertEquals("مصرف الراجحي", v2.institutionDisplayName)
        assertEquals("مصرف الراجحي", v3.institutionDisplayName)
    }

    @Test fun arabicDigitsAreNormalized() = runBlocking {
        val r = resolver(SenderInstitutionMappingEntity(senderKey = "1234", institutionName = "بنك تجريبي", isActive = true, confirmationCount = 1, lastConfirmedAt = 1L, createdAt = 1L))
        val v = r.resolve(sender = "\u0661\u0662\u0663\u0664")
        assertEquals("بنك تجريبي", v.institutionDisplayName)
    }

    @Test fun confirmRequiresUserActionAndDoesNotInventName() = runBlocking {
        val r = resolver()
        assertFalse(r.confirm(sender = "whoever", institutionName = ""))
        assertFalse(r.confirm(sender = "", institutionName = "bank"))
        assertTrue(r.confirm(sender = "whoever", institutionName = "بنك الجزيرة"))
        // Confirming twice increments the count.
        assertTrue(r.confirm(sender = "whoever", institutionName = "بنك الجزيرة"))
    }
}
