package com.sagewiki.android.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface SageWikiApi {

    @GET("api/health")
    suspend fun health(): HealthResponse

    @GET("api/config")
    suspend fun getConfig(): ConfigResponse

    @PUT("api/config")
    suspend fun updateConfig(@Body body: ConfigUpdateRequest): ConfigUpdateResponse

    @GET("api/sources")
    suspend fun getSources(): SourcesResponse

    @Multipart
    @POST("api/sources/upload")
    suspend fun uploadSource(@Part file: MultipartBody.Part): UploadResponse

    @POST("api/compile")
    suspend fun compile(): CompileResponse

    @POST("api/share")
    suspend fun share(@Body body: ShareRequest): ShareResponse

    @GET("api/articles/{path}")
    suspend fun getArticle(@Path("path", encoded = true) path: String): ArticleResponse

    @PUT("api/article")
    suspend fun writeArticle(@Body body: ArticleWriteRequest): Map<String, Any>

    @DELETE("api/article")
    suspend fun deleteArticle(@Query("path") path: String): ArticleDeleteResponse

    @GET("api/manifest")
    suspend fun getManifest(): ManifestResponse

    @GET("api/models")
    suspend fun getModels(@Query("fetch") fetch: Boolean = false): ModelsFetchResponse

    companion object {
        fun create(baseUrl: String, token: String? = null): SageWikiApi {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                    if (!token.isNullOrBlank()) {
                        request.addHeader("Authorization", "Bearer $token")
                    }
                    chain.proceed(request.build())
                }
                .addInterceptor(logging)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()

            val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

            return Retrofit.Builder()
                .baseUrl(url)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(SageWikiApi::class.java)
        }
    }
}

fun SageWikiApi.baseUrl(): String {
    return ""
}
