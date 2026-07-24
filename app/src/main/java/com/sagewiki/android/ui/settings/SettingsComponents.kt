package com.sagewiki.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

// ═══════════════════════════════════════════════════════════════════════════
//  ModelField — a label-text-field + refresh button row used for LLM model
//  role inputs. When the refresh button is clicked, `onFetch` is invoked so
//  the caller can fetch model list and open the picker.
//
//  Original code had this as a `private fun` inside SettingsScreen.kt.
//  Extracted here for reuse and cleaner separation.
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun ModelField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onFetch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onFetch) {
            Icon(Icons.Filled.Refresh, contentDescription = "获取模型")
        }
    }
}

/**
 * Overload that accepts a `MutableState<String>` for backward compatibility
 * with code that still uses `remember { mutableStateOf("") }` directly.
 */
@Composable
fun ModelField(
    label: String,
    value: MutableState<String>,
    onFetch: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModelField(
        label = label,
        value = value.value,
        onValueChange = { value.value = it },
        onFetch = onFetch,
        modifier = modifier
    )
}

// ═══════════════════════════════════════════════════════════════════════════
//  EmbeddingModelRow — embedding model input + search button (different icon)
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun EmbeddingModelRow(
    value: String,
    onValueChange: (String) -> Unit,
    onFetch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Model") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onFetch) {
            Icon(Icons.Filled.Search, contentDescription = "获取嵌入模型")
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  PasswordField — OutlinedTextField with show/hide toggle for API keys
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None
                               else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff
                                  else Icons.Filled.Visibility,
                    contentDescription = "切换密码可见性"
                )
            }
        },
        modifier = modifier.fillMaxWidth()
    )
}

// ═══════════════════════════════════════════════════════════════════════════
//  NumberField — OutlinedTextField with numeric keyboard for fields like
//  Embedding Dimensions
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = KeyboardType.Number
        ),
        modifier = modifier.fillMaxWidth()
    )
}

// ═══════════════════════════════════════════════════════════════════════════
//  ModelPickerDialog — reusable model list picker
//
//  Shows a scrollable list of model IDs. `onSelect` is called with the
//  chosen model id, then the dialog is dismissed.
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun ModelPickerDialog(
    title: String,
    models: List<String>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                items(models) { modelId ->
                    TextButton(
                        onClick = {
                            onSelect(modelId)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(modelId)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

// ═══════════════════════════════════════════════════════════════════════════
//  SaveButton — full-width save button with loading indicator
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun SaveButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    label: String = "💾 保存配置",
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(48.dp)
    ) {
        if (!enabled) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
        } else {
            Text(label)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  TestResultCard — displays model test result (success or failure)
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun TestResultCard(
    success: Boolean,
    model: String?,
    latencyMs: Long?,
    statusCode: Int?,
    error: String?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (success) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                if (success) "✅ 测试成功" else "❌ 测试失败",
                fontWeight = FontWeight.Bold
            )
            Text("模型: $model")
            latencyMs?.let { Text("延迟: ${it}ms") }
            statusCode?.let { Text("状态码: $it") }
            error?.let { Text("错误: $it") }
        }
    }
}
