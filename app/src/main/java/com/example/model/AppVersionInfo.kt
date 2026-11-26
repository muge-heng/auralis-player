package com.example.model

data class AppReleaseNote(
    val version: String,
    val tag: String,
    val title: String,
    val highlights: List<String>
)

data class PreReleaseMilestone(
    val phase: String,
    val summary: String
)

/**
 * Centralized in-app semantic version metadata.
 * Note: Android APK versionCode/versionName stays at default (1 / "1.0") to preserve seamless
 * upgrade compatibility on devices that already installed earlier builds, while user-facing
 * semantic versions are tracked cleanly here starting from v0.9.0.
 */
object AppVersionInfo {
    const val APP_DISPLAY_NAME = "Auralis"
    const val CURRENT_VERSION = "0.9.3"
    const val PREVIOUS_VERSION = "0.9.2"
    const val FIRST_PUBLIC_VERSION = "0.9.0"
    const val APK_COMPAT_LABEL = "安装包兼容版本 1.0 (Build 1)"

    const val PRE_RELEASE_NOTE =
        "v0.9.0 是第一个在 GitHub 公开维护的版本。在此之前的早期开发阶段未单独切分版本号，应用历经了以下核心演进路径："

    val PRE_RELEASE_MILESTONES: List<PreReleaseMilestone> = listOf(
        PreReleaseMilestone(
            phase = "声学底座与本地曲库",
            summary = "搭建 ExoPlayer 高解析音频引擎、本地无损/Hi-Res 扫描与元数据/内嵌歌词提取、曜石深色与云白浅色双主题及动态流光画布。"
        ),
        PreReleaseMilestone(
            phase = "交互动效与歌词舞台",
            summary = "引入跟手可打断的播放页上滑/下滑与封面共享形变动画、多源在线歌词聚合（网易云 / QQ 音乐 / 酷狗 / LRCLIB）、字重与档位字号调节、渐进景深模糊与沉浸歌词模式。"
        ),
        PreReleaseMilestone(
            phase = "调音台与 AI 乐评融合",
            summary = "将均衡器由 5 段升级为 10 段并支持保存自定义预设，构建多窗口独立 AI 对话引擎，支持思考过程展示、可调思考强度滑块与 AI 策展建单。"
        ),
        PreReleaseMilestone(
            phase = "曲库治理与品质决策",
            summary = "新增高级扫描筛选、普通音频独立分区、重复曲目智能检测与高品质版本冲突保留策略，最终收敛为 v0.9.0 公开维护基线。"
        )
    )

    val RELEASE_NOTES: List<AppReleaseNote> = listOf(
        AppReleaseNote(
            version = "0.9.3",
            tag = "当前版本",
            title = "顶部可划走轻提示 · 大幅精简通知频次 · 设置排布精修",
            highlights = listOf(
                "移除底部白底且无法划走的原生提示条，全面替换为顶部悬浮轻量提示卡片：深浅主题自适应、支持左右或上滑手势随时划走，彻底杜绝遮挡底栏、迷你播放器与 AI 输入区。",
                "大幅精简高频非必要提示：常规开关切换、均衡器预设点击、队列调序、红心收藏、歌单编辑与弹窗确认删除等即时可见操作均静默配合触觉反馈，仅在异步导入/去重、歌词刻录导出或异常提醒时出现轻提示。",
                "优化「防误删保护机制」为全宽上下选项行，修复标题异常折断与双卡高低不齐问题，并同步对齐「底部四段导航栏样式」双卡等高与单行排版。"
            )
        ),
        AppReleaseNote(
            version = "0.9.2",
            tag = "历史版本",
            title = "歌词刻录与导出 · 双模式防误删保护机制",
            highlights = listOf(
                "歌曲列表「更多」菜单新增「刻录歌词到音频文件」，支持将已获取的歌词直接写入 MP3 (ID3v2 USLT)、FLAC (Vorbis LYRICS)、M4A/MP4 (©lyr) 原音频文件标签中补齐元数据，无损保留音质与封面。",
                "歌曲列表「更多」菜单新增「导出歌词文件」，支持按原始格式（.lrc / .qrc / .yrc / .krc / .ttml / .srt / .txt）导出到本地任意目录。",
                "全应用删除操作（自定义 EQ 预设、AI 对话窗口、歌单、曲目、播放记录等）统一接入防误删保护，并可在「工坊」自由切换「删除前二次确认弹窗」或「置顶可撤销提示条（支持一键撤销与手势划掉）」。",
                "完善版本演进日志，补充 v0.9.0 公开维护基线之前的早期迭代脉络说明。"
            )
        ),
        AppReleaseNote(
            version = "0.9.1",
            tag = "历史版本",
            title = "歌词工具栏精修 · 歌单封面自定义 · 版本记录体系",
            highlights = listOf(
                "修复逐行歌词开启模拟逐字后，顶部格式标签过宽导致「匹配/源」按钮被挤压换行的问题，改为紧凑防折行工具栏。",
                "在自建歌单、歌手自动歌单与智能合集右上角「更多」菜单中新增「设置歌单封面」，支持自动四宫格、指定单曲封面、自选四首拼贴及随机四首并可随时换一批。",
                "建立应用内语义化版本维护与更新日志面板，保留默认 1.0 安装包兼容版本号以确保平滑覆盖安装。"
            )
        ),
        AppReleaseNote(
            version = "0.9.0",
            tag = "首个公开维护版本",
            title = "DeepSeek 深度搜索 · Markdown 表格与外链 · 歌手自动歌单",
            highlights = listOf(
                "首个在 GitHub 公开维护的里程碑版本：适配 DeepSeek 原生边思考边搜索与 384K 默认输出上限，支持持久记忆附带曲库清单与按曲目附带歌词。",
                "升级 AI Markdown 渲染，支持表格、全矢量图标搜索轨迹卡片、点击跳转系统浏览器的参考来源链接以及统一的 AI 策展建单卡片。",
                "歌单页新增「歌手自动歌单」与大封面详情头图，支持歌单名与简介编辑。"
            )
        )
    )
}
