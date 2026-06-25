# AI Chat - Android App

AI Chat Platform 的 Android 客户端，基于 WebView 构建。

## 功能特点

- 📱 **原生 Android 应用** - 沉浸式全屏体验，无浏览器地址栏
- 🔄 **下拉刷新** - 支持手势刷新页面
- 📎 **文件上传** - 支持拍照、相册选图、文件选择
- ⬇️ **下载管理** - 文件下载自动使用系统下载管理器
- 🌐 **网络监测** - 断网自动提示
- 🎨 **主题适配** - 支持深色模式
- ⚙️ **自定义服务器地址** - 可配置连接任意服务器

## 系统要求

- Android 8.0+ (API 26+)
- Java 17+ (构建时需要)

## 构建 APK

### 方式一：Android Studio (推荐)

1. 用 Android Studio 打开 `packaging/android` 目录
2. 等待 Gradle 同步完成
3. 点击 `Build > Build Bundle(s) / APK(s) > Build APK(s)`

### 方式二：命令行构建

```bash
# 需要有 ANDROID_HOME 环境变量
cd packaging/android

# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release APK (需要签名)
./gradlew assembleRelease
```

APK 输出路径: `app/build/outputs/apk/debug/app-debug.apk`

## 自定义服务器配置

### 方法 1: 修改 build.gradle.kts（构建前）

编辑 `app/build.gradle.kts`:
```kotlin
buildConfigField("String", "SERVER_URL", "\"http://你的服务器IP:8080\"")
```

### 方法 2: 应用内设置（运行时）

如果连接失败，应用会弹出服务器地址设置对话框，
输入你的服务器地址即可。

## 开发说明

- 模拟器默认连接 `http://10.0.2.2:8080` (映射到宿主机 localhost)
- 真机测试需要连接同一 Wi-Fi 下的服务器 IP
- 默认允许 HTTP 明文流量（开发用），生产环境应启用 HTTPS

## 目录结构

```
packaging/android/
├── app/
│   ├── build.gradle.kts        # App 构建配置
│   ├── proguard-rules.pro      # 混淆规则
│   └── src/main/
│       ├── AndroidManifest.xml  # 应用清单
│       ├── java/com/chat/app/
│       │   ├── MainActivity.kt      # 主 Activity (WebView)
│       │   └── WebAppInterface.kt   # JS 接口桥接
│       └── res/
│           ├── layout/          # 布局文件
│           ├── values/          # 字符串、颜色、主题
│           ├── values-night/    # 深色模式主题
│           ├── drawable/        # 图标向量图
│           ├── mipmap-*/        # 启动图标
│           └── xml/             # 网络安全配置
├── build.gradle.kts             # 顶层构建文件
├── settings.gradle.kts          # 项目设置
├── gradle.properties            # Gradle 配置
└── gradle/wrapper/              # Gradle Wrapper
```
