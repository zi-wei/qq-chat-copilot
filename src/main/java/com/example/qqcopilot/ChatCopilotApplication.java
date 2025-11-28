package com.example.qqcopilot;

import javafx.application.Application;
import love.forte.simbot.spring.EnableSimbot;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@EnableSimbot
@SpringBootApplication
public class ChatCopilotApplication {

    public static void main(String[] args) {
        Application.launch(JavaFxApplication.class, args);
    }
}
