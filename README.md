# QQ Chat Copilot

这是一个基于 Spring Boot + JavaFX + Simbot 的桌面端聊天辅助系统。

## 功能特性
- **静默记录**: 后台自动记录所有群聊消息（最近99条）。
- **智能锁定**: 自动识别并锁定最近活跃的群聊，无需手动输入群号。
- **AI 分析**: 一键调用 AI (DeepSeek/OpenAI) 分析当前局势，生成高情商回复建议。
- **防卡死**: 异步处理 AI 请求，界面流畅。
- **成本控制**: 内置 API 频率限制和 Token 长度保护。

## 快速开始

1. **配置环境**
   - 确保本地已运行 OneBot V11 协议端（如 NapCatQQ），WebSocket 端口默认为 3001。
   - 修改 `src/main/resources/application.yml`，填入你的 AI API Key。

2. **运行**
   - 运行 `ChatCopilotApplication.main()` 启动程序。

## 注意事项
- **Simbot 依赖**: 项目使用了 Simbot 4.x。如果遇到 `BotManager` 相关的编译错误，请检查 Maven 依赖是否下载成功。
- **发送功能**: `MainController.java` 中的发送逻辑暂时被注释（为了防止编译错误），请在确认依赖正常后取消注释。

## 目录结构
- `service/GroupMemoryService`: 消息记忆与活跃群管理。
- `service/AiAnalysisService`: AI 接口调用与保护。
- `ui/MainController`: 界面逻辑与异步任务。
