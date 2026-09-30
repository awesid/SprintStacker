package app.sprintstacker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sprintstacker.BuildConfig

@Composable
fun SettingsScreen(
    vibrate: Boolean,
    privacyOptionsRequired: Boolean,
    onVibrate: (Boolean) -> Unit,
    onPrivacyOptions: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onResetAll: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmReset by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, colors = IconButtonDefaults.iconButtonColors(containerColor = Palette.Panel2)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 12.dp))
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Group("Sprints") {
                Item(
                    "Vibrate when a block lands",
                    trailing = {
                        Switch(
                            checked = vibrate, onCheckedChange = onVibrate,
                            colors = SwitchDefaults.colors(checkedTrackColor = Palette.Aqua),
                        )
                    },
                )
                Item(
                    "Leaving the app",
                    "Leaving during a sprint counts as giving up: pressing Home, switching apps or " +
                        "swiping the app away. Locking the screen does not.",
                )
            }
            Group("Ads and privacy") {
                if (privacyOptionsRequired) {
                    Item("Privacy options", "Change your ad consent choices", onClick = onPrivacyOptions, trailingText = "›")
                }
                Item("Privacy policy", "How Google AdMob uses device identifiers for ads and analytics", onClick = onPrivacyPolicy, trailingText = "↗")
                Item(
                    "How ads work here",
                    "Rewarded videos are always optional. Full-screen ads only appear after a sprint ends " +
                        "or when you clear the board, never while the timer runs.",
                )
            }
            Group("Your data") {
                Item("Stored on this device", "Tower, scores and sprint history stay on your phone. No account and no server.")
                Item("Reset all data", "Clears the tower, score and best score", danger = true, onClick = { confirmReset = true })
            }
            Group("About") {
                if (BuildConfig.TEST_ADS) Item("Google test ad units", "This build shows test ads only", trailingText = "ON")
                Item("Version", trailingText = BuildConfig.VERSION_NAME)
            }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset all data?") },
            text = { Text("Your tower, score and best score are deleted from this device. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; onResetAll() }) { Text("Reset", color = Palette.Hazard) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
            containerColor = Palette.Panel,
        )
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title.uppercase(), fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold,
            color = Palette.Tangerine, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
        content()
    }
}

@Composable
private fun Item(
    title: String,
    subtitle: String? = null,
    danger: Boolean = false,
    trailingText: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.Panel, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (danger) Palette.Hazard else Palette.Text)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = Palette.Muted, lineHeight = 16.sp)
        }
        if (trailingText != null) Text(trailingText, fontSize = 12.sp, color = if (trailingText == "ON") Color(0xFF231A00) else Palette.Muted,
            modifier = if (trailingText == "ON") Modifier.background(Palette.Lemon, RoundedCornerShape(5.dp)).padding(horizontal = 7.dp, vertical = 2.dp) else Modifier)
        trailing?.invoke()
    }
}
