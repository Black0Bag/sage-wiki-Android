package com.sagewiki.android.network

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface SageWikiApi {

    @GET("api/health")
    suspend fun health(): HealthResponse

    @GET("api/status")
    suspend fun getStatus(): StatusResponse

    @GET("api/sysinfo")
    suspend fun getSysInfo(): SysInfoResponse

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
    suspend fun compile(@Query("source") source: String? = null): CompileResponse

    @POST("api/share")
    suspend fun share(@Body body: ShareRequest): ShareResponse

    @GET("api/articles/{path}")
    suspend fun getArticle(@Path("path") path: String): ArticleResponse

    @PUT("api/article")
    suspend fun writeArticle(@Body body: ArticleWriteRequest): Map<String, Any>

    @GET("api/sources/raw/{name}")
    suspend fun getSourceRaw(@Path("name") name: String): okhttp3.ResponseBody

    @PUT("api/sources/update")
    suspend fun updateSource(@Body body: SourceUpdateRequest): Map<String, Any>

    @DELETE("api/sources")
    suspend fun deleteSource(@Query("name") name: String): Map<String, Any>

    @GET("api/manifest")
    suspend fun getManifest(): ManifestResponse

    @GET("api/models")
    suspend fun getModels(@Query("fetch") fetch: Boolean = false): ModelsFetchResponse

    @POST("api/models/test")
    suspend fun testModel(@Body body: ModelTestRequest): ModelTestResponse

    @GET("api/tree")
    suspend fun getTree(): Map<String, Any>

    @GET("api/graph")
    suspend fun getGraph(): GraphResponse

    // ============ 搜索 API ============

    @GET("api/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("limit") limit: Int? = null
    ): SearchResponse

    // ============ LLM 查询 API ============

    @POST("api/query")
    suspend fun query(@Body body: QueryRequest): QueryResponse

    // ============ 来源追溯 API ============

    @GET("api/provenance")
    suspend fun getProvenance(
        @Query("article") article: String? = null,
        @Query("source") source: String? = null
    ): ProvenanceResponse

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
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
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
