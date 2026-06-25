#!/bin/bash
# ==========================================
# AI Chat Platform - 一键构建所有安装包
# ==========================================
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo "╔══════════════════════════════════════════╗"
echo "║   AI Chat Platform 安装包构建工具       ║"
echo "╚══════════════════════════════════════════╝"
echo ""

# Check prerequisites
echo "🔍 检查构建环境..."

check_command() {
    if command -v "$1" &> /dev/null; then
        echo "  ✅ $1: $($1 --version 2>&1 | head -1)"
        return 0
    else
        echo "  ❌ $1: 未找到"
        return 1
    fi
}

HAS_JAVA=$(check_command "java")
HAS_NODE=$(check_command "node")
HAS_NPM=$(check_command "npm")

echo ""

# Step 1: Build backend JAR
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📦 [1/3] 构建后端 Spring Boot 应用"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

cd "$PROJECT_DIR"
if [ -f "target/ai-chat-platform-1.0.0.jar" ]; then
    echo "  后端 JAR 已存在，跳过构建"
else
    if [ "$HAS_JAVA" -eq 0 ]; then
        echo "  正在使用 Maven 构建..."
        mvn clean package -DskipTests -q
        echo "  ✅ 后端 JAR 构建成功"
    else
        echo "  ⚠️ Java 未安装，跳过后端构建"
    fi
fi

# Step 2: Build desktop app
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🖥️  [2/3] 构建桌面版 (Electron)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

cd "$PROJECT_DIR/packaging/desktop"

if [ "$HAS_NODE" -eq 0 ] && [ "$HAS_NPM" -eq 0 ]; then
    echo "  安装依赖..."
    npm install --silent 2>&1 | tail -1

    echo "  生成图标..."
    if [ -f "scripts/generate-icons.py" ]; then
        python3 scripts/generate-icons.py 2>/dev/null || true
    fi

    echo "  构建 Windows 安装包..."
    npm run build:win 2>&1 | tail -5

    echo "  ✅ 桌面版构建完成"
else
    echo "  ⚠️ Node.js 未安装，跳过桌面版构建"
fi

# Step 3: Build Android app (if gradle wrapper exists and ANDROID_HOME is set)
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📱 [3/3] 构建 Android APK (可选)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

cd "$PROJECT_DIR/packaging/android"

if [ -f "gradlew" ] && [ -n "${ANDROID_HOME:-}" ]; then
    echo "  正在构建 Debug APK..."
    chmod +x gradlew 2>/dev/null || true
    bash gradlew assembleDebug 2>&1 | tail -5

    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
    if [ -f "$APK_PATH" ]; then
        echo "  ✅ Android APK 构建成功: $APK_PATH"
    fi
else
    echo "  ⚠️ Android SDK 未配置，跳过 APK 构建"
    echo "  提示: 使用 Android Studio 打开 packaging/android 目录构建"
fi

echo ""
echo "╔══════════════════════════════════════════╗"
echo "║         全部构建完成！                    ║"
echo "╚══════════════════════════════════════════╝"
echo ""
echo "📦 构建产出:"
echo "  • 后端 JAR:      $PROJECT_DIR/target/ai-chat-platform-1.0.0.jar"
echo "  • 桌面安装包:    $PROJECT_DIR/packaging/desktop/dist/"
echo "  • Android APK:   $PROJECT_DIR/packaging/android/app/build/outputs/apk/"
echo ""
echo "💡 提示: 也可单独构建各平台"
echo "  • 桌面版:  cd packaging/desktop && npm run build:win"
echo "  • Android: cd packaging/android && bash gradlew assembleDebug"
