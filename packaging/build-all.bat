@echo off
chcp 65001 >nul
title AI Chat Platform - 一键构建工具

echo ===========================================
echo    AI Chat Platform 安装包构建工具
echo ===========================================
echo.

cd /d "%~dp0.."

echo [1/3] 构建后端 JAR...
if exist "target\ai-chat-platform-1.0.0.jar" (
    echo   后端 JAR 已存在，跳过
) else (
    call mvn clean package -DskipTests -q
    if %errorlevel% neq 0 (
        echo   ❌ 后端构建失败
        pause
        exit /b 1
    )
    echo   ✅ 后端构建成功
)

echo.
echo [2/3] 构建桌面安装包...
cd packaging\desktop
if exist "node_modules" (
    echo   依赖已安装
) else (
    echo   正在安装 Electron 依赖...
    call npm install --silent
)
call npm run build:win
if %errorlevel% equ 0 (
    echo   ✅ 桌面安装包构建成功
)

echo.
echo [3/3] Android APK 构建说明
echo.
echo   Android APK 需要使用 Android Studio 构建：
echo   1. 用 Android Studio 打开 packaging\android 目录
echo   2. 点击 Build ^> Build APK
echo.
echo 也可以使用命令行（需要配置 ANDROID_HOME）：
echo   cd packaging/android
echo   gradlew assembleDebug
echo.

echo ===========================================
echo  全部构建完成！
echo ===========================================
echo.
echo  后端 JAR:      target\ai-chat-platform-1.0.0.jar
echo  桌面安装包:    packaging\desktop\dist\
echo  Android APK:   packaging\android\app\build\outputs\apk\
echo.
pause
