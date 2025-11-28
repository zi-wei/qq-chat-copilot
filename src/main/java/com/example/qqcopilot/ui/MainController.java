package com.example.qqcopilot.ui;

import com.example.qqcopilot.service.AiAnalysisService;
import com.example.qqcopilot.service.ChatHistoryService;
import com.example.qqcopilot.service.GroupMemoryService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import love.forte.simbot.application.Application;
import love.forte.simbot.bot.Bot;
import love.forte.simbot.common.id.ID;
import love.forte.simbot.common.id.StringID;
import love.forte.simbot.component.onebot.v11.core.actor.OneBotFriend;
import love.forte.simbot.component.onebot.v11.core.actor.OneBotGroup;
import love.forte.simbot.component.onebot.v11.core.bot.OneBotBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.BiConsumer;

@Component
public class MainController {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    private final GroupMemoryService groupMemoryService;
    private final AiAnalysisService aiAnalysisService;
    private final ChatHistoryService chatHistoryService;
    private final Application application; // Simbot Application
    private final ApplicationContext springContext;

    @FXML private ComboBox<String> chatTypeCombo;
    @FXML private TextField targetIdInput;
    @FXML private Label chatHistoryLabel;
    @FXML private Button analyzeButton;
    @FXML private Button cancelAnalyzeButton;
    @FXML private Button refreshButton;
    @FXML private Button settingsButton;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private VBox optionsContainer;
    @FXML private TextArea chatHistoryArea;

    // 当前监听的聊天 Key (格式: "group_xxx" 或 "friend_xxx")
    private String currentChatKey = null;
    // 消息回调
    private BiConsumer<String, String> messageCallback;
    // 当前分析任务
    private Task<List<String>> currentAnalyzeTask = null;
    
    // 聊天类型常量
    private static final String CHAT_TYPE_GROUP = "群聊";
    private static final String CHAT_TYPE_FRIEND = "好友";

    public MainController(GroupMemoryService groupMemoryService, 
                          AiAnalysisService aiAnalysisService, 
                          ChatHistoryService chatHistoryService,
                          Application application,
                          ApplicationContext springContext) {
        this.groupMemoryService = groupMemoryService;
        this.aiAnalysisService = aiAnalysisService;
        this.chatHistoryService = chatHistoryService;
        this.application = application;
        this.springContext = springContext;
    }

    @FXML
    public void initialize() {
        loadingIndicator.setVisible(false);
        chatHistoryArea.setText("选择聊天类型，输入目标ID后点击[加载历史]查看聊天记录...\n新消息会自动更新显示。");

        // 初始化聊天类型下拉框
        chatTypeCombo.setItems(FXCollections.observableArrayList(CHAT_TYPE_GROUP, CHAT_TYPE_FRIEND));
        chatTypeCombo.getSelectionModel().selectFirst();
        updateChatTypeUI();
        
        // 监听聊天类型变化
        chatTypeCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            updateChatTypeUI();
            // 清空当前聊天
            currentChatKey = null;
            chatHistoryArea.setText("请输入目标ID并加载历史...");
        });

        // 注册消息回调 - 实现自动更新
        messageCallback = (chatKey, message) -> {
            // 只有当前监听的聊天才更新
            if (chatKey != null && chatKey.equals(currentChatKey)) {
                Platform.runLater(() -> {
                    appendMessage(message);
                });
            }
        };
        groupMemoryService.registerMessageCallback(messageCallback);

        // 监听目标ID输入变化
        targetIdInput.textProperty().addListener((obs, oldVal, newVal) -> {
            String trimmed = newVal != null ? newVal.trim() : "";
            if (!trimmed.isEmpty() && trimmed.matches("\\d+")) {
                currentChatKey = buildChatKey(trimmed);
                // 显示该聊天的现有消息
                refreshChatHistory(currentChatKey);
            }
        });
    }
    
    /**
     * 更新 UI 根据聊天类型
     */
    private void updateChatTypeUI() {
        String chatType = chatTypeCombo.getValue();
        if (CHAT_TYPE_GROUP.equals(chatType)) {
            targetIdInput.setPromptText("输入群号...");
            chatHistoryLabel.setText("群聊记录 (实时更新):");
        } else {
            targetIdInput.setPromptText("输入好友QQ号...");
            chatHistoryLabel.setText("好友聊天 (实时更新):");
        }
    }
    
    /**
     * 根据当前类型和ID构建 chatKey
     */
    private String buildChatKey(String targetId) {
        String chatType = chatTypeCombo.getValue();
        if (CHAT_TYPE_FRIEND.equals(chatType)) {
            return GroupMemoryService.friendKey(targetId);
        } else {
            return GroupMemoryService.groupKey(targetId);
        }
    }
    
    /**
     * 从 chatKey 提取原始 ID
     */
    private String extractTargetId(String chatKey) {
        if (chatKey == null) return null;
        if (chatKey.startsWith("group_")) {
            return chatKey.substring(6);
        } else if (chatKey.startsWith("friend_")) {
            return chatKey.substring(7);
        }
        return chatKey;
    }
    
    /**
     * 判断当前是否为好友模式
     */
    private boolean isFriendMode() {
        return CHAT_TYPE_FRIEND.equals(chatTypeCombo.getValue());
    }

    /**
     * 打开设置窗口
     */
    @FXML
    public void onOpenSettings() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/settings-view.fxml"));
            // 使用 Spring 来创建 Controller
            loader.setControllerFactory(springContext::getBean);
            Parent root = loader.load();

            Stage settingsStage = new Stage();
            settingsStage.setTitle("AI 设置");
            settingsStage.initModality(Modality.APPLICATION_MODAL);
            settingsStage.initOwner(settingsButton.getScene().getWindow());
            settingsStage.setScene(new Scene(root));
            settingsStage.setResizable(false);
            settingsStage.showAndWait();
        } catch (Exception e) {
            log.error("打开设置窗口失败", e);
            showAlert("打开设置窗口失败: " + e.getMessage());
        }
    }

    /**
     * 追加一条消息到聊天区域
     */
    private void appendMessage(String message) {
        String current = chatHistoryArea.getText();
        if (current.startsWith("输入群号") || current.startsWith("该群暂无") || current.startsWith("正在加载")) {
            chatHistoryArea.setText(message + "\n");
        } else {
            chatHistoryArea.appendText(message + "\n");
        }
        // 滚动到底部
        chatHistoryArea.setScrollTop(Double.MAX_VALUE);
    }

    @FXML
    public void onRefreshChat() {
        String targetId = targetIdInput.getText().trim();
        if (targetId.isEmpty()) {
            showAlert("请输入目标ID！");
            return;
        }
        if (!targetId.matches("\\d+")) {
            showAlert("ID格式错误，请输入纯数字！");
            return;
        }
        
        String chatKey = buildChatKey(targetId);
        currentChatKey = chatKey;
        
        // 显示加载状态
        chatHistoryArea.setText("正在加载历史消息...");
        refreshButton.setDisable(true);
        
        boolean isFriend = isFriendMode();
        
        // 在后台线程加载历史消息
        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() throws Exception {
                if (isFriend) {
                    // 好友私聊 - 使用 NapCat 的 get_friend_msg_history API
                    List<String> apiHistory = chatHistoryService.fetchFriendHistory(targetId, 30);
                    // 合并到内存
                    groupMemoryService.mergeExternalHistory(chatKey, apiHistory);
                    // 返回合并后的历史
                    return groupMemoryService.getHistory(chatKey);
                } else {
                    // 群聊 - 从 OneBot API 获取历史消息
                    List<String> apiHistory = chatHistoryService.fetchGroupHistory(targetId, 30);
                    // 合并到内存
                    groupMemoryService.mergeExternalHistory(chatKey, apiHistory);
                    // 返回合并后的历史
                    return groupMemoryService.getHistory(chatKey);
                }
            }
        };
        
        task.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                List<String> history = task.getValue();
                displayHistory(history, isFriend);
                refreshButton.setDisable(false);
            });
        });
        
        task.setOnFailed(e -> {
            Platform.runLater(() -> {
                log.error("加载历史消息失败", task.getException());
                chatHistoryArea.setText("加载失败: " + task.getException().getMessage());
                refreshButton.setDisable(false);
            });
        });
        
        new Thread(task).start();
    }

    /**
     * 显示历史消息
     */
    private void displayHistory(List<String> history, boolean isFriend) {
        if (history.isEmpty()) {
            if (isFriend) {
                chatHistoryArea.setText("暂无与该好友的聊天记录\n(请确保 Bot 已连接并是该用户的好友)");
            } else {
                chatHistoryArea.setText("该群暂无聊天记录\n(请确保 Bot 已连接并加入该群)");
            }
        } else {
            StringBuilder sb = new StringBuilder();
            for (String msg : history) {
                sb.append(msg).append("\n");
            }
            chatHistoryArea.setText(sb.toString());
            // 滚动到底部
            chatHistoryArea.setScrollTop(Double.MAX_VALUE);
        }
    }

    private void refreshChatHistory(String chatKey) {
        List<String> history = groupMemoryService.getHistory(chatKey);
        displayHistory(history, chatKey.startsWith("friend_"));
    }

    @FXML
    public void onAnalyze() {
        String targetId = targetIdInput.getText().trim();
        if (targetId.isEmpty()) {
            showAlert("请输入目标ID！");
            return;
        }

        // 验证ID格式（纯数字）
        if (!targetId.matches("\\d+")) {
            showAlert("ID格式错误，请输入纯数字！");
            return;
        }
        
        String chatKey = buildChatKey(targetId);
        boolean isFriend = isFriendMode();

        List<String> history = groupMemoryService.getHistory(chatKey);
        if (history.isEmpty()) {
            if (isFriend) {
                showAlert("暂无与该好友的聊天记录，请确保有消息记录。");
            } else {
                showAlert("该群暂无聊天记录，请确保 Bot 已加入该群并有消息记录。");
            }
            return;
        }

        analyzeButton.setDisable(true);
        cancelAnalyzeButton.setVisible(true);
        loadingIndicator.setVisible(true);
        optionsContainer.getChildren().clear();

        currentAnalyzeTask = new Task<>() {
            @Override
            protected List<String> call() throws Exception {
                return aiAnalysisService.analyze(history);
            }
        };

        currentAnalyzeTask.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                renderOptions(targetId, isFriend, currentAnalyzeTask.getValue());
                resetAnalyzeState();
            });
        });

        currentAnalyzeTask.setOnFailed(e -> {
            Throwable ex = currentAnalyzeTask.getException();
            Platform.runLater(() -> {
                if (!currentAnalyzeTask.isCancelled()) {
                    showAlert("分析失败: " + ex.getMessage());
                }
                resetAnalyzeState();
            });
        });

        currentAnalyzeTask.setOnCancelled(e -> {
            Platform.runLater(() -> {
                optionsContainer.getChildren().clear();
                resetAnalyzeState();
            });
        });

        new Thread(currentAnalyzeTask).start();
    }

    /**
     * 取消分析
     */
    @FXML
    public void onCancelAnalyze() {
        if (currentAnalyzeTask != null && currentAnalyzeTask.isRunning()) {
            currentAnalyzeTask.cancel(true);
            log.info("用户取消了 AI 分析");
        }
    }

    /**
     * 重置分析状态
     */
    private void resetAnalyzeState() {
        analyzeButton.setDisable(false);
        cancelAnalyzeButton.setVisible(false);
        loadingIndicator.setVisible(false);
        currentAnalyzeTask = null;
    }

    private void renderOptions(String targetId, boolean isFriend, List<String> options) {
        optionsContainer.getChildren().clear();
        for (String optionText : options) {
            Button btn = new Button(optionText);
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setWrapText(true);
            // 使用 AtlantaFX 样式类
            btn.getStyleClass().add("flat");
            
            btn.setOnAction(event -> {
                if (isFriend) {
                    sendToFriend(targetId, optionText);
                } else {
                    sendToGroup(targetId, optionText);
                }
                optionsContainer.getChildren().clear(); 
            });
            
            optionsContainer.getChildren().add(btn);
        }
    }

    private void sendToGroup(String groupId, String content) {
        try {
            log.info("Sending to group {}: {}", groupId, content);
            
            OneBotBot bot = getOneBotBot();
            if (bot == null) {
                showAlert("未连接到任何 QQ 账号 (Bot)，请检查 NapCat 连接！");
                return;
            }

            // 获取群对象并发送消息 - 使用 StringID.valueOf() 创建ID
            ID gid = StringID.valueOf(groupId);
            // 使用 bot.getGroupRelation().getGroup(id) 获取群
            OneBotGroup group = bot.getGroupRelation().getGroup(gid);
            if (group != null) {
                group.sendBlocking(content);
                log.info("消息已发送到群 {}", groupId);
                
                // 将Bot发送的消息也记录到历史中
                String chatKey = GroupMemoryService.groupKey(groupId);
                String formattedMessage = String.format("[我]: %s", content);
                groupMemoryService.addSentMessage(chatKey, formattedMessage);
                // 更新UI显示
                Platform.runLater(() -> appendMessage(formattedMessage));
            } else {
                showAlert("Bot 找不到群: " + groupId + " (可能 Bot 不在群里)");
            }

        } catch (Exception e) {
            log.error("发送消息失败", e);
            showAlert("发送失败: " + e.getMessage());
        }
    }
    
    private void sendToFriend(String friendId, String content) {
        try {
            log.info("Sending to friend {}: {}", friendId, content);
            
            OneBotBot bot = getOneBotBot();
            if (bot == null) {
                showAlert("未连接到任何 QQ 账号 (Bot)，请检查 NapCat 连接！");
                return;
            }

            // 获取好友对象并发送消息
            ID fid = StringID.valueOf(friendId);
            // 使用 bot.getContactRelation().getContact(id) 获取好友
            OneBotFriend friend = bot.getContactRelation().getContact(fid);
            if (friend != null) {
                friend.sendBlocking(content);
                log.info("消息已发送给好友 {}", friendId);
                
                // 将Bot发送的消息也记录到历史中
                String chatKey = GroupMemoryService.friendKey(friendId);
                String formattedMessage = String.format("[我]: %s", content);
                groupMemoryService.addSentMessage(chatKey, formattedMessage);
                // 更新UI显示
                Platform.runLater(() -> appendMessage(formattedMessage));
            } else {
                showAlert("Bot 找不到好友: " + friendId + " (可能不是好友关系)");
            }

        } catch (Exception e) {
            log.error("发送消息失败", e);
            showAlert("发送失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取已连接的 OneBotBot
     */
    private OneBotBot getOneBotBot() {
        for (var botManager : application.getBotManagers()) {
            for (Bot b : botManager.allToList()) {
                if (b instanceof OneBotBot oneBotBot) {
                    return oneBotBot;
                }
            }
        }
        return null;
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("提示");
        alert.setHeaderText(null);
        alert.setContentText(message);
        // 设置 owner 确保对话框显示在主窗口前面
        if (settingsButton != null && settingsButton.getScene() != null) {
            alert.initOwner(settingsButton.getScene().getWindow());
        }
        alert.showAndWait();
    }
}
