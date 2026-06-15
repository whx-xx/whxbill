package com.whxbill.android.data

import retrofit2.HttpException
import java.io.IOException
import java.math.BigDecimal

class BillRepository(
    private val api: WhxBillApi,
    private val sessionStore: SessionStore
) {
    suspend fun login(username: String, password: String): LoginResponse {
        val response = unwrap { api.login(LoginRequest(username, password)) }
        ApiClient.accessToken = response.accessToken
        sessionStore.save(response)
        return response
    }

    suspend fun books(): List<Book> = unwrap { api.books() }

    suspend fun accounts(bookId: Long): List<Account> = unwrap { api.accounts(bookId) }

    suspend fun categories(bookId: Long, categoryType: String): List<Category> =
        unwrap { api.categories(bookId, categoryType) }

    suspend fun saveBill(
        bookId: Long,
        accountId: Long,
        categoryId: Long,
        billType: String,
        amount: BigDecimal,
        billDate: String,
        billTime: String,
        merchantName: String,
        remark: String
    ): Bill = unwrap {
        api.saveBill(
            BillSaveRequest(
                bookId = bookId,
                accountId = accountId,
                categoryId = categoryId,
                billType = billType,
                amount = amount,
                billDate = billDate,
                billTime = billTime,
                merchantName = merchantName.ifBlank { null },
                remark = remark.ifBlank { null }
            )
        )
    }

    suspend fun logout() {
        ApiClient.accessToken = ""
        sessionStore.clear()
    }

    private suspend fun <T> unwrap(call: suspend () -> ApiResponse<T>): T {
        return try {
            val response = call()
            if (response.code == 200 && response.data != null) {
                response.data
            } else {
                throw ApiException(response.code, response.message.ifBlank { "请求失败" })
            }
        } catch (error: ApiException) {
            throw error
        } catch (error: HttpException) {
            val body = error.response()?.errorBody()?.string().orEmpty()
            val message = Regex("\"message\"\\s*:\\s*\"([^\"]*)\"")
                .find(body)
                ?.groupValues
                ?.getOrNull(1)
                ?.takeIf { it.isNotBlank() }
                ?: "HTTP ${error.code()} ${error.message()}"
            throw ApiException(error.code(), message)
        } catch (error: IOException) {
            throw ApiException(0, "网络连接失败，请检查服务器地址或网络")
        } catch (error: Exception) {
            throw ApiException(0, error.message?.takeIf { it.isNotBlank() } ?: "请求失败")
        }
    }
}

class ApiException(val code: Int, override val message: String) : Exception(message)
