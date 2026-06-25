# AI Chat Platform - 安装包构建

本项目支持构建 **桌面版** 和 **Android 手机版** 两种安装包。

## 构建环境要求

| 环境 | 桌面版 | Android |
|------|--------|---------|
| Java 17+ | ✅ 必需 (后端编译) | ✅ 必需 |
| Node.js 18+ | ✅ 必需 (Electron) | ❌ 不需要 |
| npm | ✅ 必需 | ❌ 不需要 |
| Android Studio | ❌ 不需要 | ✅ 推荐 |
| ANDROID_HOME | ❌ 不需要 | ✅ 可选 |

## 快速构建

### 一键构建 (推荐)

```bash
# Windows
packaging\build-all.bat

# macOS / Linux
bash packaging/build-all.sh
```

### 单独构建桌面版

```bash
# 1. 先构建后端 JAR
cd D:\ai-chat-platform
mvn clean package -DskipTests

# 2. 构建桌面安装包
cd packaging\desktop
npm install
npm run build:win        # Windows .exe
npm run build:mac        # macOS .dmg
npm run build:linux      # Linux .AppImage
```

### 单独构建 Android APK

```bash
# 命令行 (需要 ANDROID_HOME)
cd packaging\android
gradlew assembleDebug

# 或使用 Android Studio
# 1. 打开 packaging\android 目录
# 2. Build > Build APK
```

## 构建产出

构建完成后，安装包位于:

```
packaging/
├── desktop/dist/
│   └── AI Chat-Setup-1.0.0-win-x64.exe   ← Windows 安装包 (~60MB)
└── android/app/build/outputs/apk/
    └── debug/app-debug.apk               ← Android APK (~5MB)
```

## 启动说明

### 桌面版
安装后双击运行即可。应用会自动启动 Java 后端服务 (端口 8080)。

如果系统没有 Java 17+，请先安装:
- https://adoptium.net/temurin/releases/?version=17

### Android 版
安装 APK 后，需要配置服务器地址:
- 模拟器: 默认连接 `http://10.0.2.2:8080`
- 真机: 输入服务器局域网 IP，例如 `http://192.168.1.100:8080`

## 架构说明

```
┌─────────────────────────────────────────────────┐
│                  用户访问层                       │
├─────────────┬─────────────────┬─────────────────┤
│  桌面版      │    Web 浏览器    │   Android App   │
│  (Electron)  │   (Chrome/Edge) │   (WebView)     │
└──────┬──────┴────────┬────────┴───────┬─────────┘
       │              │                │
       └──────────────┼────────────────┘
                      │ http://localhost:8080
              ┌───────┴────────┐
              │  Spring Boot   │
              │   Backend JAR  │
              │   (端口 8080)   │
              └───────┬────────┘
                      │
              ┌───────┴────────┐
              │   H2 Database  │
              │   (文件存储)    │
              └────────────────┘
```
