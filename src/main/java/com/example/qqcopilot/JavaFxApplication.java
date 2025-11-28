package com.example.qqcopilot;

import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

public class JavaFxApplication extends Application {

    private ConfigurableApplicationContext context;

    @Override
    public void init() {
        context = SpringApplication.run(ChatCopilotApplication.class);
    }

    @Override
    public void start(Stage stage) throws Exception {
        // 应用 AtlantaFX 主题 (PrimerLight - 类似 GitHub 风格)
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/fxml/main-view.fxml"));
        fxmlLoader.setControllerFactory(context::getBean);
        Parent root = fxmlLoader.load();
        Scene scene = new Scene(root);
        stage.setTitle("QQ Chat Copilot");
        stage.setScene(scene);
        stage.setAlwaysOnTop(true);
        
        // 点击关闭按钮时退出整个应用
        stage.setOnCloseRequest(event -> {
            Platform.exit();
            System.exit(0);
        });
        
        stage.show();
    }

    @Override
    public void stop() {
        context.close();
    }
}
