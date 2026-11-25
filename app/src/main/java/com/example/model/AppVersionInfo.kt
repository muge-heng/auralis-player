package com.example.model

data class AppReleaseNote(
    val version: String,
    val tag: String,
    val title: String,
    val highlights: List<String>
)

/**
 * Centralized in-app semantic version metadata.
 * Note: Android APK versionCode/versionName stays at default (1 / "1.0") to preserve seamless
 * upgrade compatibility on devices that already installed earlier builds, while user-facing
 * semantic versions are tracked cleanly here starting from 0.9.0.
 */
object AppVersionInfo {
    const val APP_DISPLAY_NAME = "Auralis"
    const val CURRENT_VERSION = "0.9.1"
    const val PREVIOUS_VERSION = "0.9.0"
    const val APK_COMPAT_LABEL = "安装包兼容版本 1.0 (Build 1)"

    val RELEASE_NOTES: List<AppReleaseNote> = listOf(
        AppReleaseNote(
            version = "0.9.1",
            tag = "当前版本",
            title = "歌词工具栏精修 · 歌单封面自定义 · 版本记录体系",
            highlights = listOf(
                "修复逐行歌词开启模拟逐字后，顶部格式标签过宽导致「匹配/源」按钮被挤压换行的问题，改为紧凑防折行工具栏。",
                "在自建歌单、歌手自动歌单与智能合集右上角「更多」菜单中新增「设置歌单封面」，支持自动四宫格、指定单曲封面、自选四首拼贴及随机四首并可随时换一批。",
                "建立应用内语义化版本维护与更新日志面板（保留默认 1.0 安装包版本以保障平滑覆盖安装，以上个版本为 0.9.0 基线演进至 0.9.1）。"
            )
        ),
        AppReleaseNote(
            version = "0.9.0",
            tag = "基线版本",
            title = "DeepSeek 深度搜索 · Markdown 表格与外链 · 歌手自动歌单",
            highlights = listOf(
                "适配 DeepSeek 原生边思考边搜索与 384K 默认输出上限，支持持久记忆附带曲库清单与按曲目附带歌词。",
                "升级 AI Markdown 渲染，支持表格、全矢量图标搜索轨迹卡片、点击跳转系统浏览器的参考来源链接以及统一的 AI 策展建单卡片。",
                "歌单页新增「歌手自动歌单」与大封面详情头图，支持重复音频智能检测与高品质版本冲突决策。",
                "支持 10 段均衡器与自定义预设保存、Apple Music 风格渐进景深模糊与沉浸歌词模式。"
            )
        )
    )
}
