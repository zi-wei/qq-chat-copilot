# QQ Chat Copilot/bot

这是一个基于 Spring Boot + JavaFX + Simbot 的桌面端聊天辅助系统。
（或者整蛊某人时候进行的聊天辅助系统）

## 功能特性
- **静默记录**: 后台自动记录所有群聊消息和好友消息（最近99条）。
- **AI 分析**: 一键调用 AI  分析当前局势，生成回复建议。
- **防卡死**: 异步处理 AI 请求，界面流畅。
- **成本控制**: 内置 API 频率限制和 Token 长度保护。
- **很强的攻击性**:猛猛攻击别人，不知道为啥，可能是我提示词不对
- **自定义提示词**:你可以给他设计有意思的提示词，也许以后能当gal玩

## 快速开始

1. **配置环境**
   - 确保本地已运行 OneBot V11 协议端（如 NapCatQQ），WebSocket 端口默认为 3001。（直接弄napcat吧）
   - 前端配置api key

2. **运行**
   - 运行 `ChatCopilotApplication.main()` 启动程序。

## 注意事项
- **Simbot 依赖**: 项目使用了 Simbot 4.x。如果遇到 `BotManager` 相关的编译错误，请检查 Maven 依赖是否下载成功。
- **发送功能**: `MainController.java` 中的发送逻辑暂时被注释（为了防止编译错误），请在确认依赖正常后取消注释。

## 目录结构
- `service/GroupMemoryService`: 消息记忆与活跃群管理。
- `service/AiAnalysisService`: AI 接口调用与保护。
- `ui/MainController`: 界面逻辑与异步任务。

  <img width="400" height="400" alt="PixPin_2025-11-28_13-24-16" src="https://github.com/user-attachments/assets/06bc9964-4ba2-4392-ba2b-9f62dfe8bba2" />
  
<img width="400" height="400" alt="PixPin_2025-11-28_13-24-37" src="https://github.com/user-attachments/assets/9025357b-c5ae-4a2f-b837-18c4c1d94486" />

