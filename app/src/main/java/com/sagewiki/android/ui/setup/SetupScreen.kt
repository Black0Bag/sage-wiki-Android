package com.sagewiki.android.ui.setup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sagewiki.android.data.AppSettings
import com.sagewiki.android.network.SageWikiApi
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(onSaved: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    val scope = rememberCoroutineScope()

    var serverUrl by remember { mutableStateOf("") }
    var bearerToken by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testSuccess by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Sage Wiki",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "配置您的 wiki 服务器",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = serverUrl,
                onValueChange = {
                    serverUrl = it
                    testResult = null
                    testSuccess = false
                },
                label = { Text("服务器地址") },
                placeholder = { Text("http://192.168.1.100:3333") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                ),
                enabled = !saving
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = bearerToken,
                onValueChange = { bearerToken = it },
                label = { Text("Bearer Token（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showToken) VisualTransformation.None
                    else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showToken = !showToken }) {
                        Icon(
                            if (showToken) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                            contentDescription = if (showToken) "隐藏" else "显示"
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                enabled = !saving
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (testResult != null) {
                Text(
                    text = testResult!!,
                    color = if (testSuccess) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                            .padding(vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = {
                        if (serverUrl.isBlank()) {
                            testResult = "请输入服务器地址"
                            testSuccess = false
                            return@Button
                        }
                        testing = true
                        testResult = null
                        testSuccess = false
                        scope.launch {
                            try {
                                val api = SageWikiApi.create(serverUrl, bearerToken.ifBlank { null })
                                val health = api.health()
                                if (health.status == "healthy") {
                                    testResult = "✓ 连接成功 — ${health.project ?: ""}"
                                    testSuccess = true
                                } else {
                                    testResult = "服务器状态异常: ${health.status}"
                                    testSuccess = false
                                }
                            } catch (e: Exception) {
                                testResult = "✗ 连接失败: ${e.localizedMessage ?: "未知错误"}"
                                testSuccess = false
                            }
                            testing = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !testing && !saving && serverUrl.isNotBlank()
                ) {
                    if (testing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("测试")
                }

                Button(
                    onClick = {
                        saving = true
                        scope.launch {
                            settings.saveServerConfig(serverUrl, bearerToken)
                            saving = false
                            onSaved()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = testSuccess && !saving
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("保存")
                }
            }
        }
    }
}
