package com.example.qqcopilot.service;

import com.example.qqcopilot.model.GroupInfo;
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotFriendMessageEvent;
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotNormalGroupMessageEvent;
import love.forte.simbot.quantcat.common.annotations.Listener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.BiConsumer;

@Service
public class GroupMemoryService {

    private static final Logger log = LoggerFactory.getLogger(GroupMemoryService.class);

    private final MessageSanitizer sanitizer;
    private final ApplicationEventPublisher eventPublisher;

    // Map<ChatId, Deque<MessageString>> - ChatId 可以是群号或好友QQ号 (前缀区分: "group_xxx" 或 "friend_xxx")
    private final Map<String, LinkedBlockingDeque<String>> chatHistory = new ConcurrentHashMap<>();
    
    // List of active groups
    private final LinkedList<GroupInfo> activeGroups = new LinkedList<>();
    
    private static final int MAX_HISTORY_SIZE = 99;
    private static final int MAX_ACTIVE_GROUPS = 10;

    // 消息更新回调列表 - 用于通知 UI 更新 (chatKey, message)
    private final List<BiConsumer<String, String>> messageCallbacks = new CopyOnWriteArrayList<>();

    public GroupMemoryService(MessageSanitizer sanitizer, ApplicationEventPublisher eventPublisher) {
        this.sanitizer = sanitizer;
        this.eventPublisher = eventPublisher;
    }
    
    // ============ 静态工具方法 - 生成聊天 Key ============
    public static String groupKey(String groupId) {
        return "group_" + groupId;
    }
    
    public static String friendKey(String friendId) {
        return "friend_" + friendId;
    }
    
    /**
     * 添加Bot发送的消息到历史记录中
     * @param chatKey 聊天Key (group_xxx 或 friend_xxx)
     * @param formattedMessage 已格式化的消息，如 "[我]: 消息内容"
     */
    public void addSentMessage(String chatKey, String formattedMessage) {
        LinkedBlockingDeque<String> history = chatHistory.computeIfAbsent(chatKey, k -> new LinkedBlockingDeque<>());
        history.add(formattedMessage);
        
        // 限制大小
        while (history.size() > MAX_HISTORY_SIZE) {
            history.poll();
        }
        
        log.info("已记录发送消息到 {}: {}", chatKey, formattedMessage);
    }

    /**
     * 注册消息回调 - 当收到新消息时通知
     * @param callback (chatKey, formattedMessage) -> void, chatKey格式: "group_xxx" 或 "friend_xxx"
     */
    public void registerMessageCallback(BiConsumer<String, String> callback) {
        messageCallbacks.add(callback);
    }

    /**
     * 移除消息回调
     */
    public void unregisterMessageCallback(BiConsumer<String, String> callback) {
        messageCallbacks.remove(callback);
    }

    @Listener
    public void onGroupMessage(OneBotNormalGroupMessageEvent event) {
        // 1. 获取群ID
        String groupId = event.getGroupId().toString();
        String chatKey = groupKey(groupId);
        
        // 2. 获取群名称 (尝试从事件获取，如果没有则用ID代替)
        String groupName = "群 " + groupId;
        try {
            var group = event.getContent(); // 使用 getContent() 获取群对象
            if (group != null && group.getName() != null && !group.getName().isBlank()) {
                groupName = group.getName();
            }
        } catch (Exception e) {
            log.debug("获取群名称失败，使用默认名称", e);
        }
        
        // 3. 获取发送者信息 - 使用 getSourceEvent() 获取原始事件的 Sender
        String senderName = event.getUserId().toString();
        try {
            var rawSender = event.getSourceEvent().getSender();
            if (rawSender != null) {
                String nick = rawSender.getNickname();
                if (nick != null && !nick.isBlank()) {
                    senderName = nick;
                }
            }
        } catch (Exception e) {
            log.debug("获取发送者昵称失败", e);
        }

        // 4. 清洗内容
        String content = sanitizer.sanitize(event.getMessageContent().getPlainText());
        String formattedMessage = String.format("[%s]: %s", senderName, content);
        
        log.info("收到群消息: {} - {}", groupName, formattedMessage);

        // 5. 更新历史记录 (使用 chatKey)
        chatHistory.computeIfAbsent(chatKey, k -> new LinkedBlockingDeque<>()).add(formattedMessage);
        LinkedBlockingDeque<String> history = chatHistory.get(chatKey);
        while (history.size() > MAX_HISTORY_SIZE) {
            history.poll();
        }

        // 6. 通知所有回调 (用于 UI 自动更新, 使用 chatKey)
        for (BiConsumer<String, String> callback : messageCallbacks) {
            try {
                callback.accept(chatKey, formattedMessage);
            } catch (Exception e) {
                log.debug("消息回调执行失败", e);
            }
        }

        // 7. 更新活跃列表
        updateActiveGroups(groupId, groupName);
    }

    /**
     * 监听好友私聊消息
     */
    @Listener
    public void onFriendMessage(OneBotFriendMessageEvent event) {
        // 1. 获取好友ID - 通过 sourceEvent 获取
        String friendId = event.getSourceEvent().getUserId().toString();
        String chatKey = friendKey(friendId);
        
        // 2. 获取好友昵称
        String friendName = friendId;
        try {
            var rawSender = event.getSourceEvent().getSender();
            if (rawSender != null) {
                String nick = rawSender.getNickname();
                if (nick != null && !nick.isBlank()) {
                    friendName = nick;
                }
            }
        } catch (Exception e) {
            log.debug("获取好友昵称失败", e);
        }

        // 3. 清洗内容
        String content = sanitizer.sanitize(event.getMessageContent().getPlainText());
        String formattedMessage = String.format("[%s]: %s", friendName, content);
        
        log.info("收到好友消息: {} - {}", friendName, formattedMessage);

        // 4. 更新历史记录 (使用 chatKey)
        chatHistory.computeIfAbsent(chatKey, k -> new LinkedBlockingDeque<>()).add(formattedMessage);
        LinkedBlockingDeque<String> history = chatHistory.get(chatKey);
        while (history.size() > MAX_HISTORY_SIZE) {
            history.poll();
        }

        // 5. 通知所有回调 (用于 UI 自动更新, 使用 chatKey)
        for (BiConsumer<String, String> callback : messageCallbacks) {
            try {
                callback.accept(chatKey, formattedMessage);
            } catch (Exception e) {
                log.debug("消息回调执行失败", e);
            }
        }
    }

    private synchronized void updateActiveGroups(String groupId, String groupName) {
        activeGroups.removeIf(g -> g.getGroupId().equals(groupId));
        activeGroups.addFirst(new GroupInfo(groupId, groupName, LocalDateTime.now()));
        
        while (activeGroups.size() > MAX_ACTIVE_GROUPS) {
            activeGroups.removeLast();
        }

        eventPublisher.publishEvent(new ActiveGroupsUpdatedEvent(this, getRecentGroupsSnapshot()));
    }

    public synchronized List<GroupInfo> getRecentGroupsSnapshot() {
        return new ArrayList<>(activeGroups);
    }

    /**
     * 获取历史记录 (通过 chatKey)
     * @param chatKey 格式: "group_xxx" 或 "friend_xxx"
     */
    public List<String> getHistory(String chatKey) {
        LinkedBlockingDeque<String> history = chatHistory.get(chatKey);
        if (history == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(history);
    }
    
    /**
     * 获取群历史记录 (便捷方法)
     */
    public List<String> getGroupHistory(String groupId) {
        return getHistory(groupKey(groupId));
    }
    
    /**
     * 获取好友历史记录 (便捷方法)
     */
    public List<String> getFriendHistory(String friendId) {
        return getHistory(friendKey(friendId));
    }

    /**
     * 将外部获取的历史消息合并到内存中 (去重)
     * @param chatKey 聊天Key (group_xxx 或 friend_xxx)
     * @param externalMessages 从 API 获取的历史消息
     */
    public void mergeExternalHistory(String chatKey, List<String> externalMessages) {
        if (externalMessages == null || externalMessages.isEmpty()) {
            return;
        }
        
        LinkedBlockingDeque<String> history = chatHistory.computeIfAbsent(chatKey, k -> new LinkedBlockingDeque<>());
        
        // 获取现有消息用于去重
        Set<String> existingMessages = new HashSet<>(history);
        
        // 将外部消息添加到队头 (历史消息在前)
        LinkedBlockingDeque<String> newHistory = new LinkedBlockingDeque<>();
        for (String msg : externalMessages) {
            if (!existingMessages.contains(msg)) {
                newHistory.addLast(msg);
            }
        }
        // 添加现有消息
        newHistory.addAll(history);
        
        // 替换历史记录
        chatHistory.put(chatKey, newHistory);
        
        // 限制大小
        while (newHistory.size() > MAX_HISTORY_SIZE) {
            newHistory.poll();
        }
        
        log.info("合并 {} 历史消息，当前共 {} 条", chatKey, newHistory.size());
    }
    
    public static class ActiveGroupsUpdatedEvent extends org.springframework.context.ApplicationEvent {
        private final List<GroupInfo> activeGroups;

        public ActiveGroupsUpdatedEvent(Object source, List<GroupInfo> activeGroups) {
            super(source);
            this.activeGroups = activeGroups;
        }

        public List<GroupInfo> getActiveGroups() {
            return activeGroups;
        }
    }
}
