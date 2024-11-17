package de.vrauchhaupt.chatbotfx;

import de.vrauchhaupt.chatbotfx.manager.SettingsManager;
import de.vrauchhaupt.chatbotfx.manager.ThreadManager;
import de.vrauchhaupt.chatbotfx.model.ChatViewModel;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.InputStream;
import java.net.URL;


public class ChatBot extends Application {

    public static ChatViewModel chatViewModel = null;

    public static void main(String... args) {
        Thread.currentThread().setUncaughtExceptionHandler(ThreadManager.instance());
        Application.launch(args);
    }

    private void addImageToPrimaryStage(Stage primaryStage, String filename) {
        try (InputStream fileStream = getClass().getResourceAsStream("/de/vrauchhaupt/chatbotfx/" + filename)) {
            primaryStage.getIcons().add(new Image( fileStream));
        } catch (Exception e) {
            throw new RuntimeException("Could not load app image " + filename);
        }
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        Thread.currentThread().setUncaughtExceptionHandler(ThreadManager.instance());

        addImageToPrimaryStage(primaryStage, "icon_16.png");
        addImageToPrimaryStage(primaryStage, "icon_32.png");
        addImageToPrimaryStage(primaryStage, "icon_48.png");
        addImageToPrimaryStage(primaryStage, "icon_512.png");
        URL resource = getClass().getResource("/de/vrauchhaupt/chatbotfx/view/ChatMainWindow.fxml");

        SettingsManager.instance().logLn("Settings loaded");

        FXMLLoader loader = new FXMLLoader(resource);
        Parent mainWindowNode = loader.load();

        Scene scene = new Scene(mainWindowNode, 1200, 1000);
        primaryStage.setScene(scene);
        primaryStage.setTitle("ChatBot");
        primaryStage.setOnCloseRequest(event -> {
            Platform.exit();
            System.exit(0);
        });
        primaryStage.show();
    }
}
