package com.example.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DeviceRomGuide(
    val romDisplayName: String,
    val settingsPathHint: String,
    val clearDefaultHint: String
)

sealed interface AudioDefaultStatus {
    data object AlreadyAuralis : AudioDefaultStatus
    data object NoDefaultLocked : AudioDefaultStatus
    data class LockedByOther(val packageName: String, val appLabel: String) : AudioDefaultStatus
}

sealed interface DefaultPickerLaunchResult {
    data object LaunchedSystemPicker : DefaultPickerLaunchResult
    data object AlreadyDefault : DefaultPickerLaunchResult
    data class BlockedByExistingDefault(val packageName: String, val appLabel: String) : DefaultPickerLaunchResult
    data object LaunchFailed : DefaultPickerLaunchResult
}

/**
 * Chinese-Android-aware helper for configuring Auralis as the system default audio player.
 *
 * Why we deliberately avoid Google's ACTION_MANAGE_DEFAULT_APPS_SETTINGS and
 * ACTION_APP_OPEN_BY_DEFAULT_SETTINGS:
 * - ACTION_APP_OPEN_BY_DEFAULT_SETTINGS only configures web App Links (http/https), NOT audio files.
 * - ACTION_MANAGE_DEFAULT_APPS_SETTINGS opens AOSP/Google's 6-role screen (Browser/Phone/SMS/Home/
 *   Assistant/Caller ID) which lacks a "Default Music" entry on Android 10+ and Chinese ROMs.
 *
 * Instead, this helper provides:
 * 1. Direct system audio "Open with -> Always" picker via a silent probe audio URI when no default
 *    player is locked (with live detection if another player currently holds the lock).
 * 2. Safe OEM-first jump into the Chinese ROM's real "应用 / 默认应用管理" hub (falling back cleanly
 *    to Settings.ACTION_APPLICATION_SETTINGS, never App Links or Google's stripped role screen).
 * 3. Direct App Info jump for clearing an existing default music player's lock.
 */
object DefaultAudioPlayerHelper {

    const val DEFAULT_PROBE_FILE_NAME = "auralis_default_audio_probe.wav"
    private const val PROBE_SUBDIR = "default_probe"

    fun isDefaultAudioProbeUri(uri: Uri?): Boolean {
        if (uri == null) return false
        return uri.lastPathSegment?.endsWith(DEFAULT_PROBE_FILE_NAME, ignoreCase = true) == true ||
            uri.toString().contains(DEFAULT_PROBE_FILE_NAME, ignoreCase = true)
    }

    fun detectDeviceRomGuide(
        manufacturer: String = Build.MANUFACTURER.orEmpty(),
        brand: String = Build.BRAND.orEmpty()
    ): DeviceRomGuide {
        val m = "$manufacturer $brand".lowercase()
        return when {
            m.contains("oppo") || m.contains("oneplus") || m.contains("realme") || m.contains("oplus") -> {
                DeviceRomGuide(
                    romDisplayName = "ColorOS / 一加 / 真我",
                    settingsPathHint = "系统设置 → 应用 → 默认应用 → 音乐（若无独立音乐项，可在系统「文件管理」点按任意音乐文件，选择 Auralis 并点选「始终」）",
                    clearDefaultHint = "若当前已绑定系统音乐或其他播放器，可先进入其「应用详情 → 默认打开 → 清除默认设置」，再回来唤起选择。"
                )
            }
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> {
                DeviceRomGuide(
                    romDisplayName = "澎湃OS / MIUI",
                    settingsPathHint = "系统设置 → 应用设置 → 应用管理 → 右上角「⋮」→ 其他设置 → 默认应用设置 → 音乐",
                    clearDefaultHint = "也可在「应用管理」中找到当前默认音乐应用，点击底部「清除默认操作」，再回来唤起音频打开方式。"
                )
            }
            m.contains("huawei") -> {
                DeviceRomGuide(
                    romDisplayName = "鸿蒙 / EMUI",
                    settingsPathHint = "系统设置 → 应用和服务 → 默认应用 → 音乐（或点击「更多默认应用」选择音频）",
                    clearDefaultHint = "若已默认绑定华为音乐或其他播放器，可前往其「应用信息 → 默认打开 → 清除默认设置」后再重新指派。"
                )
            }
            m.contains("honor") -> {
                DeviceRomGuide(
                    romDisplayName = "荣耀 MagicOS",
                    settingsPathHint = "系统设置 → 应用 → 默认应用 → 音乐（或点击「更多默认应用」配置音频）",
                    clearDefaultHint = "若当前由其他播放器接管，可进入其应用详情清除默认设置后，再唤起系统音频打开方式选择 Auralis。"
                )
            }
            m.contains("vivo") || m.contains("iqoo") -> {
                DeviceRomGuide(
                    romDisplayName = "OriginOS / FuntouchOS",
                    settingsPathHint = "系统设置 → 应用与权限 → 权限管理 → 默认应用设置 → 音乐",
                    clearDefaultHint = "若当前已有默认音乐应用，可进入其应用详情点击「默认打开 → 清除默认设置」后重新选择 Auralis。"
                )
            }
            m.contains("meizu") -> {
                DeviceRomGuide(
                    romDisplayName = "Flyme",
                    settingsPathHint = "系统设置 → 应用管理 → 默认应用 → 音乐",
                    clearDefaultHint = "若当前已绑定其他音乐应用，清除其默认打开方式后即可选择 Auralis。"
                )
            }
            m.contains("samsung") -> {
                DeviceRomGuide(
                    romDisplayName = "三星 One UI",
                    settingsPathHint = "系统设置 → 应用程序 → 找到当前音乐应用 → 设为默认 → 清除默认设置，随后在文件管理器点击音频选 Auralis「始终」",
                    clearDefaultHint = "清除原音乐应用的默认设置后，点击任意音频即可将 Auralis 设为始终打开。"
                )
            }
            else -> {
                DeviceRomGuide(
                    romDisplayName = "Android 系统",
                    settingsPathHint = "进入系统「应用管理」调整默认音乐应用，或在文件管理器中点击任意音频文件，选择 Auralis 并点选「始终」",
                    clearDefaultHint = "如果系统已自动用其他播放器打开音频，请先进入该播放器应用详情清除默认设置。"
                )
            }
        }
    }

    /**
     * Inspects which Activity currently handles content audio ACTION_VIEW intents.
     */
    fun inspectCurrentAudioDefault(context: Context): AudioDefaultStatus {
        val probeUri = ensureProbeContentUri(context) ?: return AudioDefaultStatus.NoDefaultLocked
        val probeIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(probeUri, "audio/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val pm = context.packageManager
        val resolved = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.resolveActivity(
                    probeIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.resolveActivity(probeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            }
        }.getOrNull() ?: return AudioDefaultStatus.NoDefaultLocked

        val pkg = resolved.activityInfo?.packageName.orEmpty()
        val cls = resolved.activityInfo?.name.orEmpty().lowercase()

        if (pkg.isBlank() ||
            pkg == "android" ||
            pkg == "com.android.intentresolver" ||
            cls.contains("resolver") ||
            cls.contains("chooser") ||
            cls.contains("disambiguation")
        ) {
            return AudioDefaultStatus.NoDefaultLocked
        }

        if (pkg == context.packageName) {
            return AudioDefaultStatus.AlreadyAuralis
        }

        val label = runCatching {
            val appInfo = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(appInfo).toString().trim()
        }.getOrNull().takeUnless { it.isNullOrBlank() } ?: pkg

        return AudioDefaultStatus.LockedByOther(packageName = pkg, appLabel = label)
    }

    /**
     * Triggers the native system "Open audio with... (Always / Just once)" picker when no default
     * music app is locked. Never silently launches a rival music player if one is already locked.
     */
    fun launchSystemAudioDefaultPicker(context: Context): DefaultPickerLaunchResult {
        when (val status = inspectCurrentAudioDefault(context)) {
            AudioDefaultStatus.AlreadyAuralis -> return DefaultPickerLaunchResult.AlreadyDefault
            is AudioDefaultStatus.LockedByOther -> {
                return DefaultPickerLaunchResult.BlockedByExistingDefault(
                    packageName = status.packageName,
                    appLabel = status.appLabel
                )
            }
            AudioDefaultStatus.NoDefaultLocked -> Unit
        }

        val probeUri = ensureProbeContentUri(context) ?: return DefaultPickerLaunchResult.LaunchFailed
        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(probeUri, "audio/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (runCatching { context.startActivity(openIntent) }.isSuccess) {
            DefaultPickerLaunchResult.LaunchedSystemPicker
        } else {
            DefaultPickerLaunchResult.LaunchFailed
        }
    }

    /**
     * Opens the Chinese OEM's real "应用 / 默认应用管理" hub (where OEMs place "默认音乐/音频"),
     * falling back cleanly to Settings.ACTION_APPLICATION_SETTINGS (the OEM's top-level Apps page)
     * instead of Google's stripped 6-role screen or web App Links.
     */
    fun openOemAppManagementSettings(context: Context): Boolean {
        val m = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
        val oemCandidates = buildList<Intent> {
            when {
                m.contains("oppo") || m.contains("oneplus") || m.contains("realme") || m.contains("oplus") -> {
                    add(
                        Intent().setComponent(
                            ComponentName(
                                "com.oplus.safecenter",
                                "com.oplus.safecenter.defaultapp.DefaultAppActivity"
                            )
                        )
                    )
                    add(
                        Intent().setComponent(
                            ComponentName(
                                "com.coloros.safecenter",
                                "com.coloros.safecenter.defaultapp.DefaultAppActivity"
                            )
                        )
                    )
                }
                m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> {
                    add(
                        Intent().setComponent(
                            ComponentName(
                                "com.android.settings",
                                "com.android.settings.applications.DefaultAppSettingsActivity"
                            )
                        )
                    )
                    add(
                        Intent().setComponent(
                            ComponentName(
                                "com.miui.securitycenter",
                                "com.miui.permcenter.permissions.DefaultAppActivity"
                            )
                        )
                    )
                }
                m.contains("huawei") || m.contains("honor") -> {
                    add(Intent("com.huawei.settings.action.DEFAULT_APP_SETTINGS"))
                    add(
                        Intent().setComponent(
                            ComponentName(
                                "com.android.settings",
                                "com.android.settings.Settings\$PreferredSettingsActivity"
                            )
                        )
                    )
                }
                m.contains("vivo") || m.contains("iqoo") -> {
                    add(
                        Intent().setComponent(
                            ComponentName(
                                "com.vivo.permissionmanager",
                                "com.vivo.permissionmanager.activity.DefaultAppActivity"
                            )
                        )
                    )
                    add(
                        Intent().setComponent(
                            ComponentName(
                                "com.iqoo.secure",
                                "com.iqoo.secure.ui.phoneoptimize.DefaultAppActivity"
                            )
                        )
                    )
                }
            }
            // Universal OEM-native Apps hub (on ColorOS/HyperOS/HarmonyOS/OriginOS this opens the
            // real OEM "应用" page with full 默认应用 / 应用管理, NOT Google's 6-role page or App Links)
            add(Intent(Settings.ACTION_APPLICATION_SETTINGS))
            add(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS))
            add(Intent(Settings.ACTION_SETTINGS))
        }

        for (intent in oemCandidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val resolved = runCatching {
                context.packageManager.resolveActivity(intent, 0)
            }.getOrNull()
            if (resolved != null && runCatching { context.startActivity(intent) }.isSuccess) {
                return true
            }
        }
        return false
    }

    /**
     * Opens the system App Info screen for Auralis or for a rival music player package
     * (so the user can tap "清除默认设置" cleanly).
     */
    fun openAppDetailsSettings(context: Context, packageName: String = context.packageName): Boolean {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (runCatching { context.startActivity(intent) }.isSuccess) {
            return true
        }
        return openOemAppManagementSettings(context)
    }

    private fun ensureProbeContentUri(context: Context): Uri? {
        return runCatching {
            val dir = File(context.cacheDir, PROBE_SUBDIR).apply { mkdirs() }
            val probeFile = File(dir, DEFAULT_PROBE_FILE_NAME)
            if (!probeFile.exists() || probeFile.length() < 44L) {
                probeFile.writeBytes(buildTinySilentWavBytes())
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                probeFile
            )
        }.getOrNull()
    }

    /**
     * Generates a valid 50ms 16-bit mono PCM silent WAV header + samples (~4444 bytes)
     * strictly for invoking Android's system audio MIME picker without touching user storage.
     */
    private fun buildTinySilentWavBytes(): ByteArray {
        val sampleRate = 8000
        val numChannels = 1
        val bitsPerSample = 16
        val numSamples = 400 // 50ms silence
        val dataSize = numSamples * numChannels * (bitsPerSample / 8)
        val totalSize = 36 + dataSize
        val byteRate = sampleRate * numChannels * (bitsPerSample / 8)
        val blockAlign = (numChannels * (bitsPerSample / 8)).toShort()

        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(totalSize)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16) // Subchunk1Size (PCM)
        buffer.putShort(1) // AudioFormat = PCM
        buffer.putShort(numChannels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign)
        buffer.putShort(bitsPerSample.toShort())
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataSize)
        // Remaining dataSize bytes are already 0 (digital silence)
        return buffer.array()
    }
}
