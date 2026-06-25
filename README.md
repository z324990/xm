# AI Chat Platform 🤖

基于 Spring Boot 的智能对话平台，支持多轮对话、文档生成、文件互转、图片分析等功能。

**🌐 线上体验地址:** [https://ai-chat-platform.onrender.com](https://ai-chat-platform.onrender.com) （如果已部署）

## ✨ 功能特性

- 💬 **AI 智能对话** - 支持流式输出，实时呈现回复内容
- 📝 **文档生成** - 一键生成 Word、PPT、PDF、TXT 文档
- 🔄 **文件互转** - 支持 PDF/Word/PPT/TXT 格式互转
- 🖼️ **图片分析** - 上传图片让 AI 分析内容
- 📎 **文件上传** - 拖拽上传，支持多种格式
- 👥 **多对话管理** - 创建和管理多个独立对话
- 🎨 **响应式设计** - 完美适配桌面和移动端

---

## 🚀 一键部署到云端（免费，24小时在线）

### 方式 1：Render（推荐，最简单）

[![Deploy to Render](https://render.com/images/deploy-to-render-button.svg)](https://render.com/deploy)

**步骤：**
1. 点击上方按钮
2. 用 GitHub 账号登录
3. 连接仓库 → 填写 AI API Key → 点击 **Deploy**
4. 等待 3 分钟部署完成 ✅

**部署后：** Render 会给你一个 `https://xxx.onrender.com` 的域名，手机随时随地访问。

### 方式 2：Railway

[![Deploy on Railway](https://railway.app/button.svg)](https://railway.app/new/template)

**步骤：**
1. 点击上方按钮
2. 用 GitHub 登录 → **Deploy**
3. 在 Dashboard 中设置环境变量 `AI_API_KEY`
4. 部署完成自动生成域名

### 方式 3：手动部署到阿里云/腾讯云

```bash
# 1. 在服务器上安装 Docker
# 2. 构建并运行
docker build -t ai-chat-platform .
docker run -d -p 8080:8080 \
  -e AI_API_KEY=your-key \
  -v /data/ai-chat:/app/data \
  ai-chat-platform
```

---

## 💻 本地开发

```bash
# 方式一：直接运行 JAR
java -jar target/ai-chat-platform-1.0.0.jar

# 方式二：Maven 构建并运行
mvn spring-boot:run
```

启动后访问: **http://localhost:8080**

## 📦 安装包下载（客户端）

本项目支持多种客户端方式：

| 方式 | 说明 | 构建方法 |
|------|------|----------|
| 🌐 **PWA 网页版** | 手机浏览器打开网址 → 添加到主屏幕 | 无需构建 |
| 🖥️ **桌面版** | Windows 原生应用 (Electron) | `packaging\desktop\scripts\build.bat` |
| 📱 **Android APP** | Android WebView 原生壳 | Android Studio 打开 `packaging/android` |

详见 [packaging/README.md](packaging/README.md)

## ⚙️ 配置 AI API Key

| 环境变量 | 说明 | 示例 |
|----------|------|------|
| `AI_API_KEY` | API Key（**必填**） | `sk-your-key-here` |
| `AI_API_URL` | API 地址 | `https://api.deepseek.com/chat/completions` |
| `AI_MODEL` | 模型名称 | `deepseek-chat` |

支持任何 OpenAI 兼容接口：DeepSeek、通义千问、SiliconFlow 等。

## 📁 项目结构

```
├── src/main/java/com/chat/
│   ├── ChatApplication.java        # 启动入口
│   ├── controller/                 # 控制器
│   ├── service/                    # 业务逻辑
│   ├── model/                      # 实体
│   └── repository/                 # 数据访问
├── src/main/resources/
│   ├── templates/                  # 页面模板
│   ├── static/
│   │   ├── css/style.css          # 样式
│   │   ├── js/chat.js             # 前端逻辑
│   │   ├── manifest.json          # PWA 配置
│   │   └── sw.js                  # Service Worker
│   └── application.yml            # 应用配置
├── packaging/                      # 安装包构建
│   ├── desktop/                    # Electron 桌面版
│   └── android/                    # Android APP
├── Dockerfile                      # 容器化部署
├── render.yaml                     # Render 部署配置
└── Procfile                        # Render/Heroku 启动
```
