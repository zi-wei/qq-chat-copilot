package com.example.qqcopilot.service;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MessageSanitizer {

    // Regex to match CQ codes like [CQ:image,...] or [CQ:face,...]
    private static final Pattern CQ_CODE_PATTERN = Pattern.compile("\\[CQ:([a-zA-Z0-9]+),.*?\\]");

    public String sanitize(String rawMessage) {
        if (rawMessage == null) {
            return "";
        }

        Matcher matcher = CQ_CODE_PATTERN.matcher(rawMessage);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String type = matcher.group(1);
            String replacement;
            switch (type) {
                case "image":
                    replacement = "(图片)";
                    break;
                case "face":
                    replacement = "(表情)";
                    break;
                case "record":
                    replacement = "(语音)";
                    break;
                case "video":
                    replacement = "(视频)";
                    break;
                case "at":
                    replacement = "@某人"; // Simplified
                    break;
                case "reply":
                    replacement = ""; // Remove reply references to keep context clean
                    break;
                case "json":
                case "xml":
                    replacement = "(卡片消息)";
                    break;
                default:
                    replacement = "(" + type + ")";
                    break;
            }
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);

        return sb.toString().trim();
    }
}
