package com.meoify.music.ui.components

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Android-style volume and output-device picker opened from the player speaker button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    var volume by remember { mutableStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, max)) }
    var lastVolume by remember { mutableStateOf(volume.coerceAtLeast(1)) }
    val outputs = remember { outputDevices(audio) }
    val active = outputs.firstOrNull { it.type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER } ?: outputs.firstOrNull()

    fun setVolume(value: Int) {
        val level = value.coerceIn(0, max)
        volume = level
        if (level > 0) lastVolume = level
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.Filled.VolumeUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(9.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Volume & output",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = active?.detail ?: "Phone speaker",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { setVolume(if (volume == 0) lastVolume else 0) }) {
                    Icon(
                        imageVector = if (volume == 0) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                        contentDescription = if (volume == 0) "Unmute" else "Mute",
                    )
                }
                Slider(
                    value = volume.toFloat(),
                    onValueChange = { setVolume(it.toInt()) },
                    valueRange = 0f..max.toFloat(),
                    steps = (max - 1).coerceAtLeast(0),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${volume * 100 / max}%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(42.dp),
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = "Connected devices",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            if (outputs.isEmpty()) {
                Text(
                    text = "Only the phone speaker is available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                outputs.forEach { device ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = device.icon,
                            contentDescription = null,
                            tint = if (device == active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(device.detail, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = device.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (device == active) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Active output",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            FilledTonalButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    }
                    onDismiss()
                },
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Connect a Bluetooth device")
            }
        }
    }
}

private data class AudioOutput(
    val label: String,
    val detail: String,
    val type: Int,
    val icon: ImageVector,
)

private fun outputDevices(audio: AudioManager): List<AudioOutput> =
    runCatching { audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }
        .getOrDefault(emptyArray())
        .mapNotNull { device ->
            val detail = device.type.detail()
            val label = runCatching { device.productName?.toString()?.trim() }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: detail
            AudioOutput(label = label, detail = detail, type = device.type, icon = device.type.icon())
        }
        .distinctBy { "${it.label}|${it.type}" }

private fun Int.detail(): String = when (this) {
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth speaker"
    AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones"
    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB audio"
    AudioDeviceInfo.TYPE_HDMI -> "HDMI output"
    else -> "Phone speaker"
}

private fun Int.icon(): ImageVector = when (this) {
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> Icons.Filled.Bluetooth
    AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> Icons.Filled.Headset
    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> Icons.Filled.Usb
    else -> Icons.Filled.Speaker
}
