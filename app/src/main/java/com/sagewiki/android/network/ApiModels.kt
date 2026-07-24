package com.sagewiki.android.network

import com.google.gson.annotations.SerializedName

// ========== 原有模型 ==========

data class HealthResponse(
    val status: String,
    val project: String?,
    val version: String?,
    val language: String?,
    val timestamp: String?
)

data class SourcesResponse(
    val sources: List<SourceInfo>,
    val total: Int
)

data class SourceInfo(
    val name: String,
    val size: Long,
    @SerializedName("mod_time") val modTime: String
)

data class UploadResponse(
    val status: String,
    val filename: String?,
    val size: Long?,
    val path: String?,
    val message: String?
)

data class CompileResponse(
    val status: String,
    val message: String?
)

data class ShareResponse(
    val status: String,
    val filename: String?,
    val path: String?,
    val message: String?
)

data class ShareRequest(
    val title: String,
    val text: String,
    val url: String,
    val source: String = "android"
)

data class ArticleResponse(
    val path: String?,
    val frontmatter: Map<String, Any>?,
    val body: String?
)

data class ArticleWriteRequest(
    val path: String,
    val content: String
)

data class SourceUpdateRequest(
    val name: String,
    val content: String
)

// ========== 配置模型 ==========

data class ConfigResponse(
    val project: String?,
    val description: String?,
    val language: String?,
    val output: String?,
    val api: ApiConfigResponse?,
    val models: ModelsConfigResponse?,
    val embed: EmbedConfigResponse?,
    val compiler: CompilerConfigResponse?,
    val search: SearchConfigResponse?,
    val serve: ServeConfigResponse?,
    @SerializedName("llm_api_base") val llmApiBase: String?,
    @SerializedName("embedding_api_base") val embeddingApiBase: String?
)

data class ApiConfigResponse(
    val provider: String?,
    @SerializedName("api_key") val apiKey: String?,
    @SerializedName("base_url") val baseUrl: String?,
    @SerializedName("rate_limit") val rateLimit: Int?
)

data class ModelsConfigResponse(
    val summarize: String?,
    val extract: String?,
    val write: String?,
    val lint: String?,
    val query: String?
)

data class EmbedConfigResponse(
    val provider: String?,
    val model: String?,
    val dimensions: Int?,
    @SerializedName("base_url") val baseUrl: String?,
    @SerializedName("api_key") val apiKey: String?,
    @SerializedName("rate_limit") val rateLimit: Int?
)

data class CompilerConfigResponse(
    @SerializedName("max_parallel") val maxParallel: Int?,
    @SerializedName("summary_max_tokens") val summaryMaxTokens: Int?,
    @SerializedName("article_max_tokens") val articleMaxTokens: Int?,
    val mode: String?
)

data class SearchConfigResponse(
    @SerializedName("default_limit") val defaultLimit: Int?
)

data class ServeConfigResponse(
    val port: Int?
)

data class ConfigUpdateRequest(
    val project: String? = null,
    val language: String? = null,
    @SerializedName("llm_model") val llmModel: String? = null,
    @SerializedName("extract_model") val extractModel: String? = null,
    @SerializedName("write_model") val writeModel: String? = null,
    @SerializedName("lint_model") val lintModel: String? = null,
    @SerializedName("query_model") val queryModel: String? = null,
    @SerializedName("embedding_model") val embeddingModel: String? = null,
    val output: String? = null,
    @SerializedName("api_key") val apiKey: String? = null,
    @SerializedName("api_base") val apiBase: String? = null,
    @SerializedName("embedding_api_key") val embeddingApiKey: String? = null,
    @SerializedName("embedding_base_url") val embeddingBaseUrl: String? = null,
    @SerializedName("embedding_provider") val embeddingProvider: String? = null,
    @SerializedName("embedding_dims") val embeddingDims: Int? = null
)

data class ConfigUpdateResponse(
    val status: String?,
    val project: String?
)

data class ModelsFetchResponse(
    val `object`: String?,
    val data: List<ModelInfo>?
)

data class ModelInfo(
    val id: String,
    val `object`: String?
)

data class ManifestResponse(
    val concepts: Map<String, ConceptInfo>?,
    val summaries: List<String>?,
    val sources: List<String>?
)

data class ConceptInfo(
    @SerializedName("article_path") val articlePath: String?,
    val tier: Int?,
    val source: String?
)

// ========== v1.1.0 新增模型 ==========

data class StatusResponse(
    val project: String?,
    val entries: Int?,
    val vectors: Int?,
    val dimensions: Int?,
    val entities: Int?,
    val relations: Int?
)

data class SysInfoResponse(
    val go: GoRuntimeInfo?,
    val memory: MemoryInfo?,
    val disk: DiskInfo?,
    val load: LoadInfo?,
    val temperatures: List<TempInfo>?,
    val uptime: Long?,
    @SerializedName("cpu_model") val cpuModel: String?,
    val hostname: String?,
    val version: String?
)

data class GoRuntimeInfo(
    val version: String?,
    val goroutines: Int?,
    val numCPU: Int?,
    @SerializedName("mem_alloc") val memAlloc: Long?,
    @SerializedName("mem_sys") val memSys: Long?,
    @SerializedName("mem_heap") val memHeap: Long?,
    @SerializedName("gc_pause_ns") val gcPauseNs: Long?,
    @SerializedName("num_gc") val numGC: Int?
)

data class MemoryInfo(
    val total: Long?,
    val used: Long?,
    val free: Long?,
    val buffer: Long?,
    @SerializedName("usage_percent") val usagePercent: Double?
)

data class DiskInfo(
    val total: Long?,
    val used: Long?,
    val free: Long?,
    @SerializedName("usage_percent") val usagePercent: Double?
)

data class LoadInfo(
    @SerializedName("load_1") val load1: Double?,
    @SerializedName("load_5") val load5: Double?,
    @SerializedName("load_15") val load15: Double?
)

data class TempInfo(
    val zone: Int?,
    val type: String?,
    @SerializedName("temp_c") val tempC: Double?,
    @SerializedName("temp_raw") val tempRaw: Int?
)

data class ModelTestRequest(
    val provider: String? = null,
    @SerializedName("base_url") val baseUrl: String? = null,
    @SerializedName("api_key") val apiKey: String? = null,
    val model: String? = null
)

data class ModelTestResponse(
    val success: Boolean,
    val model: String?,
    @SerializedName("latency_ms") val latencyMs: Long?,
    @SerializedName("status_code") val statusCode: Int?,
    val error: String?
)

data class GraphResponse(
    val nodes: List<GraphNode>?,
    val edges: List<GraphEdge>?
)

data class GraphNode(
    val id: String,
    val name: String? = null,
    val type: String? = null,
    val connections: Int? = null
)

data class GraphEdge(
    val source: String,
    val target: String,
    val relation: String? = null
)

// ============ 搜索 API ============

data class SearchResponse(
    val results: List<SearchResult>? = null,
    val total: Int? = null,
    val query: String? = null
)

data class SearchResult(
    val id: String? = null,
    val path: String? = null,
    val snippet: String? = null,
    val score: Float? = null
)

// ============ LLM 查询 API ============

data class QueryRequest(
    val question: String,
    @SerializedName("top_k") val topK: Int? = null
)

data class QueryResponse(
    val answer: String? = null,
    val sources: List<String>? = null,
    val error: String? = null
)

// ============ 来源追溯 API ============

data class ProvenanceResponse(
    val article: String? = null,
    val sources: List<ProvenanceSource>? = null
)

data class ProvenanceSource(
    val path: String? = null,
    val type: String? = null,
    val relevance: String? = null
)
