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
    val settingsPathHint: String
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
 * Deliberately avoided intents (all wrong on Chinese Android ROMs):
 * - ACTION_APP_OPEN_BY_DEFAULT_SETTINGS: only configures web App Links (http/https), NOT audio.
 * - ACTION_MANAGE_DEFAULT_APPS_SETTINGS: Google's 6-role screen, lacks a "Default Music" entry.
 * - ACTION_APPLICATION_SETTINGS / ACTION_MANAGE_APPLICATIONS_SETTINGS: opens "应用管理"
 *   (the installed-apps list), which is a SIBLING of "默认应用", one level too deep!
 * - ACTION_APPLICATION_DETAILS_SETTINGS on rival music apps: blocked or ineffective on Chinese ROMs.
 *
 * Instead, this helper provides:
 * 1. Direct system audio "Open with -> Always" picker via a silent probe audio URI when no default
 *    player is locked.
 * 2. Safe jump directly into the OEM's Default Apps page or the parent "应用" hub (where "应用管理"
 *    and "默认应用" appear side-by-side), falling back cleanly to top-level Settings.ACTION_SETTINGS.
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
                    settingsPathHint = "系统设置 → 应用 → 默认应用 → 音乐（注意：「默认应用」与「应用管理」为同级菜单，位于「应用」页面内）"
                )
            }
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> {
                DeviceRomGuide(
                    romDisplayName = "澎湃OS / MIUI",
                    settingsPathHint = "系统设置 → 应用设置 → 默认应用设置 → 音乐（部分版本在「应用管理」右上角「⋮」→ 其他设置 → 默认应用设置）"
                )
            }
            m.contains("huawei") -> {
                DeviceRomGuide(
                    romDisplayName = "鸿蒙 / EMUI",
                    settingsPathHint = "系统设置 → 应用和服务 → 默认应用 → 音乐（或点击「更多默认应用」选择音频）"
                )
            }
            m.contains("honor") -> {
                DeviceRomGuide(
                    romDisplayName = "荣耀 MagicOS",
                    settingsPathHint = "系统设置 → 应用 → 默认应用 → 音乐（或点击「更多默认应用」配置音频）"
                )
            }
            m.contains("vivo") || m.contains("iqoo") -> {
                DeviceRomGuide(
                    romDisplayName = "OriginOS / FuntouchOS",
                    settingsPathHint = "系统设置 → 应用与权限 → 默认应用设置 → 音乐（或权限管理 → 默认应用设置）"
                )
            }
            m.contains("meizu") -> {
                DeviceRomGuide(
                    romDisplayName = "Flyme",
                    settingsPathHint = "系统设置 → 应用 → 默认应用 → 音乐"
                )
            }
            m.contains("samsung") -> {
                DeviceRomGuide(
                    romDisplayName = "三星 One UI",
                    settingsPathHint = "系统设置 → 应用程序 → 选择默认应用程序（或在「文件管理」点选任意音频后选 Auralis「始终」）"
                )
            }
            else -> {
                DeviceRomGuide(
                    romDisplayName = "Android 系统",
                    settingsPathHint = "系统设置 → 应用 → 默认应用 → 音乐（或在系统文件管理器点击任意音频文件，选择 Auralis 并点选「始终」）"
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
     * music app is locked. Never launches a rival music player or rival app-details screen.
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
     * Opens the OEM's real "默认应用" screen or the parent "应用" dashboard (the level ABOVE both
     * "应用管理" and "默认应用"), falling back cleanly to top-level Settings.ACTION_SETTINGS.
     *
     * Never calls ACTION_APPLICATION_SETTINGS or ACTION_MANAGE_APPLICATIONS_SETTINGS because those
     * jump inside the sibling "应用管理" list where "默认应用" is not visible.
     */
    fun openOemAppsHubOrSystemSettings(context: Context): Boolean {
        val m = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
        val candidates = buildList<Intent> {
            // 1. Vendor-specific direct "默认应用" activities (only launched if exported & resolvable)
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

            // 2. Parent "应用" (Apps) hub — sits ABOVE sibling "应用管理" and "默认应用" menus
            add(
                Intent().setComponent(
                    ComponentName(
                        "com.android.settings",
                        "com.android.settings.Settings\$AppsDashboardActivity"
                    )
                )
            )
            add(
                Intent().setComponent(
                    ComponentName(
                        "com.android.settings",
                        "com.android.settings.Settings\$AppAndNotificationDashboardActivity"
                    )
                )
            )

            // 3. Guaranteed clean fallback: top-level System Settings (so user taps 应用 → 默认应用)
            add(Intent(Settings.ACTION_SETTINGS))
        }

        val pm = context.packageManager
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val resolved = runCatching { pm.resolveActivity(intent, 0) }.getOrNull() ?: continue
            val isExported = resolved.activityInfo?.exported ?: true
            if (!isExported) continue
            if (runCatching { context.startActivity(intent) }.isSuccess) {
                return true
            }
        }
        return false
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
        return buffer.array()
    }
}
