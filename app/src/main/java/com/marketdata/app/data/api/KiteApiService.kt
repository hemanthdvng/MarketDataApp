package com.marketdata.app.data.api

import com.marketdata.app.data.models.HistoricalResponse
import com.marketdata.app.data.models.QuoteResponse
import com.marketdata.app.data.models.SessionResponse
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface KiteApiService {

    @FormUrlEncoded
    @POST("session/token")
    suspend fun createSession(
        @Header("X-Kite-Version") version: String = "3",
        @Field("api_key") apiKey: String,
        @Field("request_token") requestToken: String,
        @Field("checksum") checksum: String
    ): Response<SessionResponse>

    @DELETE("session/token")
    suspend fun deleteSession(
        @Header("X-Kite-Version") version: String = "3",
        @Header("Authorization") auth: String,
        @Query("api_key") apiKey: String
    ): Response<SessionResponse>

    @GET("instruments/historical/{instrument_token}/{interval}")
    suspend fun getHistoricalData(
        @Header("X-Kite-Version") version: String = "3",
        @Header("Authorization") auth: String,
        @Path("instrument_token") instrumentToken: Long,
        @Path("interval") interval: String,
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("oi") oi: Int = 0,
        @Query("continuous") continuous: Int = 0
    ): Response<HistoricalResponse>

    @GET("quote")
    suspend fun getQuote(
        @Header("X-Kite-Version") version: String = "3",
        @Header("Authorization") auth: String,
        @Query("i") instruments: List<String>
    ): Response<QuoteResponse>

    @GET("quote/ltp")
    suspend fun getLtp(
        @Header("X-Kite-Version") version: String = "3",
        @Header("Authorization") auth: String,
        @Query("i") instruments: List<String>
    ): Response<QuoteResponse>

    companion object {
        private const val BASE_URL = "https://api.kite.trade/"

        fun create(): KiteApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(KiteApiService::class.java)
        }
    }
}
