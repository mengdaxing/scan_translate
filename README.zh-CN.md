# scan_translate · 字幕宝

[English](README.md) · [简体中文](README.zh-CN.md)

字幕宝是一个开源 Android 悬浮字幕翻译工具，方便在看不同语言的剧集时学习外语。把扫描框放在视频字幕上方，应用会自动识别画面文字，并把译文显示在扫描框外部上方。

## 功能

- 悬浮球可以显示在其他应用顶部。启动时为未激活的 100% 不透明状态；点击后进入激活状态并变为 50% 不透明，显示选框并启动 OCR 和翻译；再次点击会隐藏选框并停止 OCR 和翻译。
- 扫描框支持拖动移动和拖动右下角调整大小，并记住上一次的位置和尺寸。
- 只有识别文字发生变化时才会请求翻译，避免重复翻译同一行字幕。
- 默认使用设备上的 ML Kit 本地 OCR 和本地翻译模型，也可以选择 DeepL API。
- 可配置源语言、目标语言、文字大小、文字颜色、OCR 间隔和译文显示时长。

## 开始使用

1. 使用 Android Studio Hedgehog 或更高版本打开项目，等待 Gradle 同步。
2. 用 Android 8.0（API 26）或更高版本的设备运行。
3. 在设置页允许“在其他应用上层显示”，并允许屏幕捕获。
4. 点击悬浮球激活功能，拖动选框覆盖字幕；再次点击悬浮球即可停止扫描。
5. 第一次使用本地翻译语言组合时，系统会下载对应的离线模型，建议在 Wi-Fi 下完成下载。

DeepL 使用 `https://api-free.deepl.com/v2/translate`。API Key 只保存在本机的 SharedPreferences 中，不会提交到本项目。

## 架构

- `MainActivity`：配置页、权限和 MediaProjection 授权。
- `OverlayService`：前台服务、悬浮球、扫描框、屏幕帧、OCR、翻译和译文显示。
- `ScanBoxView`：扫描框的移动、缩放和位置保存交互。
- `AppPrefs`：所有用户设置和扫描框几何信息的持久化。

屏幕采集通过 Android MediaProjection 完成，OCR 和本地翻译在设备端执行。应用不会保存完整屏幕截图，只在内存中保留当前帧用于裁剪扫描区域。

## 构建

本项目使用 AGP 8.5.2、Kotlin 2.0.21 和 JDK 17。用 Android Studio 打开项目并等待 Gradle 同步后运行 `app` 配置；如果本机已安装 Gradle，也可以执行：

```bash
gradle assembleDebug
```

准备此仓库的开发容器没有预装 JDK 和 Android SDK，因此未执行 Gradle 构建；在本地 Android Studio 中即可同步、构建和调试。

## 持续集成

每次 push 都会触发 `Build APK` GitHub Actions 工作流。它使用 JDK 17 和 Gradle 8.7 构建 debug APK，并上传名为 `scan_translate-debug-<commit-sha>` 的 artifact。推送到 `main` 时还会在仓库的 **Releases** 页面创建包含 APK 的预发布版本；其他分支的构建可在对应运行记录的 **Artifacts** 区域下载。

## 开源协议

MIT License，欢迎提交 Issue 和 Pull Request。
