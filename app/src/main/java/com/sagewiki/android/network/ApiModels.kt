package com.sagewiki.android.network

import com.google.gson.annotations.SerializedName

// ========== 原有模型 ==========

/** 服务健康检查响应。 */
data class HealthResponse(
    val status: String,
    val project: String?,
    val version: String?,
    val language: String?,
    val timestamp: String?
)

/** 来源列表响应。 */
data class SourcesResponse(
    val sources: List<SourceInfo>,
    val total: Int
)

/** 单个来源文件信息。 */
data class SourceInfo(
    val name: String,
    val size: Long,
    @SerializedName("mod_time") val modTime: String
)

/** 文件上传响应。 */
data class UploadResponse(
    val status: String,
    val filename: String?,
    val size: Long?,
    val path: String?,
    val message: String?
)

/** 编译请求响应。 */
data class CompileResponse(
    val status: String,
    val message: String?
)

/** 分享操作响应。 */
data class ShareResponse(
    val status: String,
    val filename: String?,
    val path: String?,
    val message: String?
)

/** 分享内容请求体。 */
data class ShareRequest(
    val title: String,
    val text: String,
    val url: String,
    val source: String = "android"
)

/** 文章详情响应。 */
data class ArticleResponse(
    val path: String?,
    val frontmatter: Map<String, Any>?,
    val body: String?
)

/** 写入文章请求体。 */
data class ArticleWriteRequest(
    val path: String,
    val content: String
)

/** 更新来源内容请求体。 */
data class SourceUpdateRequest(
    val name: String,
    val content: String
)

// ========== 配置模型 ==========

/** 项目配置响应。 */
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

/** API 连接配置响应。 */
data class ApiConfigResponse(
    val provider: String?,
    @SerializedName("api_key") val apiKey: String?,
    @SerializedName("base_url") val baseUrl: String?,
    @SerializedName("rate_limit") val rateLimit: Int?
)

/** 各任务所用模型名称配置响应。 */
data class ModelsConfigResponse(
    val summarize: String?,
    val extract: String?,
    val write: String?,
    val lint: String?,
    val query: String?
)

/** 向量化（Embedding）配置响应。 */
data class EmbedConfigResponse(
    val provider: String?,
    val model: String?,
    val dimensions: Int?,
    @SerializedName("base_url") val baseUrl: String?,
    @SerializedName("api_key") val apiKey: String?,
    @SerializedName("rate_limit") val rateLimit: Int?
)

/** 编译器配置响应。 */
data class CompilerConfigResponse(
    @SerializedName("max_parallel") val maxParallel: Int?,
    @SerializedName("summary_max_tokens") val summaryMaxTokens: Int?,
    @SerializedName("article_max_tokens") val articleMaxTokens: Int?,
    val mode: String?
)

/** 搜索配置响应。 */
data class SearchConfigResponse(
    @SerializedName("default_limit") val defaultLimit: Int?
)

/** Serve 服务端口配置响应。 */
data class ServeConfigResponse(
    val port: Int?
)

/** 更新配置请求体。 */
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

/** 配置更新响应。 */
data class ConfigUpdateResponse(
    val status: String?,
    val project: String?
)

/** 可用模型列表响应（兼容 OpenAI 风格）。 */
data class ModelsFetchResponse(
    val `object`: String?,
    val data: List<ModelInfo>?
)

/** 单个模型信息。 */
data class ModelInfo(
    val id: String,
    val `object`: String?
)

/** Manifest 清单响应。 */
data class ManifestResponse(
    val concepts: Map<String, ConceptInfo>?,
    val summaries: List<String>?,
    val sources: Map<String, SourceManifestInfo>?
)

/** 概念条目详情。 */
data class ConceptInfo(
    @SerializedName("article_path") val articlePath: String?,
    @SerializedName("last_compiled") val lastCompiled: String?,
    val sources: List<String>?
)

/** 来源在 Manifest 中的元信息。 */
data class SourceManifestInfo(
    @SerializedName("added_at") val addedAt: String?,
    @SerializedName("compiled_at") val compiledAt: String?,
    val hash: String?,
    @SerializedName("size_bytes") val sizeBytes: Long?,
    val status: String?,
    @SerializedName("summary_path") val summaryPath: String?,
    val type: String?
)

// ========== v1.1.0 新增模型 ==========

/** 知识库状态统计响应。 */
data class StatusResponse(
    val project: String?,
    val entries: Int?,
    val vectors: Int?,
    val dimensions: Int?,
    val entities: Int?,
    val relations: Int?
)

/** 系统运行信息响应。 */
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

/** Go 运行时信息。 */
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

/** 内存使用信息。 */
data class MemoryInfo(
    val total: Long?,
    val used: Long?,
    val free: Long?,
    val buffer: Long?,
    @SerializedName("usage_percent") val usagePercent: Double?
)

/** 磁盘使用信息。 */
data class DiskInfo(
    val total: Long?,
    val used: Long?,
    val free: Long?,
    @SerializedName("usage_percent") val usagePercent: Double?
)

/** 系统负载信息。 */
data class LoadInfo(
    @SerializedName("load_1") val load1: Double?,
    @SerializedName("load_5") val load5: Double?,
    @SerializedName("load_15") val load15: Double?
)

/** 单个温度传感器读数。 */
data class TempInfo(
    val zone: Int?,
    val type: String?,
    @SerializedName("temp_c") val tempC: Double?,
    @SerializedName("temp_raw") val tempRaw: Int?
)

/** 模型连通性测试请求体。 */
data class ModelTestRequest(
    val provider: String? = null,
    @SerializedName("base_url") val baseUrl: String? = null,
    @SerializedName("api_key") val apiKey: String? = null,
    val model: String? = null
)

/** 模型连通性测试响应。 */
data class ModelTestResponse(
    val success: Boolean,
    val model: String?,
    @SerializedName("latency_ms") val latencyMs: Long?,
    @SerializedName("status_code") val statusCode: Int?,
    val error: String?
)

/** 知识图谱响应。 */
data class GraphResponse(
    val nodes: List<GraphNode>?,
    val edges: List<GraphEdge>?
)

/** 图谱节点。 */
data class GraphNode(
    val id: String,
    val name: String? = null,
    val type: String? = null,
    val connections: Int? = null,
    val definition: String? = null
)

/** 图谱边。 */
data class GraphEdge(
    val source: String,
    val target: String,
    val relation: String? = null
)

// ============ 搜索 API ============

/** 语义搜索响应。 */
data class SearchResponse(
    val results: List<SearchResult>? = null,
    val total: Int? = null,
    val query: String? = null
)

/** 单条搜索结果。 */
data class SearchResult(
    val id: String? = null,
    val path: String? = null,
    val snippet: String? = null,
    val score: Float? = null
)

// ============ LLM 查询 API ============

/** LLM 问答请求体。 */
data class QueryRequest(
    val question: String,
    @SerializedName("top_k") val topK: Int? = null
)

/** LLM 问答响应。 */
data class QueryResponse(
    val answer: String? = null,
    val sources: List<String>? = null,
    val error: String? = null
)

// ============ 来源追溯 API ============

/** 文章来源追溯响应（GET /api/provenance?path=...）。 */
data class ProvenanceResponse(
    val path: String,
    val sources: List<String>,
    val compiled_at: String?
)

// ============================ v3.0.0 新增模型 ============================

/** Directory tree node from GET /api/tree */
data class TreeNode(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val children: List<TreeNode>?
)

/** Tree response from GET /api/tree */
data class TreeResponse(
    val tree: List<TreeNode>
)

/** Compile status response */
data class CompileStatusResponse(
    val status: String,
    val last_compile: String?,
    val error: String?
)
