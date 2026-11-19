package com.meoify.music.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Battery0Bar
import androidx.compose.material.icons.filled.Battery1Bar
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryUnknown
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.meoify.music.playback.isBluetoothAudio

/** Android-style volume and output-device picker opened from the player speaker button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeSheet(
    selectedOutputType: Int,
    selectedOutputDeviceId: Int?,
    bluetoothBattery: Int?,
    onSelectDevice: (AudioDeviceInfo) -> Unit,
    onRefreshBattery: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    var volume by remember { mutableStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, max)) }
    var lastVolume by remember { mutableStateOf(volume.coerceAtLeast(1)) }
    val outputs = remember { outputDevices(audio) }
    val selected = remember(selectedOutputType, selectedOutputDeviceId, outputs) {
        outputs.firstOrNull {
            it.device.id == selectedOutputDeviceId && it.device.type == selectedOutputType
        } ?: outputs.firstOrNull { it.device.type == selectedOutputType }
        ?: outputs.firstOrNull { it.isBuiltIn }
        ?: outputs.firstOrNull()
    }
    val bluetoothOutput = remember(outputs) {
        outputs.firstOrNull { it.device.type.isBluetoothAudio() }
    }
    val batteryPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) onRefreshBattery(bluetoothOutput?.device?.productName())
    }
    LaunchedEffect(bluetoothOutput) {
        if (bluetoothOutput != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            batteryPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

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
                        text = selected?.title ?: "Phone speaker",
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
                outputs.forEach { output ->
                    val isSelected = selected?.key == output.key
                    Surface(
                        onClick = { onSelectDevice(output.device) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = output.icon,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = output.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = output.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (output.device.type.isBluetoothAudio()) {
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    imageVector = batteryIcon(bluetoothBattery),
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(17.dp),
                                )
                                if (bluetoothBattery != null) {
                                    Spacer(Modifier.width(2.dp))
                                    Text(
                                        text = "$bluetoothBattery%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (isSelected) {
                                Spacer(Modifier.width(6.dp))
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
    val key: String,
    val title: String,
    val subtitle: String,
    val device: AudioDeviceInfo,
    val icon: ImageVector,
    val isBuiltIn: Boolean,
)

/** One entry per physical output; phone speaker variants are merged into one. */
private fun outputDevices(audio: AudioManager): List<AudioOutput> {
    val devices = runCatching { audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }
        .getOrDefault(emptyArray())
        .toList()
        .distinctBy { it.id }
        // Drop OEM/phantom routing entries (e.g. a "Phone speaker" row whose
        // product name is the device model) by keeping only known output types.
        .filter { it.isKnownOutputType() }
    val builtIn = devices.filter { it.isBuiltInSpeaker() }
        .minByOrNull { if (it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE) 1 else 0 }
    val others = devices.filterNot { it.isBuiltInSpeaker() }
        .distinctBy { it.outputKey() }

    return listOfNotNull(builtIn?.let { device ->
        AudioOutput(
            key = "builtin-speaker",
            title = "Phone speaker",
            subtitle = "Built-in speaker",
            device = device,
            icon = Icons.Filled.Speaker,
            isBuiltIn = true,
        )
    }) + others.map { device ->
        val name = device.productName()
        AudioOutput(
            key = device.outputKey(),
            title = name.ifBlank { outputLabel(device.type) },
            subtitle = outputLabel(device.type),
            device = device,
            icon = outputIcon(device.type),
            isBuiltIn = false,
        )
    }
}

private fun AudioDeviceInfo.isKnownOutputType(): Boolean = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE,
    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_USB_ACCESSORY,
    AudioDeviceInfo.TYPE_HDMI,
    AudioDeviceInfo.TYPE_LINE_ANALOG,
    AudioDeviceInfo.TYPE_LINE_DIGITAL -> true
    else -> false
}
private fun AudioDeviceInfo.isBuiltInSpeaker(): Boolean =
    type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
        type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE ||
        // Some OEMs also expose the earpiece as an output. Media uses the
        // loudspeaker, so showing both creates duplicate "Phone speaker" rows.
        type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE

private fun AudioDeviceInfo.outputKey(): String = "${type}|${productName().lowercase()}"

private fun AudioDeviceInfo.productName(): String =
    runCatching { productName?.toString()?.trim() }.getOrNull().orEmpty()

/** Public so the player control can show the same output icon as this sheet. */
fun outputIcon(type: Int): ImageVector = when (type) {
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> Icons.Filled.Bluetooth
    AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> Icons.Filled.Headset
    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY -> Icons.Filled.Usb
    else -> Icons.Filled.Speaker
}

/** Battery glyph matched to the last level reported by the Bluetooth stack. */
fun batteryIcon(level: Int?): ImageVector = when {
    level == null -> Icons.Filled.BatteryUnknown
    level >= 95 -> Icons.Filled.BatteryFull
    level >= 80 -> Icons.Filled.Battery6Bar
    level >= 65 -> Icons.Filled.Battery5Bar
    level >= 50 -> Icons.Filled.Battery4Bar
    level >= 35 -> Icons.Filled.Battery3Bar
    level >= 20 -> Icons.Filled.Battery2Bar
    level >= 10 -> Icons.Filled.Battery1Bar
    else -> Icons.Filled.Battery0Bar
}

/** Tint for the battery glyph based on the reported level. */
fun batteryColor(level: Int?): androidx.compose.ui.graphics.Color {
    return when {
        level == null -> androidx.compose.ui.graphics.Color.Gray
        level < 20 -> androidx.compose.ui.graphics.Color(0xFFEF4444)
        level < 50 -> androidx.compose.ui.graphics.Color(0xFFF59E0B)
        else -> androidx.compose.ui.graphics.Color(0xFF22C55E)
    }
}
fun outputLabel(type: Int): String = when (type) {
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth device"
    AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones"
    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB audio"
    AudioDeviceInfo.TYPE_HDMI -> "HDMI output"
    else -> "Phone speaker"
}
