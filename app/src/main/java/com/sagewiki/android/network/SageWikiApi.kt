package com.sagewiki.android.network

import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

/**
 * Retrofit API interface for SageWiki backend communication.
 *
 * Defines endpoints for health checks, configuration, source management,
 * article access, search, LLM queries, provenance, and model testing.
 */
interface SageWikiApi {

    /** Checks backend service health. */
    @GET("api/health")
    suspend fun health(): HealthResponse

    /** Retrieves the current server status. */
    @GET("api/status")
    suspend fun getStatus(): StatusResponse

    /** Retrieves system information from the backend. */
    @GET("api/sysinfo")
    suspend fun getSysInfo(): SysInfoResponse

    /** Fetches the current server configuration. */
    @GET("api/config")
    suspend fun getConfig(): ConfigResponse

    /** Updates the server configuration with the given request body. */
    @PUT("api/config")
    suspend fun updateConfig(@Body body: ConfigUpdateRequest): ConfigUpdateResponse

    /** Lists all available sources. */
    @GET("api/sources")
    suspend fun getSources(): SourcesResponse

    /** Uploads a source file via multipart form data. */
    @Multipart
    @POST("api/sources/upload")
    suspend fun uploadSource(@Part file: MultipartBody.Part): UploadResponse

    /** Triggers compilation, optionally scoped to a specific source. */
    @POST("api/compile")
    suspend fun compile(@Query("source") source: String? = null): CompileResponse

    /** Shares content based on the provided request body. */
    @POST("api/share")
    suspend fun share(@Body body: ShareRequest): ShareResponse

    /** Fetches an article by its path. */
    @GET("api/articles/{path}")
    suspend fun getArticle(@Path(value = "path", encoded = true) path: String): ArticleResponse

    /** Creates or updates an article with the given request body. */
    @PUT("api/article")
    suspend fun writeArticle(@Body body: ArticleWriteRequest): Map<String, Any>

    /** Downloads the raw content of a named source. */
    @GET("api/sources/raw/{name}")
    suspend fun getSourceRaw(@Path("name") name: String): okhttp3.ResponseBody

    /** Updates an existing source with the given request body. */
    @PUT("api/sources/update")
    suspend fun updateSource(@Body body: SourceUpdateRequest): Map<String, Any>

    /** Deletes a source identified by its name. */
    @DELETE("api/sources")
    suspend fun deleteSource(@Query("name") name: String): Map<String, Any>

    /** Retrieves the article manifest. */
    @GET("api/manifest")
    suspend fun getManifest(): ManifestResponse

    /** Fetches available models, optionally refreshing from the provider. */
    @GET("api/models")
    suspend fun getModels(@Query("fetch") fetch: Boolean = false): ModelsFetchResponse

    /** Tests connectivity to a model with the given configuration. */
    @POST("api/models/test")
    suspend fun testModel(@Body body: ModelTestRequest): ModelTestResponse

    /** Retrieves the full page tree structure. */
    @GET("api/tree")
    suspend fun getTree(): Map<String, Any>

    /** Retrieves the graph data for visualization. */
    @GET("api/graph")
    suspend fun getGraph(): GraphResponse

    // ============ 搜索 API ============

    /** Searches articles by query string with an optional result limit. */
    @GET("api/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("limit") limit: Int? = null
    ): SearchResponse

    // ============ LLM 查询 API ============
    // Backend returns SSE (text/event-stream), not JSON.
    // We get the raw ResponseBody and parse SSE events in the Repository.

    /** Submits a query to the LLM and returns a streaming SSE response body. */
    @POST("api/query")
    @Streaming
    suspend fun query(@Body body: QueryRequest): okhttp3.ResponseBody

    // ============ 来源追溯 API ============

    /** Retrieves provenance information filtered by article or source name. */
    @GET("api/provenance")
    suspend fun getProvenance(
        @Query("article") article: String? = null,
        @Query("source") source: String? = null
    ): ProvenanceResponse

    companion object {
        /** Creates a [SageWikiApi] instance with default timeouts and optional auth token. */
        fun create(baseUrl: String, token: String? = null): SageWikiApi {
            require(baseUrl.isNotBlank()) { "Server URL must not be empty" }

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
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(300, TimeUnit.SECONDS)
                .build()

            val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

            return Retrofit.Builder()
                .baseUrl(url)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(SageWikiApi::class.java)
        }

        /**
         * Creates an [OkHttpClient] configured for large file downloads.
         *
         * Timeouts are set to connect 30s, read 300s, write 300s to accommodate
         * long-running download operations.
         *
         * @param baseUrl The base URL of the backend server (used only for context; not part of the client).
         * @param token Optional bearer token added as an Authorization header to every request.
         * @return A configured [OkHttpClient] suitable for download operations.
         */
        fun createDownloadClient(baseUrl: String, token: String? = null): OkHttpClient {
            require(baseUrl.isNotBlank()) { "Server URL must not be empty" }

            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            return OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                    if (!token.isNullOrBlank()) {
                        request.addHeader("Authorization", "Bearer $token")
                    }
                    chain.proceed(request.build())
                }
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(300, TimeUnit.SECONDS)
                .build()
        }
    }
}
