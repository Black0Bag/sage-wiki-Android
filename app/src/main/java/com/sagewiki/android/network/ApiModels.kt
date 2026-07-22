package com.sagewiki.android.network

import com.google.gson.annotations.SerializedName

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

data class ArticleDeleteResponse(
    val status: String,
    val path: String?
)

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
    val api_key: String?,
    val base_url: String?,
    val rate_limit: Int?
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
    val base_url: String?,
    val rate_limit: Int?
)

data class CompilerConfigResponse(
    val max_parallel: Int?,
    val summary_max_tokens: Int?,
    val article_max_tokens: Int?,
    val mode: String?
)

data class SearchConfigResponse(
    val default_limit: Int?
)

data class ServeConfigResponse(
    val port: Int?
)

data class ConfigUpdateRequest(
    val project: String? = null,
    val language: String? = null,
    val llm_model: String? = null,
    val embedding_model: String? = null,
    val output: String? = null
)

data class ConfigUpdateResponse(
    val status: String?,
    val project: String?
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
