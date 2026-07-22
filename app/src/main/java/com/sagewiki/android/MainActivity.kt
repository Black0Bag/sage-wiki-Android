package com.sagewiki.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.sagewiki.android.data.AppSettings
import com.sagewiki.android.network.SageWikiApi
import com.sagewiki.android.network.ShareRequest
import com.sagewiki.android.ui.main.MainScreen
import com.sagewiki.android.ui.setup.SetupScreen
import com.sagewiki.android.ui.theme.SageWikiTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var settings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AppSettings(applicationContext)

        val sharedText = extractSharedText(intent)

        setContent {
            SageWikiTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var setupDone by remember { mutableStateOf(false) }
                    val scope = rememberCoroutineScope()

                    LaunchedEffect(Unit) {
                        settings.isSetupDone.collect { done ->
                            setupDone = done
                        }
                    }

                    if (!setupDone) {
                        SetupScreen(
                            onSaved = {
                                setupDone = true
                            }
                        )
                    } else {
                        MainScreen(
                            initialSharedText = sharedText,
                            appSettings = settings
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val sharedText = extractSharedText(intent)
        if (sharedText != null) {
            handleShare(sharedText)
        }
    }

    private fun extractSharedText(intent: Intent?): ShareData? {
        if (intent?.action != Intent.ACTION_SEND && intent?.action != Intent.ACTION_SEND_MULTIPLE) {
            return null
        }
        if (intent.action == Intent.ACTION_SEND) {
            val type = intent.type ?: return null
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: ""
            val title = subject.ifBlank { "分享内容" }
            val url = extractUrl(text) ?: ""
            val body = text?.replace(url, "")?.trim() ?: ""
            return ShareData(title = title, text = body, url = url)
        }
        return null
    }

    private fun extractUrl(text: String?): String? {
        if (text == null) return null
        val uri = Uri.parse(text.trim())
        return if (uri.scheme != null && (uri.scheme == "http" || uri.scheme == "https")) {
            uri.toString()
        } else null
    }

    data class ShareData(val title: String, val text: String, val url: String)

    companion object {
        var pendingShare: ShareData? = null
    }

    private fun handleShare(data: ShareData) {
        companion.pendingShare = data
    }
}
