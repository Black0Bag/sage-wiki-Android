package com.sagewiki.android.data

import com.sagewiki.android.network.*
import com.sagewiki.android.network.TreeResponse
import com.sagewiki.android.network.ProvenanceResponse
import com.sagewiki.android.network.SysInfoResponse
import com.sagewiki.android.network.ModelTestRequest
import com.sagewiki.android.network.ModelTestResponse
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
        /** 创建并返回一个配置好服务器地址与令牌的 [SageWikiRepository] 实例。 */
        fun create(url: String, token: String): SageWikiRepository {
            val api = SageWikiApi.create(url, token)
            return SageWikiRepository(api).also {
                it.serverUrl = url
                it.serverToken = token
            }
        }
    }

    // ========== 状态 & 系统 ==========

    /** 检查服务器健康状态。 */
    suspend fun health() = api.health()

    /** 获取服务器运行状态信息。 */
    suspend fun getStatus() = api.getStatus()

    /** Get system info from /api/sysinfo */
    suspend fun getSysInfo(): SysInfoResponse = api.getSysInfo()

    // ========== 配置 ==========

    /** 获取当前服务器配置。 */
    suspend fun getConfig() = api.getConfig()

    /** 更新服务器配置。 */
    suspend fun updateConfig(body: ConfigUpdateRequest) = api.updateConfig(body)

    // ========== 源文件 ==========

    /** 获取所有源文件列表。 */
    suspend fun getSources() = api.getSources()

    /** 上传源文件到服务器。 */
    suspend fun uploadSource(file: MultipartBody.Part) = api.uploadSource(file)

    /** 编译指定源文件（为空时编译全部）。 */
    suspend fun compile(source: String? = null) = api.compile(source)

    /** 获取指定源文件的原始内容。 */
    suspend fun getSourceRaw(name: String) = api.getSourceRaw(name)

    /** 更新指定源文件内容。 */
    suspend fun updateSource(body: SourceUpdateRequest) = api.updateSource(body)

    /** 删除指定名称的源文件。 */
    suspend fun deleteSource(name: String) = api.deleteSource(name)

    // ========== 文章 ==========

    /** 获取指定路径的文章内容。 */
    suspend fun getArticle(path: String) = api.getArticle(path)

    /** 写入或更新文章。 */
    suspend fun writeArticle(body: ArticleWriteRequest) = api.writeArticle(body)

    // ========== 模型 ==========

    /** 获取可用模型列表，[fetch] 为 true 时强制从服务器重新拉取。 */
    suspend fun getModels(fetch: Boolean = false) = api.getModels(fetch)

    /** Test model connectivity via /api/models/test */
    suspend fun testModel(model: String, prompt: String? = null): ModelTestResponse =
        api.testModel(ModelTestRequest(model, prompt))

    // ========== 知识图谱 & 树 ==========

    /** Get directory tree from /api/tree */
    suspend fun getTree(): TreeResponse = api.getTree()

    /** 获取知识图谱数据。 */
    suspend fun getGraph() = api.getGraph()

    /** 获取编译清单（manifest）。 */
    suspend fun getManifest() = api.getManifest()

    // ========== 搜索 & 查询 ==========

    /** 搜索文章，[query] 为关键词，[limit] 限制返回条数。 */
    suspend fun search(query: String, limit: Int? = null) = api.search(query, limit)

    /** 向服务器发起自然语言查询。 */
    suspend fun query(body: QueryRequest) = api.query(body)

    // ========== 来源追溯 ==========

    /** Get article provenance from /api/provenance */
    suspend fun getProvenance(path: String): ProvenanceResponse = api.getProvenance(path)

    // ========== 分享 ==========

    /** 创建分享链接。 */
    suspend fun share(body: ShareRequest) = api.share(body)
}
