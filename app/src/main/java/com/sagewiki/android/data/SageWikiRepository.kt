package com.sagewiki.android.data

import com.sagewiki.android.network.*
import okhttp3.MultipartBody

/**
 * 统一网络调用入口。所有 ViewModel 通过 Repository 访问后端 API。
 * 职责：管理 API 实例创建、服务器切换、错误处理。
 */
class SageWikiRepository private constructor(
    private val api: SageWikiApi
) {
    var serverUrl: String = ""
        private set
    var serverToken: String = ""
        private set

    companion object {
        fun create(url: String, token: String): SageWikiRepository {
            val api = SageWikiApi.create(url, token)
            return SageWikiRepository(api).also {
                it.serverUrl = url
                it.serverToken = token
            }
        }
    }

    // ========== 状态 & 系统 ==========

    suspend fun health() = api.health()
    suspend fun getStatus() = api.getStatus()
    suspend fun getSysInfo() = api.getSysInfo()

    // ========== 配置 ==========

    suspend fun getConfig() = api.getConfig()
    suspend fun updateConfig(body: ConfigUpdateRequest) = api.updateConfig(body)

    // ========== 源文件 ==========

    suspend fun getSources() = api.getSources()
    suspend fun uploadSource(file: MultipartBody.Part) = api.uploadSource(file)
    suspend fun compile(source: String? = null) = api.compile(source)
    suspend fun getSourceRaw(name: String) = api.getSourceRaw(name)
    suspend fun updateSource(body: SourceUpdateRequest) = api.updateSource(body)
    suspend fun deleteSource(name: String) = api.deleteSource(name)

    // ========== 文章 ==========

    suspend fun getArticle(path: String) = api.getArticle(path)
    suspend fun writeArticle(body: ArticleWriteRequest) = api.writeArticle(body)

    // ========== 模型 ==========

    suspend fun getModels(fetch: Boolean = false) = api.getModels(fetch)
    suspend fun testModel(body: ModelTestRequest) = api.testModel(body)

    // ========== 知识图谱 & 树 ==========

    suspend fun getTree() = api.getTree()
    suspend fun getGraph() = api.getGraph()
    suspend fun getManifest() = api.getManifest()

    // ========== 搜索 & 查询 ==========

    suspend fun search(query: String, limit: Int? = null) = api.search(query, limit)
    suspend fun query(body: QueryRequest) = api.query(body)

    // ========== 来源追溯 ==========

    suspend fun getProvenance(article: String? = null, source: String? = null) =
        api.getProvenance(article, source)

    // ========== 分享 ==========

    suspend fun share(body: ShareRequest) = api.share(body)
}
