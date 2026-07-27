package com.sagewiki.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "sage_wiki_settings")

/**
 * 管理应用持久化设置（服务器地址、Bearer Token、主题等），基于 DataStore Preferences 实现。
 *
 * @param context 应用上下文，用于访问 DataStore。
 */
class AppSettings(private val context: Context) {

    /** 提供对内部 context 的只读访问（仅供 ViewModel Factory 使用）。 */
    val appContext: Context get() = context

    companion object {
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_BEARER_TOKEN = stringPreferencesKey("bearer_token")
        private val KEY_SETUP_DONE = stringPreferencesKey("setup_done")
        private val KEY_SERVER_LIST = stringPreferencesKey("server_list")
        private val KEY_ACTIVE_SERVER = stringPreferencesKey("active_server")
        private val KEY_DARK_THEME = stringPreferencesKey("dark_theme")
    }

    /** 当前服务器 URL，空字符串表示未配置。 */
    val serverUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SERVER_URL] ?: ""
    }

    /** 当前服务器的 Bearer Token，空字符串表示未配置。 */
    val bearerToken: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_BEARER_TOKEN] ?: ""
    }

    /** 初始设置是否已完成。 */
    val isSetupDone: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SETUP_DONE] == "true"
    }

    /** 当前激活的服务器名称，空字符串表示未选择。 */
    val activeServerName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_SERVER] ?: ""
    }

    /** 是否启用暗色主题，默认为 true。 */
    val isDarkTheme: Flow<Boolean> = context.dataStore.data.map { prefs ->
        // Default to true (dark theme) for backward compatibility
        prefs[KEY_DARK_THEME] != "false"
    }

    /**
     * 读取已保存的服务器列表。
     *
     * @return 服务器配置列表；无数据时返回空列表。
     */
    suspend fun getServerList(): List<ServerConfig> {
        val raw = context.dataStore.data.first()[KEY_SERVER_LIST] ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("\n").filter { it.isNotBlank() }.map { line ->
            val parts = line.split("\t")
            if (parts.size >= 3) ServerConfig(parts[0], parts[1], parts[2])
            else ServerConfig(parts.getOrElse(0) { "服务器" }, parts.getOrElse(1) { "" }, parts.getOrElse(2) { "" })
        }
    }

    /**
     * 保存或更新一个服务器配置。若同名服务器已存在则覆盖，否则追加。
     *
     * @param name 服务器显示名称。
     * @param url 服务器 URL，末尾斜杠会被自动去除。
     * @param token 服务器的 Bearer Token。
     */
    suspend fun saveServer(name: String, url: String, token: String) {
        val list = getServerList().toMutableList()
        val idx = list.indexOfFirst { it.name == name }
        val config = ServerConfig(name, url.trimEnd('/'), token)
        if (idx >= 0) list[idx] = config else list.add(config)
        saveServerList(list)
    }

    /**
     * 删除指定名称的服务器配置。
     *
     * @param name 要删除的服务器名称。
     */
    suspend fun deleteServer(name: String) {
        val list = getServerList().filter { it.name != name }
        saveServerList(list)
    }

    /**
     * 将指定服务器设为当前激活服务器，同时更新 URL、Token 和设置完成状态。
     *
     * @param name 要激活的服务器名称，需已存在于服务器列表中。
     */
    suspend fun setActiveServer(name: String) {
        val list = getServerList()
        val target = list.find { it.name == name }
        if (target != null) {
            context.dataStore.edit { prefs ->
                prefs[KEY_ACTIVE_SERVER] = name
                prefs[KEY_SERVER_URL] = target.url
                prefs[KEY_BEARER_TOKEN] = target.token
                prefs[KEY_SETUP_DONE] = "true"
            }
        }
    }

    private suspend fun saveServerList(list: List<ServerConfig>) {
        val raw = list.joinToString("\n") { "${it.name}\t${it.url}\t${it.token}" }
        context.dataStore.edit { prefs ->
            prefs[KEY_SERVER_LIST] = raw
        }
    }

    /**
     * 快速保存默认服务器配置，同时写入 URL、Token、激活服务器及设置完成状态。
     *
     * @param url 服务器 URL，末尾斜杠会被自动去除。
     * @param token 服务器的 Bearer Token。
     */
    suspend fun saveServerConfig(url: String, token: String) {
        val name = "默认服务器"
        context.dataStore.edit { prefs ->
            prefs[KEY_SERVER_URL] = url.trimEnd('/')
            prefs[KEY_BEARER_TOKEN] = token
            prefs[KEY_SETUP_DONE] = "true"
            prefs[KEY_ACTIVE_SERVER] = name
        }
        saveServer(name, url, token)
    }

    /** 清除所有已保存的设置数据。 */
    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    /**
     * 设置是否启用暗色主题。
     *
     * @param enabled 为 true 时启用暗色主题，false 时禁用。
     */
    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DARK_THEME] = if (enabled) "true" else "false"
        }
    }

    /** 一次性读取当前服务器 URL，为 Flow 的便捷方法。 */
    suspend fun getServerUrl(): String = serverUrl.first()
    /** 一次性读取当前 Bearer Token，为 Flow 的便捷方法。 */
    suspend fun getBearerToken(): String = bearerToken.first()
}
