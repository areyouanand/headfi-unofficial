package org.headfi.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.headfi.app.data.ContentRepository
import org.headfi.app.model.UserSessionState
import org.headfi.app.net.HeadFiUserAgent

/**
 * Explains whether the native layer currently has a usable session.
 *
 * This exists because the hardest failure to diagnose in the field is a
 * Cloudflare challenge: the app looks empty rather than broken. The probe runs
 * the same request the native screens do, so its result is the real answer.
 */
@Composable
fun SessionHealthDialog(
    session: UserSessionState,
    repository: ContentRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val userAgent = remember { HeadFiUserAgent.mobile(context) }

    var probe by remember { mutableStateOf<String?>(null) }
    var cookieNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var running by remember { mutableStateOf(true) }
    var attempt by remember { mutableIntStateOf(0) }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(attempt) {
        running = true
        cookieNames = repository.storedCookieNames()
        probe = repository.probe()
        running = false
    }

    val diagnostics = buildString {
        appendLine("Head-Fi session health")
        appendLine("Signed in: ${session.isLoggedIn}")
        appendLine("Remember-me cookie: ${session.hasRememberMeCookie}")
        appendLine("Vault cookies: ${cookieNames.joinToString(", ").ifBlank { "none" }}")
        appendLine("User-Agent: $userAgent")
        appendLine("Probe: ${probe ?: "running…"}")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Session health") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "If native screens look empty, this check tells you whether " +
                        "Head-Fi is accepting the app's saved session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()

                HealthRow(
                    label = "Session",
                    value = when {
                        session.isLoggedIn -> "Signed in as ${session.username.ifBlank { "your account" }}"
                        session.hasRememberMeCookie -> "Cookie saved, waiting for a page load"
                        else -> "Not signed in"
                    }
                )
                HealthRow(
                    label = "Vault",
                    value = if (cookieNames.isEmpty()) {
                        "No saved cookies"
                    } else {
                        "${cookieNames.size} cookies: ${cookieNames.joinToString(", ")}"
                    }
                )
                HealthRow(label = "User agent", value = userAgent)
                HealthRow(label = "Browser check", value = probe ?: "Checking…")

                if (running) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                    }
                }

                Text(
                    text = "Cookies are stored only on this device and are excluded from " +
                        "Android cloud backup and device transfer.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    clipboard.setText(AnnotatedString(diagnostics))
                    copied = true
                }
            ) {
                Text(if (copied) "Copied" else "Copy diagnostics")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { attempt++ }, enabled = !running) {
                    Text("Check again")
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}

@Composable
private fun HealthRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
