package com.example.qqcopilot.service;

import com.example.qqcopilot.config.AiConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AiAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisService.class);

    private final AiConfig aiConfig;
    private final OkHttpClient client;
    private final Gson gson;
    private final AtomicLong lastRequestTime;

    public AiAnalysisService(AiConfig aiConfig) {
        this.aiConfig = aiConfig;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)  // 3分钟读取超时，AI 可能需要较长时间
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
        this.lastRequestTime = new AtomicLong(0);
    }

    public List<String> analyze(List<String> history) throws IOException {
        // 检查配置
        if (!aiConfig.isConfigured()) {
            throw new IOException("请先在设置中配置 AI API");
        }

        String apiUrl = buildChatCompletionsUrl(aiConfig.getApiUrl());
        String apiKey = aiConfig.getApiKey();
        String model = aiConfig.getModel();

        log.info("AI 请求 URL: {}, Model: {}", apiUrl, model);

        if (history == null || history.isEmpty()) {
            return Collections.singletonList("当前没有聊天记录，无法分析。");
        }

        // 2. Token Protection (Simple char count)
        // Max chars approx 5000 to be safe
        List<String> safeHistory = new ArrayList<>(history);
        while (calculateTotalLength(safeHistory) > 5000 && !safeHistory.isEmpty()) {
            safeHistory.remove(0);
        }
        
        String contextBlock = String.join("\n", safeHistory);

        // 3. Construct Request
        // 如果用户设置了自定义提示词，使用自定义的；否则使用默认提示词
        String customPrompt = aiConfig.getSystemPrompt();
        String systemPrompt;
        if (customPrompt != null && !customPrompt.isBlank()) {
            // 用户自定义提示词 + 固定的格式要求
            systemPrompt = customPrompt + "\n\n" +
                    "请根据提供的聊天上下文，为我（当前用户）生成 3 个不同风格的回复建议。" +
                    "请忽略 (图片)、(表情) 等占位符，专注于文本逻辑。" +
                    "请严格只返回一个 JSON 字符串数组，格式如：[\"建议1\", \"建议2\", \"建议3\"]。不要包含任何 Markdown 标记或其他解释性文字。";
        } else {
            // 默认提示词
            systemPrompt = "你是一个高情商聊天辅助助手。请根据提供的群聊上下文，为我（当前用户）生成 3 个不同风格的回复建议（例如：幽默、温和、犀利）。" +
                    "请忽略 (图片)、(表情) 等占位符，专注于文本逻辑。" +
                    "请严格只返回一个 JSON 字符串数组，格式如：[\"建议1\", \"建议2\", \"建议3\"]。不要包含任何 Markdown 标记或其他解释性文字。";
        }

        String userPrompt = "聊天记录：\n" + contextBlock;

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("stream", false);
        
        JsonArray messages = new JsonArray();
        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", systemPrompt);
        messages.add(systemMsg);

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userPrompt);
        messages.add(userMsg);

        requestBody.add("messages", messages);

        Request request = new Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(gson.toJson(requestBody), MediaType.get("application/json")))
                .build();

        // 4. Execute
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("AI 请求失败: " + response.code() + " " + response.message());
            }
            
            ResponseBody body = response.body();
            if (body == null) {
                 throw new IOException("AI 响应为空");
            }
            String responseBody = body.string();
            return parseResponse(responseBody);
        }
    }

    /**
     * 构建完整的 chat/completions URL
     * 支持多种输入格式:
     * - http://xxx/v1 -> http://xxx/v1/chat/completions
     * - http://xxx/v1/chat/completions -> 保持不变
     * - http://xxx -> http://xxx/v1/chat/completions
     */
    private String buildChatCompletionsUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }

        String url = baseUrl.trim();
        
        // 去除末尾斜杠
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        // 如果已经包含 chat/completions，直接返回
        if (url.endsWith("/chat/completions")) {
            return url;
        }

        // 如果以 /v1 结尾，追加 /chat/completions
        if (url.endsWith("/v1")) {
            return url + "/chat/completions";
        }

        // 如果包含 /v1 但后面还有其他路径，尝试修正
        if (url.contains("/v1/")) {
            // 可能是 /v1/models 之类的，替换为 /v1/chat/completions
            int idx = url.indexOf("/v1/");
            return url.substring(0, idx) + "/v1/chat/completions";
        }

        // 其他情况，假设需要追加完整路径
        return url + "/v1/chat/completions";
    }

    private int calculateTotalLength(List<String> list) {
        int len = 0;
        for (String s : list) {
            len += s.length();
        }
        return len;
    }

    private List<String> parseResponse(String jsonResponse) {
        try {
            // Parse OpenAI/DeepSeek format
            // { "choices": [ { "message": { "content": "[\"opt1\", ...]" } } ] }
            JsonObject root = JsonParser.parseString(jsonResponse).getAsJsonObject();
            String content = root.getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content").getAsString();

            // Clean up markdown code blocks if present
            content = content.replaceAll("```json", "").replaceAll("```", "").trim();

            JsonArray optionsArray = JsonParser.parseString(content).getAsJsonArray();
            List<String> result = new ArrayList<>();
            optionsArray.forEach(e -> result.add(e.getAsString()));
            return result;
        } catch (Exception e) {
            log.error("Failed to parse AI response", e);
            return Collections.singletonList("解析 AI 响应失败，请检查日志。");
        }
    }
}
