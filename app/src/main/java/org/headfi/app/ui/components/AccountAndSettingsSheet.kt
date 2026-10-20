package org.headfi.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.headfi.app.model.AppPreferences
import org.headfi.app.model.QuickDestination
import org.headfi.app.model.ThemeMode
import org.headfi.app.model.UserSessionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountAndSettingsSheet(
    sessionState: UserSessionState,
    preferences: AppPreferences,
    onDismiss: () -> Unit,
    onNavigateUrl: (String) -> Unit,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onAmoledBlackChange: (Boolean) -> Unit,
    onInjectWebDarkChange: (Boolean) -> Unit,
    onAdBlockerChange: (Boolean) -> Unit,
    onRememberLastPageChange: (Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Account & Persistent Session Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(48.dp)
                        ) {
                            HeadFiMonochromeLogoIcon(
                                size = 34.dp,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(7.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            val displayUser = sessionState.username.ifBlank {
                                preferences.savedUsername.ifBlank { "Head-Fi Member" }
                            }
                            Text(
                                text = if (sessionState.isLoggedIn || sessionState.hasRememberMeCookie) {
                                    displayUser
                                } else {
                                    "Guest Session"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = if (sessionState.isLoggedIn || sessionState.hasRememberMeCookie) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = when {
                                        sessionState.isLoggedIn && sessionState.hasRememberMeCookie ->
                                            "Logged in • Persistent across reboots"
                                        sessionState.hasRememberMeCookie ->
                                            "Session cookie active & backed up"
                                        else ->
                                            "Sign in to sync watched threads & alerts"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (sessionState.isLoggedIn || sessionState.hasRememberMeCookie) {
                            Button(
                                onClick = {
                                    onNavigateUrl("https://www.head-fi.org/account/")
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PersonOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Account")
                            }
                            OutlinedButton(
                                onClick = {
                                    onLogoutClick()
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Logout,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Log Out")
                            }
                        } else {
                            Button(
                                onClick = {
                                    onLoginClick()
                                    onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Login,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sign In to Head-Fi.org (Remember Login)")
                            }
                        }
                    }
                }
            }

            // Quick Member Destinations
            Text(
                text = "Quick Member Access",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp)
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = MaterialTheme.shapes.large
            ) {
                QuickNavRow(
                    icon = Icons.Outlined.NotificationsNone,
                    title = QuickDestination.ALERTS.label,
                    subtitle = QuickDestination.ALERTS.subtitle,
                    badgeCount = sessionState.unreadAlerts,
                    onClick = {
                        onNavigateUrl(QuickDestination.ALERTS.url)
                        onDismiss()
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                QuickNavRow(
                    icon = Icons.Outlined.MailOutline,
                    title = QuickDestination.CONVERSATIONS.label,
                    subtitle = QuickDestination.CONVERSATIONS.subtitle,
                    badgeCount = sessionState.unreadConversations,
                    onClick = {
                        onNavigateUrl(QuickDestination.CONVERSATIONS.url)
                        onDismiss()
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                QuickNavRow(
                    icon = Icons.Outlined.Visibility,
                    title = QuickDestination.WATCHED_THREADS.label,
                    subtitle = QuickDestination.WATCHED_THREADS.subtitle,
                    onClick = {
                        onNavigateUrl(QuickDestination.WATCHED_THREADS.url)
                        onDismiss()
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                QuickNavRow(
                    icon = Icons.Outlined.BookmarkBorder,
                    title = QuickDestination.BOOKMARKS.label,
                    subtitle = QuickDestination.BOOKMARKS.subtitle,
                    onClick = {
                        onNavigateUrl(QuickDestination.BOOKMARKS.url)
                        onDismiss()
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                QuickNavRow(
                    icon = Icons.Outlined.Forum,
                    title = QuickDestination.ARTICLES.label,
                    subtitle = QuickDestination.ARTICLES.subtitle,
                    onClick = {
                        onNavigateUrl(QuickDestination.ARTICLES.url)
                        onDismiss()
                    }
                )
            }

            // Material 3 Expressive Appearance & Browsing Settings
            Text(
                text = "Design & Browsing Experience",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp)
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "App Theme",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = preferences.themeMode == mode,
                                onClick = { onThemeModeChange(mode) },
                                label = { Text(mode.label) }
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))

                SettingToggleRow(
                    icon = Icons.Outlined.Palette,
                    title = "Material You Dynamic Color",
                    subtitle = "Adapt app colors to your Android wallpaper palette",
                    checked = preferences.dynamicColors,
                    onCheckedChange = onDynamicColorChange
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))

                SettingToggleRow(
                    icon = Icons.Outlined.DarkMode,
                    title = "Head-Fi Web Dark Theme",
                    subtitle = "Style forum threads & cards with a dark surface when in dark mode",
                    checked = preferences.injectWebDarkTheme,
                    onCheckedChange = onInjectWebDarkChange
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))

                SettingToggleRow(
                    icon = Icons.Outlined.DarkMode,
                    title = "Pure AMOLED Black",
                    subtitle = "Use true #000000 black background on OLED displays",
                    checked = preferences.amoledBlack,
                    onCheckedChange = onAmoledBlackChange
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))

                SettingToggleRow(
                    icon = Icons.Outlined.Shield,
                    title = "Ad & Tracker Blocker",
                    subtitle = "Block third-party ad networks while keeping Cloudflare login intact",
                    checked = preferences.adBlockerEnabled,
                    onCheckedChange = onAdBlockerChange
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))

                SettingToggleRow(
                    icon = Icons.Outlined.CheckCircle,
                    title = "Restore Last Visited Thread on Launch",
                    subtitle = "Resume where you left off across app restarts and reboots",
                    checked = preferences.rememberLastPage,
                    onCheckedChange = onRememberLastPageChange
                )
            }
        }
    }
}

@Composable
private fun QuickNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badgeCount: Int = 0,
    onClick: () -> Unit
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        headlineContent = {
            Text(text = title, fontWeight = FontWeight.Medium)
        },
        supportingContent = {
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
        },
        trailingContent = {
            if (badgeCount > 0) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ) {
                    Text(badgeCount.toString())
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun SettingToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        headlineContent = {
            Text(text = title, fontWeight = FontWeight.Medium)
        },
        supportingContent = {
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        },
        modifier = Modifier.clickable { onCheckedChange(!checked) }
    )
}
