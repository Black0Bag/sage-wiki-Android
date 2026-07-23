package com.sagewiki.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "sage_wiki_settings")

data class ServerConfig(
    val name: String,
    val url: String,
    val token: String
)

class AppSettings(private val context: Context) {

    companion object {
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_BEARER_TOKEN = stringPreferencesKey("bearer_token")
        private val KEY_SETUP_DONE = stringPreferencesKey("setup_done")
        private val KEY_SERVER_LIST = stringPreferencesKey("server_list")
        private val KEY_ACTIVE_SERVER = stringPreferencesKey("active_server")
    }

    val serverUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SERVER_URL] ?: ""
    }

    val bearerToken: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_BEARER_TOKEN] ?: ""
    }

    val isSetupDone: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SETUP_DONE] == "true"
    }

    val activeServerName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_SERVER] ?: ""
    }

    suspend fun getServerList(): List<ServerConfig> {
        val raw = context.dataStore.data.first()[KEY_SERVER_LIST] ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("\n").filter { it.isNotBlank() }.map { line ->
            val parts = line.split("\t")
            if (parts.size >= 3) ServerConfig(parts[0], parts[1], parts[2])
            else ServerConfig(parts.getOrElse(0) { "服务器" }, parts.getOrElse(1) { "" }, parts.getOrElse(2) { "" })
        }
    }

    suspend fun saveServer(name: String, url: String, token: String) {
        val list = getServerList().toMutableList()
        val idx = list.indexOfFirst { it.name == name }
        val config = ServerConfig(name, url.trimEnd('/'), token)
        if (idx >= 0) list[idx] = config else list.add(config)
        saveServerList(list)
    }

    suspend fun deleteServer(name: String) {
        val list = getServerList().filter { it.name != name }
        saveServerList(list)
    }

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

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun getServerUrl(): String = serverUrl.first()
    suspend fun getBearerToken(): String = bearerToken.first()
}
