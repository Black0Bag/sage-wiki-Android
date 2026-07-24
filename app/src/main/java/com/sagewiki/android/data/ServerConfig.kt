package com.sagewiki.android.data

/**
 * 服务器配置数据类。
 * 从 AppSettings.kt 中提取，职责单一化。
 */
data class ServerConfig(
    val name: String,
    val url: String,
    val token: String
)
