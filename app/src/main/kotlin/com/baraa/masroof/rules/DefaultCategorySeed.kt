package com.baraa.masroof.rules

import com.baraa.masroof.data.db.CategoryEntity

/**
 * Initial category list seeded **once** on first DB creation. The
 * [MasroofDatabase] `onCreate` callback invokes [seed] which inserts all
 * categories with `isSystem = true`. The user can later add, rename, or
 * disable these — but cannot delete them while the foreign key from
 * [com.baraa.masroof.data.db.TransactionEntity] is live.
 */
object DefaultCategorySeed {

    /**
     * Returns the seed categories in (parent → child) order. The list is
     * designed to match the user spec:
     *  - المنزل, المطاعم, النقل, التعليم, الاتصالات, الصحة, التسوق,
     *    الترفيه, الالتزامات, الاستثمار, التحويلات, أخرى
     *  - each with its suggested children.
     */
    fun seed(now: Long): List<CategoryEntity> {
        val result = ArrayList<CategoryEntity>(40)
        var order = 0
        fun add(parentNameAr: String, en: String?, sortOrder: Int): Long {
            val id = -(sortOrder + 1).toLong() // negative to avoid collision with auto-generated ids
            result.add(
                CategoryEntity(
                    id = id,
                    parentId = null,
                    nameAr = parentNameAr,
                    nameEn = en,
                    sortOrder = sortOrder,
                    enabled = true,
                    isSystem = true,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            return id
        }
        fun addChild(parentId: Long, nameAr: String, en: String?, sortOrder: Int) {
            result.add(
                CategoryEntity(
                    id = 0, // auto-generate
                    parentId = parentId,
                    nameAr = nameAr,
                    nameEn = en,
                    sortOrder = sortOrder,
                    enabled = true,
                    isSystem = true,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        }

        // -- المنزل --
        val home = add("المنزل", "Home", order++)
        addChild(home, "مقاضي", "Groceries", 0)
        addChild(home, "صيانة المنزل", "Home maintenance", 1)
        addChild(home, "أثاث", "Furniture", 2)
        addChild(home, "أدوات منزلية", "Household tools", 3)
        addChild(home, "كهرباء ومياه", "Utilities", 4)
        addChild(home, "عاملة منزلية", "Domestic help", 5)
        addChild(home, "حارس", "Security", 6)

        // -- المطاعم --
        val food = add("المطاعم", "Food & drink", order++)
        addChild(food, "مطاعم", "Restaurants", 0)
        addChild(food, "مقاهي", "Cafés", 1)
        addChild(food, "توصيل طعام", "Food delivery", 2)
        addChild(food, "حلويات", "Sweets", 3)

        // -- النقل --
        val transport = add("النقل", "Transport", order++)
        addChild(transport, "وقود", "Fuel", 0)
        addChild(transport, "صيانة السيارة", "Car maintenance", 1)
        addChild(transport, "تأمين السيارة", "Car insurance", 2)
        addChild(transport, "مواقف", "Parking", 3)
        addChild(transport, "نقل وتوصيل", "Rides & delivery", 4)

        // -- التعليم --
        val edu = add("التعليم", "Education", order++)
        addChild(edu, "رسوم دراسية", "Tuition", 0)
        addChild(edu, "مدرس خصوصي", "Private tutor", 1)
        addChild(edu, "كتب وأدوات", "Books & supplies", 2)
        addChild(edu, "أنشطة الأبناء", "Kids activities", 3)

        // -- الاتصالات --
        val telco = add("الاتصالات", "Telecom", order++)
        addChild(telco, "جوال", "Mobile", 0)
        addChild(telco, "إنترنت", "Internet", 1)
        addChild(telco, "اشتراكات رقمية", "Digital subscriptions", 2)

        // -- الصحة --
        val health = add("الصحة", "Health", order++)
        addChild(health, "صيدلية", "Pharmacy", 0)
        addChild(health, "مستشفى وعيادة", "Hospital & clinic", 1)
        addChild(health, "تأمين صحي", "Health insurance", 2)

        // -- التسوق --
        val shop = add("التسوق", "Shopping", order++)
        addChild(shop, "ملابس", "Clothing", 0)
        addChild(shop, "إلكترونيات", "Electronics", 1)
        addChild(shop, "مشتريات عامة", "General shopping", 2)

        // -- الترفيه --
        val fun1 = add("الترفيه", "Entertainment", order++)
        addChild(fun1, "سفر", "Travel", 0)
        addChild(fun1, "فعاليات", "Events", 1)
        addChild(fun1, "ألعاب", "Games", 2)
        addChild(fun1, "ترفيه عائلي", "Family entertainment", 3)

        // -- الالتزامات --
        val commit = add("الالتزامات", "Commitments", order++)
        addChild(commit, "إيجار", "Rent", 0)
        addChild(commit, "قرض", "Loan", 1)
        addChild(commit, "دعم الوالدين", "Parents support", 2)
        addChild(commit, "مصروف الزوجة", "Spouse allowance", 3)

        // -- الاستثمار --
        val inv = add("الاستثمار", "Investments", order++)
        addChild(inv, "أبيان", "Abyan", 0)
        addChild(inv, "دراية", "Derayah", 1)
        addChild(inv, "صكوك", "Sukuk", 2)
        addChild(inv, "استثمارات أخرى", "Other investments", 3)

        // -- التحويلات --
        val xfer = add("التحويلات", "Transfers", order++)
        addChild(xfer, "تحويل داخلي", "Internal transfer", 0)
        addChild(xfer, "تحويل لشخص", "Person-to-person", 1)
        addChild(xfer, "سداد بطاقة", "Card payment", 2)

        // -- أخرى --
        val other = add("أخرى", "Other", order++)
        addChild(other, "رسوم بنكية", "Bank fees", 0)
        addChild(other, "غير مصنف", "Unclassified", 1)

        return result
    }
}
