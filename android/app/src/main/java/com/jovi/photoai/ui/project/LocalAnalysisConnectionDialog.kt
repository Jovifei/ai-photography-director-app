package com.jovi.photoai.ui.project

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Pairing stays in the current process; the access token is not persisted in project data. */
@Composable
internal fun LocalAnalysisConnectionDialog(
    photoCount: Int,
    onDismiss: () -> Unit,
    onPair: (baseUrl: String, pairingCode: String, certificatePin: String) -> Unit,
    errorMessage: String? = null,
) {
    var baseUrl by rememberSaveable { mutableStateOf("") }
    var pairingCode by rememberSaveable { mutableStateOf("") }
    var certificatePin by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("连接本机分析电脑") },
        text = {
            Column {
                Text("仅连接同一私有 Wi-Fi；本项目的 $photoCount 张照片会逐张发送到已配对的 Windows 服务，不保留在服务端。")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("HTTPS 地址") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = pairingCode,
                    onValueChange = { pairingCode = it },
                    label = { Text("一次性配对码") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = certificatePin,
                    onValueChange = { certificatePin = it },
                    label = { Text("证书 pin（sha256/…）") },
                    singleLine = true,
                )
                errorMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = {
            TextButton(
                onClick = { onPair(baseUrl, pairingCode, certificatePin) },
                enabled = baseUrl.isNotBlank() && pairingCode.isNotBlank() && certificatePin.isNotBlank(),
            ) { Text("配对并继续") }
        },
    )
}
