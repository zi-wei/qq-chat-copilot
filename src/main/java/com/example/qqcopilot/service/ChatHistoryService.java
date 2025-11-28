package com.example.qqcopilot.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通过 OneBot HTTP API 获取群聊历史消息
 */
@Service
public class ChatHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ChatHistoryService.class);

    // NapCat HTTP API 配置 (从 bot 配置读取)
    private static final String API_HOST = "http://localhost:6199";
    private static final String ACCESS_TOKEN = "*#Aki#@c1F4S$#{R";

    private final HttpClient httpClient;
    private final Gson gson;

    public ChatHistoryService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new Gson();
    }

    /**
     * 获取群聊历史消息
     * @param groupId 群号
     * @param count 获取消息数量 (默认20条)
     * @return 格式化的消息列表
     */
    public List<String> fetchGroupHistory(String groupId, int count) {
        List<String> messages = new ArrayList<>();

        try {
            // 构建请求 - 使用 get_group_msg_history API
            Map<String, Object> requestMap = new HashMap<>();
            requestMap.put("group_id", Long.parseLong(groupId));
            requestMap.put("message_seq", 0); // 0 表示获取最新消息
            requestMap.put("count", count);
            
            String requestBody = gson.toJson(requestMap);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_HOST + "/get_group_msg_history"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + ACCESS_TOKEN)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject root = gson.fromJson(response.body(), JsonObject.class);
                
                // 检查返回状态
                if (root.has("retcode") && root.get("retcode").getAsInt() == 0) {
                    JsonObject data = root.getAsJsonObject("data");
                    if (data != null && data.has("messages")) {
                        JsonArray msgArray = data.getAsJsonArray("messages");
                        for (JsonElement msgElem : msgArray) {
                            JsonObject msg = msgElem.getAsJsonObject();
                            String formatted = formatMessage(msg);
                            if (formatted != null && !formatted.isBlank()) {
                                messages.add(formatted);
                            }
                        }
                    }
                } else {
                    String errMsg = root.has("message") ? root.get("message").getAsString() : "未知错误";
                    log.warn("获取群历史消息失败: {}", errMsg);
                }
            } else {
                log.warn("HTTP 请求失败: {}", response.statusCode());
            }

        } catch (Exception e) {
            log.error("获取群历史消息异常", e);
        }

        return messages;
    }

    /**
     * 格式化单条消息
     */
    private String formatMessage(JsonObject msg) {
        try {
            // 获取发送者信息
            JsonObject sender = msg.getAsJsonObject("sender");
            String nickname = "未知";
            if (sender != null) {
                if (sender.has("card") && !sender.get("card").getAsString().isBlank()) {
                    nickname = sender.get("card").getAsString();
                } else if (sender.has("nickname")) {
                    nickname = sender.get("nickname").getAsString();
                }
            }

            // 解析消息内容
            StringBuilder content = new StringBuilder();
            JsonArray messageArray = msg.getAsJsonArray("message");
            if (messageArray != null) {
                for (JsonElement segElem : messageArray) {
                    JsonObject segment = segElem.getAsJsonObject();
                    String type = segment.has("type") ? segment.get("type").getAsString() : "";
                    JsonObject data = segment.getAsJsonObject("data");

                    switch (type) {
                        case "text":
                            if (data != null && data.has("text")) {
                                content.append(data.get("text").getAsString());
                            }
                            break;
                        case "image":
                            content.append("[图片]");
                            break;
                        case "face":
                            content.append("[表情]");
                            break;
                        case "at":
                            String atQQ = data != null && data.has("qq") ? data.get("qq").getAsString() : "";
                            content.append("[@").append(atQQ).append("]");
                            break;
                        case "reply":
                            content.append("[回复]");
                            break;
                        case "forward":
                            content.append("[合并转发]");
                            break;
                        case "record":
                            content.append("[语音]");
                            break;
                        case "video":
                            content.append("[视频]");
                            break;
                        case "file":
                            content.append("[文件]");
                            break;
                        case "mface":
                            content.append("[表情包]");
                            break;
                        default:
                            // 其他类型忽略或标记
                            if (!type.isEmpty()) {
                                content.append("[").append(type).append("]");
                            }
                    }
                }
            }

            String text = content.toString().trim();
            if (text.isEmpty()) {
                return null;
            }

            return String.format("[%s]: %s", nickname, text);

        } catch (Exception e) {
            log.debug("格式化消息失败", e);
            return null;
        }
    }

    /**
     * 获取默认数量的历史消息
     */
    public List<String> fetchGroupHistory(String groupId) {
        return fetchGroupHistory(groupId, 30);
    }
    
    /**
     * 获取好友私聊历史消息
     * @param friendId 好友QQ号
     * @param count 获取消息数量
     * @return 格式化的消息列表
     */
    public List<String> fetchFriendHistory(String friendId, int count) {
        List<String> messages = new ArrayList<>();

        try {
            // 构建请求 - 使用 get_friend_msg_history API (NapCat 扩展)
            Map<String, Object> requestMap = new HashMap<>();
            requestMap.put("user_id", Long.parseLong(friendId));
            requestMap.put("message_seq", 0); // 0 表示获取最新消息
            requestMap.put("count", count);
            
            String requestBody = gson.toJson(requestMap);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_HOST + "/get_friend_msg_history"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + ACCESS_TOKEN)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            log.debug("好友历史消息响应: {}", response.body());

            if (response.statusCode() == 200) {
                JsonObject root = gson.fromJson(response.body(), JsonObject.class);
                
                // 检查返回状态
                if (root.has("retcode") && root.get("retcode").getAsInt() == 0) {
                    JsonObject data = root.getAsJsonObject("data");
                    if (data != null && data.has("messages")) {
                        JsonArray msgArray = data.getAsJsonArray("messages");
                        for (JsonElement msgElem : msgArray) {
                            JsonObject msg = msgElem.getAsJsonObject();
                            String formatted = formatFriendMessage(msg, friendId);
                            if (formatted != null && !formatted.isBlank()) {
                                messages.add(formatted);
                            }
                        }
                    }
                    log.info("获取好友 {} 历史消息成功，共 {} 条", friendId, messages.size());
                } else {
                    String errMsg = root.has("message") ? root.get("message").getAsString() : "未知错误";
                    log.warn("获取好友历史消息失败: {}", errMsg);
                }
            } else {
                log.warn("HTTP 请求失败: {}", response.statusCode());
            }

        } catch (Exception e) {
            log.error("获取好友历史消息异常", e);
        }

        return messages;
    }
    
    /**
     * 格式化好友私聊消息
     * 需要区分是自己发的还是对方发的
     */
    private String formatFriendMessage(JsonObject msg, String friendId) {
        try {
            // 获取发送者信息
            JsonObject sender = msg.getAsJsonObject("sender");
            String senderId = "";
            String nickname = "未知";
            
            if (sender != null) {
                if (sender.has("user_id")) {
                    senderId = sender.get("user_id").getAsString();
                }
                if (sender.has("nickname") && !sender.get("nickname").getAsString().isBlank()) {
                    nickname = sender.get("nickname").getAsString();
                }
            }
            
            // 判断是否是自己发的消息
            boolean isSelf = !senderId.equals(friendId);
            if (isSelf) {
                nickname = "我";
            }

            // 解析消息内容
            StringBuilder content = new StringBuilder();
            JsonArray messageArray = msg.getAsJsonArray("message");
            if (messageArray != null) {
                for (JsonElement segElem : messageArray) {
                    JsonObject segment = segElem.getAsJsonObject();
                    String type = segment.has("type") ? segment.get("type").getAsString() : "";
                    JsonObject data = segment.getAsJsonObject("data");

                    switch (type) {
                        case "text":
                            if (data != null && data.has("text")) {
                                content.append(data.get("text").getAsString());
                            }
                            break;
                        case "image":
                            content.append("[图片]");
                            break;
                        case "face":
                            content.append("[表情]");
                            break;
                        case "at":
                            String atQQ = data != null && data.has("qq") ? data.get("qq").getAsString() : "";
                            content.append("[@").append(atQQ).append("]");
                            break;
                        case "reply":
                            content.append("[回复]");
                            break;
                        case "forward":
                            content.append("[合并转发]");
                            break;
                        case "record":
                            content.append("[语音]");
                            break;
                        case "video":
                            content.append("[视频]");
                            break;
                        case "file":
                            content.append("[文件]");
                            break;
                        case "mface":
                            content.append("[表情包]");
                            break;
                        default:
                            if (!type.isEmpty()) {
                                content.append("[").append(type).append("]");
                            }
                    }
                }
            }

            String text = content.toString().trim();
            if (text.isEmpty()) {
                return null;
            }

            return String.format("[%s]: %s", nickname, text);

        } catch (Exception e) {
            log.debug("格式化好友消息失败", e);
            return null;
        }
    }
    
    /**
     * 获取默认数量的好友历史消息
     */
    public List<String> fetchFriendHistory(String friendId) {
        return fetchFriendHistory(friendId, 30);
    }
}
