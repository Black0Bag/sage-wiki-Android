package com.sagewiki.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore(name = "sage_wiki_settings")

class AppSettings(private val context: Context) {

    companion object {
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_BEARER_TOKEN = stringPreferencesKey("bearer_token")
        private val KEY_SETUP_DONE = stringPreferencesKey("setup_done")
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

    suspend fun saveServerConfig(url: String, token: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SERVER_URL] = url.trimEnd('/')
            prefs[KEY_BEARER_TOKEN] = token
            prefs[KEY_SETUP_DONE] = "true"
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    fun getServerUrlSync(): String = runBlocking { serverUrl.first() }
    fun getBearerTokenSync(): String = runBlocking { bearerToken.first() }
}
