package com.example.qqcopilot.ui;

import com.example.qqcopilot.config.AiConfig;
import com.example.qqcopilot.service.ModelFetchService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 设置对话框控制器
 */
@Component
public class SettingsController {

    private static final Logger log = LoggerFactory.getLogger(SettingsController.class);

    private final AiConfig aiConfig;
    private final ModelFetchService modelFetchService;

    @FXML private TextField apiUrlField;
    @FXML private PasswordField apiKeyField;
    @FXML private Button toggleKeyButton;
    @FXML private Button fetchModelsButton;
    @FXML private ProgressIndicator fetchingIndicator;
    @FXML private Label statusLabel;
    @FXML private ComboBox<String> modelComboBox;
    @FXML private TextField rateLimitField;
    @FXML private TextArea systemPromptArea;
    @FXML private Button testButton;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    // 用于显示/隐藏 API Key
    private TextField apiKeyTextField;
    private boolean isKeyVisible = false;

    public SettingsController(AiConfig aiConfig, ModelFetchService modelFetchService) {
        this.aiConfig = aiConfig;
        this.modelFetchService = modelFetchService;
    }

    @FXML
    public void initialize() {
        // 加载当前配置
        apiUrlField.setText(aiConfig.getApiUrl());
        apiKeyField.setText(aiConfig.getApiKey());
        rateLimitField.setText(String.valueOf(aiConfig.getRateLimitMs()));

        // 如果有保存的模型，添加到下拉框
        if (aiConfig.getModel() != null && !aiConfig.getModel().isBlank()) {
            modelComboBox.getItems().add(aiConfig.getModel());
            modelComboBox.setValue(aiConfig.getModel());
        }

        // 加载系统提示词
        if (aiConfig.getSystemPrompt() != null && !aiConfig.getSystemPrompt().isBlank()) {
            systemPromptArea.setText(aiConfig.getSystemPrompt());
        }

        // 创建一个隐藏的 TextField 用于显示 API Key
        apiKeyTextField = new TextField();
        apiKeyTextField.setVisible(false);
        apiKeyTextField.setManaged(false);
    }

    /**
     * 切换 API Key 可见性
     */
    @FXML
    public void onToggleKeyVisibility() {
        isKeyVisible = !isKeyVisible;
        if (isKeyVisible) {
            toggleKeyButton.setText("隐藏");
            // 显示明文 - 由于 PasswordField 不能直接显示，我们使用提示
            apiKeyField.setPromptText(apiKeyField.getText());
        } else {
            toggleKeyButton.setText("显示");
            apiKeyField.setPromptText("输入你的 API Key");
        }
    }

    /**
     * 拉取模型列表
     */
    @FXML
    public void onFetchModels() {
        String url = apiUrlField.getText().trim();
        String key = apiKeyField.getText().trim();

        if (url.isEmpty() || key.isEmpty()) {
            showStatus("请先填写 API 地址和 Key", true);
            return;
        }

        fetchModelsButton.setDisable(true);
        fetchingIndicator.setVisible(true);
        showStatus("正在拉取模型列表...", false);

        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() {
                return modelFetchService.fetchModels(url, key);
            }
        };

        task.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                List<String> models = task.getValue();
                fetchModelsButton.setDisable(false);
                fetchingIndicator.setVisible(false);

                if (models.isEmpty()) {
                    showStatus("未获取到模型，请检查 API 地址和 Key", true);
                } else {
                    // 保留当前选择
                    String currentSelection = modelComboBox.getValue();
                    
                    modelComboBox.getItems().clear();
                    modelComboBox.getItems().addAll(models);
                    
                    // 恢复选择或选第一个
                    if (currentSelection != null && models.contains(currentSelection)) {
                        modelComboBox.setValue(currentSelection);
                    } else if (!models.isEmpty()) {
                        // 尝试选择一个常用模型
                        String defaultModel = findPreferredModel(models);
                        modelComboBox.setValue(defaultModel);
                    }
                    
                    showStatus("成功获取 " + models.size() + " 个模型", false);
                }
            });
        });

        task.setOnFailed(e -> {
            Platform.runLater(() -> {
                fetchModelsButton.setDisable(false);
                fetchingIndicator.setVisible(false);
                showStatus("拉取失败: " + task.getException().getMessage(), true);
            });
        });

        new Thread(task).start();
    }

    /**
     * 查找首选模型
     */
    private String findPreferredModel(List<String> models) {
        // 优先选择常用模型
        String[] preferred = {"gpt-4", "gpt-4o", "gpt-3.5-turbo", "claude", "deepseek"};
        for (String p : preferred) {
            for (String m : models) {
                if (m.toLowerCase().contains(p)) {
                    return m;
                }
            }
        }
        return models.get(0);
    }

    /**
     * 测试 API 连接
     */
    @FXML
    public void onTestConnection() {
        String url = apiUrlField.getText().trim();
        String key = apiKeyField.getText().trim();

        if (url.isEmpty() || key.isEmpty()) {
            showStatus("请先填写 API 地址和 Key", true);
            return;
        }

        testButton.setDisable(true);
        showStatus("正在测试连接...", false);

        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() {
                return modelFetchService.testConnection(url, key);
            }
        };

        task.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                testButton.setDisable(false);
                if (task.getValue()) {
                    showStatus("✓ 连接成功!", false);
                } else {
                    showStatus("✗ 连接失败，请检查配置", true);
                }
            });
        });

        task.setOnFailed(e -> {
            Platform.runLater(() -> {
                testButton.setDisable(false);
                showStatus("✗ 连接失败: " + task.getException().getMessage(), true);
            });
        });

        new Thread(task).start();
    }

    /**
     * 保存配置
     */
    @FXML
    public void onSave() {
        String url = apiUrlField.getText().trim();
        String key = apiKeyField.getText().trim();
        String model = modelComboBox.getValue();
        String rateLimitStr = rateLimitField.getText().trim();
        String systemPrompt = systemPromptArea.getText().trim();

        if (url.isEmpty()) {
            showAlert("请填写 API 地址");
            return;
        }
        if (key.isEmpty()) {
            showAlert("请填写 API Key");
            return;
        }
        if (model == null || model.isBlank()) {
            showAlert("请选择或输入模型名称");
            return;
        }

        long rateLimit = 2000;
        try {
            rateLimit = Long.parseLong(rateLimitStr);
        } catch (NumberFormatException e) {
            // 使用默认值
        }

        // 保存到配置
        aiConfig.setApiUrl(url);
        aiConfig.setApiKey(key);
        aiConfig.setModel(model);
        aiConfig.setRateLimitMs(rateLimit);
        aiConfig.setSystemPrompt(systemPrompt);
        aiConfig.save();

        showStatus("配置已保存!", false);
        
        // 关闭窗口
        closeWindow();
    }

    /**
     * 取消
     */
    @FXML
    public void onCancel() {
        closeWindow();
    }

    private void closeWindow() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }

    private void showStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.setStyle(isError ? "-fx-text-fill: #dc3545;" : "-fx-text-fill: #28a745;");
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("提示");
        alert.setHeaderText(null);
        alert.setContentText(message);
        // 设置 owner 确保对话框显示在设置窗口前面
        if (statusLabel != null && statusLabel.getScene() != null) {
            alert.initOwner(statusLabel.getScene().getWindow());
        }
        alert.showAndWait();
    }
}
