package com.whxbill.android.data

import java.math.BigDecimal

data class ApiResponse<T>(
    val code: Int = 0,
    val message: String = "",
    val data: T? = null
)

data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    val userId: Long = 0,
    val username: String = "",
    val nickname: String? = null,
    val accessToken: String = "",
    val refreshToken: String = "",
    val roles: List<String> = emptyList(),
    val permissions: List<String> = emptyList()
)

data class Book(
    val id: Long = 0,
    val bookName: String = "",
    val currencyCode: String? = null,
    val isDefault: Int? = null
)

data class Account(
    val id: Long = 0,
    val bookId: Long = 0,
    val accountName: String = "",
    val accountType: String? = null,
    val balance: BigDecimal? = null,
    val colorTag: String? = null
)

data class Category(
    val id: Long = 0,
    val categoryName: String = "",
    val categoryType: String = "",
    val parentId: Long? = null,
    val icon: String? = null,
    val colorTag: String? = null
)

data class BillSaveRequest(
    val bookId: Long,
    val accountId: Long,
    val categoryId: Long,
    val billType: String,
    val amount: BigDecimal,
    val billDate: String,
    val billTime: String,
    val merchantName: String?,
    val remark: String?,
    val sourceType: String = "MANUAL"
)

data class Bill(
    val id: Long = 0,
    val bookId: Long = 0,
    val accountId: Long = 0,
    val categoryId: Long = 0,
    val billType: String = "",
    val amount: BigDecimal? = null,
    val billDate: String = "",
    val billTime: String? = null,
    val merchantName: String? = null,
    val remark: String? = null,
    val sourceType: String? = null
)

data class Session(
    val accessToken: String = "",
    val refreshToken: String = "",
    val userId: Long = 0,
    val username: String = "",
    val nickname: String = ""
) {
    val isLoggedIn: Boolean get() = accessToken.isNotBlank()
    val displayName: String get() = nickname.ifBlank { username }
}
