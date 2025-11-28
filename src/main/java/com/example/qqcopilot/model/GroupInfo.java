package com.example.qqcopilot.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class GroupInfo {
    private String groupId;
    private String groupName;
    private LocalDateTime lastActiveTime;

    public GroupInfo(String groupId, String groupName, LocalDateTime lastActiveTime) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.lastActiveTime = lastActiveTime;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public LocalDateTime getLastActiveTime() {
        return lastActiveTime;
    }

    public void setLastActiveTime(LocalDateTime lastActiveTime) {
        this.lastActiveTime = lastActiveTime;
    }

    @Override
    public String toString() {
        // Format: [10:05] Group Name (123456)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        return String.format("[%s] %s (%s)", lastActiveTime.format(formatter), groupName, groupId);
    }
}
