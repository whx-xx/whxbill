package com.whxbill.android.data

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

object ApiClient {
    const val BASE_URL = "http://39.106.168.139/api/"

    @Volatile
    var accessToken: String = ""

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder().apply {
                if (accessToken.isNotBlank()) {
                    header("Authorization", "Bearer $accessToken")
                }
            }.build()
            chain.proceed(request)
        }
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    val service: WhxBillApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(WhxBillApi::class.java)
}

interface WhxBillApi {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): ApiResponse<LoginResponse>

    @GET("books")
    suspend fun books(): ApiResponse<List<Book>>

    @GET("accounts")
    suspend fun accounts(@Query("bookId") bookId: Long): ApiResponse<List<Account>>

    @GET("categories")
    suspend fun categories(
        @Query("bookId") bookId: Long,
        @Query("categoryType") categoryType: String
    ): ApiResponse<List<Category>>

    @POST("bills")
    suspend fun saveBill(@Body request: BillSaveRequest): ApiResponse<Bill>
}
