package com.example.qqcopilot.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * AI 配置管理 - 持久化存储
 */
@Component
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);
    private static final String CONFIG_DIR = System.getProperty("user.home") + "/.qqcopilot";
    private static final String CONFIG_FILE = CONFIG_DIR + "/ai-config.json";

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // 从 application.yml 读取默认值
    @Value("${ai.api-url:}")
    private String defaultApiUrl;

    @Value("${ai.api-key:}")
    private String defaultApiKey;

    @Value("${ai.model:}")
    private String defaultModel;

    @Value("${ai.rate-limit-ms:2000}")
    private long defaultRateLimitMs;

    @Value("${ai.system-prompt:}")
    private String defaultSystemPrompt;

    // 配置项
    private String apiUrl = "";
    private String apiKey = "";
    private String model = "";
    private long rateLimitMs = 2000;
    private String systemPrompt = "";

    @PostConstruct
    public void init() {
        // 先设置默认值
        this.apiUrl = defaultApiUrl;
        this.apiKey = defaultApiKey;
        this.model = defaultModel;
        this.rateLimitMs = defaultRateLimitMs;
        this.systemPrompt = defaultSystemPrompt != null ? defaultSystemPrompt : "";
        
        // 然后尝试从文件加载（覆盖默认值）
        load();
    }

    /**
     * 从文件加载配置
     */
    public void load() {
        try {
            Path configPath = Paths.get(CONFIG_FILE);
            if (Files.exists(configPath)) {
                String json = Files.readString(configPath);
                AiConfigData data = gson.fromJson(json, AiConfigData.class);
                if (data != null) {
                    this.apiUrl = data.apiUrl != null ? data.apiUrl : "";
                    this.apiKey = data.apiKey != null ? data.apiKey : "";
                    this.model = data.model != null ? data.model : "";
                    this.rateLimitMs = data.rateLimitMs > 0 ? data.rateLimitMs : 2000;
                    this.systemPrompt = data.systemPrompt != null ? data.systemPrompt : "";
                    log.info("AI 配置已加载: URL={}, Model={}", apiUrl, model);
                }
            } else {
                log.info("AI 配置文件不存在，使用默认值");
            }
        } catch (Exception e) {
            log.error("加载 AI 配置失败", e);
        }
    }

    /**
     * 保存配置到文件
     */
    public void save() {
        try {
            // 确保目录存在
            Path dirPath = Paths.get(CONFIG_DIR);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            AiConfigData data = new AiConfigData();
            data.apiUrl = this.apiUrl;
            data.apiKey = this.apiKey;
            data.model = this.model;
            data.rateLimitMs = this.rateLimitMs;
            data.systemPrompt = this.systemPrompt;

            String json = gson.toJson(data);
            Files.writeString(Paths.get(CONFIG_FILE), json);
            log.info("AI 配置已保存");
        } catch (Exception e) {
            log.error("保存 AI 配置失败", e);
        }
    }

    // Getters and Setters
    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public long getRateLimitMs() {
        return rateLimitMs;
    }

    public void setRateLimitMs(long rateLimitMs) {
        this.rateLimitMs = rateLimitMs;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public boolean isConfigured() {
        return apiUrl != null && !apiUrl.isBlank() 
                && apiKey != null && !apiKey.isBlank()
                && model != null && !model.isBlank();
    }

    /**
     * 配置数据类 (用于 JSON 序列化)
     */
    private static class AiConfigData {
        String apiUrl;
        String apiKey;
        String model;
        long rateLimitMs;
        String systemPrompt;
    }
}
