package com.example.qqcopilot.service;

import love.forte.simbot.application.Application;
import love.forte.simbot.bot.Bot;
import love.forte.simbot.component.onebot.v11.core.bot.OneBotBot;
import love.forte.simbot.event.BotStartedEvent;
import love.forte.simbot.quantcat.common.annotations.Listener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class BotConnectionMonitor {

    private static final Logger log = LoggerFactory.getLogger(BotConnectionMonitor.class);

    private final Application application;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public BotConnectionMonitor(Application application) {
        this.application = application;
    }

    /**
     * 监听 Simbot 的 Bot 启动事件
     */
    @Listener
    public void onBotStarted(BotStartedEvent event) {
        Bot bot = event.getBot();
        log.info("═══════════════════════════════════════════════════════════");
        log.info("✅ Bot 已成功连接并启动!");
        log.info("   Bot ID: {}", bot.getId());
        log.info("   Bot 类型: {}", bot.getClass().getSimpleName());
        
        if (bot instanceof OneBotBot oneBotBot) {
            log.info("   QQ 号: {}", oneBotBot.getId());
            log.info("   OneBot 组件已成功连接到 NapCat!");
        }
        log.info("═══════════════════════════════════════════════════════════");
    }

    /**
     * Spring 应用启动完成后检查 Bot 状态
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Spring 应用已启动，开始检查 Simbot 连接状态...");
        
        // 延迟 5 秒检查，给 Bot 连接一些时间
        scheduler.schedule(this::checkBotConnection, 5, TimeUnit.SECONDS);
    }

    private void checkBotConnection() {
        log.info("══════════════ Simbot 连接状态检查 ══════════════");
        
        int botManagerCount = 0;
        int botCount = 0;
        
        try {
            for (var botManager : application.getBotManagers()) {
                botManagerCount++;
                log.info("BotManager #{}: {}", botManagerCount, botManager.getClass().getSimpleName());
                
                for (Bot bot : botManager.allToList()) {
                    botCount++;
                    log.info("  └─ Bot #{}: ID={}, 类型={}", 
                        botCount, bot.getId(), bot.getClass().getSimpleName());
                    
                    if (bot instanceof OneBotBot oneBotBot) {
                        log.info("     ├─ QQ号: {}", oneBotBot.getId());
                        log.info("     └─ 状态: 已连接 ✅");
                    }
                }
            }
            
            if (botManagerCount == 0) {
                log.error("❌ 未找到任何 BotManager!");
                log.error("   请检查 OneBot 组件是否正确配置");
            } else if (botCount == 0) {
                log.warn("⚠️ BotManager 已加载，但没有任何 Bot 连接");
                log.warn("   可能原因:");
                log.warn("   1. simbot-bots/*.bot.json 配置文件格式错误");
                log.warn("   2. NapCat 未运行或连接地址错误");
                log.warn("   3. Token 验证失败");
                log.warn("   请检查上方日志中的错误信息");
            } else {
                log.info("✅ 共找到 {} 个 BotManager, {} 个 Bot 已连接", botManagerCount, botCount);
            }
            
        } catch (Exception e) {
            log.error("❌ 检查 Bot 连接状态时发生错误: {}", e.getMessage(), e);
        }
        
        log.info("═══════════════════════════════════════════════════════════");
    }

    @PostConstruct
    public void init() {
        log.info("BotConnectionMonitor 已初始化，将在应用启动后监控 Bot 连接状态");
    }
}
