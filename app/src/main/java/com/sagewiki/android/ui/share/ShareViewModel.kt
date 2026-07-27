package com.sagewiki.android.ui.share

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sagewiki.android.MainActivity
import com.sagewiki.android.data.AppSettings
import com.sagewiki.android.data.SageWikiRepository
import com.sagewiki.android.network.ShareRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/** 分享处理状态。 */
sealed class SharePhase {
    object Idle : SharePhase()
    object Uploading : SharePhase()
    object Compiling : SharePhase()
    data class Success(val filename: String, val message: String) : SharePhase()
    data class Error(val message: String) : SharePhase()
}

/**
 * 分享处理 ViewModel，消费 [MainActivity.pendingShare]，
 * 将分享内容发送到后端，管理完整的 分享→上传→编译→反馈 状态流。
 */
class ShareViewModel(
    private val appSettings: AppSettings,
    private val context: Context
) : ViewModel() {

    private val _shareData = MutableStateFlow<MainActivity.ShareData?>(null)
    val shareData: StateFlow<MainActivity.ShareData?> = _shareData.asStateFlow()

    private val _phase = MutableStateFlow<SharePhase>(SharePhase.Idle)
    val phase: StateFlow<SharePhase> = _phase.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _resultFilename = MutableStateFlow<String?>(null)
    val resultFilename: StateFlow<String?> = _resultFilename.asStateFlow()

    private var repository: SageWikiRepository? = null

    init {
        val pending = MainActivity.pendingShare
        if (pending != null) {
            _shareData.value = pending
        }
    }

    private suspend fun ensureRepository(): SageWikiRepository? {
        if (repository != null) return repository
        val url = appSettings.serverUrl.first()
        val token = appSettings.bearerToken.first()
        if (url.isBlank()) {
            _phase.value = SharePhase.Error("服务器未配置，请先在配置页设置服务器地址")
            return null
        }
        repository = SageWikiRepository.create(url, token)
        return repository
    }

    /** 执行分享上传和编译。 */
    fun executeShare() {
        val data = _shareData.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _phase.value = SharePhase.Uploading
            _progress.value = 0.1f
            _statusMessage.value = "正在连接服务器..."

            val repo = ensureRepository() ?: return@launch

            try {
                when (data.type) {
                    MainActivity.ShareType.TEXT -> {
                        _statusMessage.value = "正在发送分享内容..."
                        val textContent = if (data.text.isNotBlank()) {
                            data.text
                        } else if (data.url.isNotBlank()) {
                            "这是用户分享的一个网址，请分析该网页的标题和描述信息，提取关键概念并编译进知识库。\n\nURL: ${data.url}"
                        } else {
                            ""
                        }
                        val request = ShareRequest(
                            title = data.title,
                            text = textContent,
                            url = data.url,
                            source = "android"
                        )
                        _progress.value = 0.5f
                        val response = repo.share(request)
                        _progress.value = 0.7f

                        if (response.status == "ok") {
                            _resultFilename.value = response.filename
                            _statusMessage.value = "内容已保存到后端"
                            _phase.value = SharePhase.Compiling
                            _progress.value = 0.8f

                            _statusMessage.value = "正在触发知识库编译..."
                            try {
                                val compileResponse = repo.compile(response.filename)
                                _progress.value = 1.0f
                                _statusMessage.value = compileResponse.message ?: "编译完成"
                                _phase.value = SharePhase.Success(
                                    filename = response.filename ?: "",
                                    message = compileResponse.message ?: "分享内容已成功编译进知识库"
                                )
                            } catch (e: Exception) {
                                _progress.value = 1.0f
                                _statusMessage.value = "内容已保存，编译触发失败: ${e.message}"
                                _phase.value = SharePhase.Success(
                                    filename = response.filename ?: "",
                                    message = "内容已保存到 raw/，编译触发失败: ${e.message}"
                                )
                            }
                        } else {
                            _phase.value = SharePhase.Error("服务器返回异常: ${response.status}")
                        }
                    }

                    MainActivity.ShareType.IMAGE,
                    MainActivity.ShareType.TEXT_FILE,
                    MainActivity.ShareType.FILE -> {
                        _statusMessage.value = "正在上传文件..."
                        val totalFiles = data.uris.size
                        val uploadedNames = mutableListOf<String>()

                        for ((index, uri) in data.uris.withIndex()) {
                            _progress.value = 0.3f + (index.toFloat() / totalFiles) * 0.35f
                            _statusMessage.value = "正在上传文件 (${index + 1}/$totalFiles)..."

                            val fileName = getFileName(context, uri) ?: "shared_file_${System.currentTimeMillis()}"
                            val fileContent = readFileContent(context, uri)
                            if (fileContent == null) {
                                _phase.value = SharePhase.Error("无法读取文件: $fileName")
                                return@launch
                            }

                            val requestBody = fileContent.toRequestBody("application/octet-stream".toMediaTypeOrNull())
                            val multipartPart = MultipartBody.Part.createFormData("file", fileName, requestBody)

                            val response = repo.uploadSource(multipartPart)
                            uploadedNames.add(response.filename ?: fileName)
                        }

                        _progress.value = 0.7f
                        _statusMessage.value = "已上传 ${uploadedNames.size} 个文件"
                        _resultFilename.value = uploadedNames.joinToString(", ")

                        _phase.value = SharePhase.Compiling
                        _statusMessage.value = "正在触发知识库编译..."
                        try {
                            val compileResponse = repo.compile()
                            _progress.value = 1.0f
                            _statusMessage.value = compileResponse.message ?: "编译完成"
                            _phase.value = SharePhase.Success(
                                filename = uploadedNames.joinToString(", "),
                                message = "已上传 ${uploadedNames.size} 个文件并触发编译"
                            )
                        } catch (e: Exception) {
                            _progress.value = 1.0f
                            _statusMessage.value = "文件已保存，编译触发失败: ${e.message}"
                            _phase.value = SharePhase.Success(
                                filename = uploadedNames.joinToString(", "),
                                message = "文件已保存到 raw/，编译触发失败: ${e.message}"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _phase.value = SharePhase.Error("操作失败: ${e.message}")
                _progress.value = 0f
                _statusMessage.value = ""
            }
        }
    }

    /** 重置分享状态。 */
    fun reset() {
        _phase.value = SharePhase.Idle
        _progress.value = 0f
        _statusMessage.value = ""
        _resultFilename.value = null
    }

    /** 清除分享数据。 */
    fun clearShareData() {
        _shareData.value = null
        MainActivity.pendingShare = null
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && it.moveToFirst()) {
                name = it.getString(nameIndex)
            }
        }
        if (name.isNullOrBlank()) {
            name = uri.lastPathSegment ?: "shared_file"
        }
        return name
    }

    private fun readFileContent(context: Context, uri: Uri): ByteArray? {
        return try {
            val stream = context.contentResolver.openInputStream(uri) ?: return null
            stream.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        fun Factory(appSettings: AppSettings): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ShareViewModel(appSettings = appSettings, context = appSettings.appContext) as T
                }
            }
        }
    }
}
