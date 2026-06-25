# AI Chat Platform 🤖

基于 Spring Boot 的智能对话平台，支持多轮对话、文档生成、文件互转、图片分析等功能。

## ✨ 功能特性

- 💬 **AI 智能对话** - 支持流式输出，实时呈现回复内容
- 📝 **文档生成** - 一键生成 Word、PPT、PDF、TXT 文档
- 🔄 **文件互转** - 支持 PDF/Word/PPT/TXT 格式互转
- 🖼️ **图片分析** - 上传图片让 AI 分析内容
- 📎 **文件上传** - 拖拽上传，支持多种格式
- 👥 **多对话管理** - 创建和管理多个独立对话
- 🎨 **响应式设计** - 完美适配桌面和移动端

## 🚀 快速启动

```bash
# 方式一：直接运行 JAR
java -jar target/ai-chat-platform-1.0.0.jar

# 方式二：Maven 构建并运行
mvn spring-boot:run
```

启动后访问: **http://localhost:8080**

## 📦 安装包下载

本项目支持三种使用方式:

| 方式 | 说明 | 构建方法 |
|------|------|----------|
| 🌐 **Web 版** | 浏览器直接访问 | 无需构建，启动后端即可 |
| 🖥️ **桌面版** | Windows 原生应用 | `packaging\desktop\scripts\build.bat` |
| 📱 **Android** | 手机 APP | 用 Android Studio 打开 `packaging/android` |

详见 [packaging/README.md](packaging/README.md)

## 🔧 技术栈

- **后端**: Spring Boot 3.2.4 + JPA + H2
- **前端**: Thymeleaf + vanilla JavaScript
- **桌面**: Electron (可选)
- **移动端**: Android WebView (可选)
- **AI**: 支持 OpenAI 兼容接口 (DeepSeek / OpenAI / 通义千问 等)

## ⚙️ 配置

编辑 `src/main/resources/application.yml`:

```yaml
# AI API 配置
ai:
  api:
    url: https://api.deepseek.com/chat/completions
    key: your-api-key
    model: deepseek-chat

# 服务端口
server:
  port: 8080
```

## 📁 项目结构

```
├── src/main/java/com/chat/
│   ├── ChatApplication.java        # 启动入口
│   ├── controller/                 # 控制器
│   │   ├── PageController.java      # 页面路由
│   │   ├── AuthController.java      # 登录注册
│   │   ├── ChatController.java      # 聊天 API
│   │   └── FileController.java      # 文件上传/转换
│   ├── service/                     # 业务逻辑
│   │   ├── AIService.java           # AI 对话服务
│   │   ├── ChatService.java         # 聊天管理
│   │   ├── FileService.java         # 文件处理
│   │   ├── DocumentGeneratorService.java  # 文档生成
│   │   ├── UserService.java         # 用户管理
│   │   └── WebSearchService.java    # 联网搜索
│   ├── model/                       # 实体
│   ├── repository/                  # 数据访问
│   ├── dto/                         # 数据传输对象
│   └── config/                      # 配置
├── src/main/resources/
│   ├── templates/                   # 页面模板
│   ├── static/css/style.css        # 样式
│   ├── static/js/chat.js           # 聊天前端逻辑
│   └── application.yml             # 应用配置
├── packaging/                       # 安装包构建
│   ├── desktop/                     # Electron 桌面版
│   ├── android/                     # Android APP
│   └── README.md                    # 构建说明
└── data/                            # H2 数据库文件
```
