package com.example.qqcopilot.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 从 AI API 拉取可用模型列表
 */
@Service
public class ModelFetchService {

    private static final Logger log = LoggerFactory.getLogger(ModelFetchService.class);

    private final OkHttpClient client;
    private final Gson gson;

    public ModelFetchService() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }

    /**
     * 从 API 获取可用模型列表
     * @param baseUrl API 基础地址 (如 https://api.openai.com/v1)
     * @param apiKey API Key
     * @return 模型 ID 列表
     */
    public List<String> fetchModels(String baseUrl, String apiKey) {
        List<String> models = new ArrayList<>();

        try {
            // 构建 /models 端点 URL
            String modelsUrl = buildModelsUrl(baseUrl);
            log.info("拉取模型列表: {}", modelsUrl);

            Request request = new Request.Builder()
                    .url(modelsUrl)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .get()
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("拉取模型失败: HTTP {}", response.code());
                    return Collections.emptyList();
                }

                String body = response.body() != null ? response.body().string() : "";
                JsonObject root = gson.fromJson(body, JsonObject.class);

                // OpenAI 格式: { "data": [ { "id": "gpt-4", ... }, ... ] }
                if (root.has("data") && root.get("data").isJsonArray()) {
                    JsonArray data = root.getAsJsonArray("data");
                    for (JsonElement elem : data) {
                        JsonObject modelObj = elem.getAsJsonObject();
                        if (modelObj.has("id")) {
                            String modelId = modelObj.get("id").getAsString();
                            models.add(modelId);
                        }
                    }
                }
                // 某些 API 可能直接返回数组
                else if (root.has("models") && root.get("models").isJsonArray()) {
                    JsonArray data = root.getAsJsonArray("models");
                    for (JsonElement elem : data) {
                        if (elem.isJsonPrimitive()) {
                            models.add(elem.getAsString());
                        } else if (elem.isJsonObject()) {
                            JsonObject modelObj = elem.getAsJsonObject();
                            if (modelObj.has("id")) {
                                models.add(modelObj.get("id").getAsString());
                            } else if (modelObj.has("name")) {
                                models.add(modelObj.get("name").getAsString());
                            }
                        }
                    }
                }

                log.info("成功拉取 {} 个模型", models.size());
            }

        } catch (Exception e) {
            log.error("拉取模型列表异常", e);
        }

        // 按名称排序
        Collections.sort(models);
        return models;
    }

    /**
     * 构建 /models 端点 URL
     */
    private String buildModelsUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }

        // 去除末尾斜杠
        String url = baseUrl.trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        // 如果 URL 已经包含 /chat/completions，替换为 /models
        if (url.endsWith("/chat/completions")) {
            url = url.replace("/chat/completions", "/models");
        }
        // 如果 URL 以 /v1 结尾，追加 /models
        else if (url.endsWith("/v1")) {
            url = url + "/models";
        }
        // 如果是其他情况，尝试智能处理
        else if (!url.endsWith("/models")) {
            // 检查是否需要加 /v1/models
            if (url.contains("/v1")) {
                int idx = url.indexOf("/v1");
                url = url.substring(0, idx + 3) + "/models";
            } else {
                url = url + "/models";
            }
        }

        return url;
    }

    /**
     * 测试 API 连接
     */
    public boolean testConnection(String baseUrl, String apiKey) {
        try {
            List<String> models = fetchModels(baseUrl, apiKey);
            return !models.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}
