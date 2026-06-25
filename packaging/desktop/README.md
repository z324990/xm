# AI Chat - 桌面版

基于 Electron 构建的 AI Chat Platform 桌面客户端。

## 功能特点

- 🪟 **原生窗口** - 独立桌面应用，无浏览器标签栏干扰
- 🔔 **系统托盘** - 最小化到托盘，后台运行
- ⚡ **自动启动后端** - 一键启动，自动管理 Java 服务进程
- 📦 **独立安装包** - 生成 Windows .exe / macOS .dmg / Linux .AppImage
- 🔒 **单实例锁** - 防止重复启动

## 系统要求

- **Windows 10+** / macOS 11+ / Linux (x64)
- **Java 17+** (或使用捆绑的 JRE)
- **Node.js 18+** (仅构建时需要)

## 目录结构

```
packaging/desktop/
├── main.js              # Electron 主进程
├── preload.js           # 预加载脚本 (安全 IPC)
├── package.json         # 依赖与构建配置
├── assets/              # 应用图标
│   ├── icon.svg         # 图标源文件
│   ├── icon.png         # 256x256 PNG
│   └── icon.ico         # Windows 图标
├── scripts/
│   ├── build.sh         # 一键构建脚本
│   ├── build.bat        # Windows 构建脚本
│   └── generate-icons.py # 图标生成脚本
└── README.md
```

## 开发

```bash
# 1. 确保后端正在运行 (端口 8080)
# 2. 启动 Electron 开发模式
npm start -- --dev
```

## 构建安装包

### Windows (.exe 安装包)

```bash
# 方式一：一键构建 (推荐)
scripts\build.bat

# 方式二：分步构建
cd packaging\desktop
npm install
npm run build:win
```

输出路径: `packaging/desktop/dist/AI Chat-Setup-1.0.0-win-x64.exe`

### macOS (.dmg)

```bash
cd packaging/desktop
npm install
npm run build:mac
```

### Linux (.AppImage)

```bash
cd packaging/desktop
npm install
npm run build:linux
```

## 配置说明

Electron 应用启动时会自动：

1. 查找系统 Java (JAVA_HOME / PATH)
2. 启动 Spring Boot 后端 JAR
3. 等待后端就绪后打开主窗口
4. 窗口关闭时最小化到系统托盘

数据目录 (生产模式):
- Windows: `%APPDATA%/AI Chat/data/`
- macOS: `~/Library/Application Support/AI Chat/data/`
- Linux: `~/.config/AI Chat/data/`
