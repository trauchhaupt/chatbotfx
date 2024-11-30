package de.vrauchhaupt.chatbotfx;

import de.vrauchhaupt.chatbotfx.manager.SettingsManager;
import de.vrauchhaupt.chatbotfx.manager.ThreadManager;
import de.vrauchhaupt.chatbotfx.model.ChatViewModel;
import de.vrauchhaupt.chatbotfx.view.ChatMainWindow;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.InputStream;
import java.net.URL;


public class ChatBot extends Application {

    public static ChatMainWindow chatMainWindow = null;
    public static Parent mainWindowNode = null;
    public static Stage mainStage = null;

    public static void main(String... args) {
        Thread.currentThread().setUncaughtExceptionHandler(ThreadManager.instance());
        Application.launch(args);
    }

    private void addImageToPrimaryStage(Stage primaryStage, String filename) {
        try (InputStream fileStream = getClass().getResourceAsStream("/de/vrauchhaupt/chatbotfx/" + filename)) {
            primaryStage.getIcons().add(new Image(fileStream));
        } catch (Exception e) {
            throw new RuntimeException("Could not load app image " + filename);
        }
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            Screen primaryScreen = Screen.getPrimary();
            mainStage = primaryStage;
            Thread.currentThread().setUncaughtExceptionHandler(ThreadManager.instance());

            addImageToPrimaryStage(primaryStage, "icon_16.png");
            addImageToPrimaryStage(primaryStage, "icon_32.png");
            addImageToPrimaryStage(primaryStage, "icon_48.png");
            addImageToPrimaryStage(primaryStage, "icon_512.png");

            SettingsManager.instance().loadFromConfigFile();

            URL resource = getClass().getResource("/de/vrauchhaupt/chatbotfx/view/ChatMainWindow.fxml");
            FXMLLoader loader = new FXMLLoader(resource);
            mainWindowNode = loader.load();
            chatMainWindow = loader.getController();
            Scene scene = new Scene(mainWindowNode, 1200, primaryScreen.getVisualBounds().getHeight() - 50);
            primaryStage.setX(0); // Position at the left of the monitor
            primaryStage.setY(4); // Start from the top
            primaryStage.setScene(scene);
            primaryStage.setTitle("ChatBot");
            primaryStage.setOnCloseRequest(event -> {
                Platform.exit();
                System.exit(0);
            });
            primaryStage.show();
            SettingsManager.instance().assertAllSettingsValid();

            chatMainWindow.buttonReloadModelsClicked(null);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
