@echo off
chcp 65001 >nul
echo === Building AI Chat Desktop App ===
echo.

cd /d "%~dp0.."

REM Step 1: Build backend JAR if needed
echo [1/4] Building Spring Boot backend...
if not exist "..\..\target\ai-chat-platform-1.0.0.jar" (
  cd ..\..
  call mvn clean package -DskipTests -q
  if %errorlevel% neq 0 (
    echo ERROR: Backend build failed
    pause
    exit /b 1
  )
  cd packaging\desktop
  echo   Backend JAR built successfully
) else (
  echo   Backend JAR already exists
)

REM Step 2: Generate icons
echo [2/4] Generating app icons...
if exist "scripts\generate-icons.py" (
  python scripts\generate-icons.py
)

REM Step 3: Install npm dependencies
echo [3/4] Installing Electron dependencies...
call npm install
if %errorlevel% neq 0 (
  echo ERROR: npm install failed
  pause
  exit /b 1
)

REM Step 4: Build installer
echo [4/4] Building Windows installer...
call npm run build:win

echo.
echo === Build Complete! ===
echo Installer is in: dist\
pause
