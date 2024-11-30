package de.vrauchhaupt.chatbotfx.view;

import de.vrauchhaupt.chatbotfx.ChatBot;
import de.vrauchhaupt.chatbotfx.manager.LlmModelCardManager;
import de.vrauchhaupt.chatbotfx.manager.SettingsManager;
import de.vrauchhaupt.chatbotfx.manager.StableDiffusionManager;
import de.vrauchhaupt.chatbotfx.model.LlmModelCardJson;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

public class LoadableImageView extends StackPane {

    private Path representingImageFile = null;
    private final ImageView imageView = new ImageView();
    private final MenuItem menuItemRemove = new MenuItem("Delete");
    private final MenuItem menuItemEnlarge = new MenuItem("Enlarge");
    private final MenuItem menuItemSetAsModelImage = new MenuItem("Model Image");
    private final ContextMenu contextMenu = new ContextMenu(menuItemRemove,
            menuItemEnlarge,
            menuItemSetAsModelImage);

    public LoadableImageView() {
        super();
        setPrefHeight(StableDiffusionManager.GENERATED_IMAGE_HEIGHT + 2);
        setMinHeight(StableDiffusionManager.GENERATED_IMAGE_HEIGHT + 2);
        setMaxHeight(StableDiffusionManager.GENERATED_IMAGE_HEIGHT + 2);

        setPrefWidth(StableDiffusionManager.GENERATED_IMAGE_WIDTH + 2);
        setMinWidth(StableDiffusionManager.GENERATED_IMAGE_WIDTH + 2);
        setMaxWidth(StableDiffusionManager.GENERATED_IMAGE_WIDTH + 2);
        setStyle("-fx-border-color:black; -fx-border-width:1;");
        getChildren().add(imageView);

        try (InputStream is = LoadableImageView.class.getResourceAsStream("LoadingAnimation.gif")) {
            Image image = new Image(is);
            imageView.setImage(image);
        } catch (IOException e) {
            throw new RuntimeException("Cannot load loading image 'LoadingAnimation.gif'", e);
        }

        menuItemRemove.setOnAction(this::menuItemRemoveClicked);
        menuItemEnlarge.setOnAction(this::menuItemEnlargeClicked);
        menuItemSetAsModelImage.setOnAction(this::menuItemSetAsModelImageClicked);
        setOnContextMenuRequested(this::onContextMenuRequested);
    }

    private void menuItemSetAsModelImageClicked(ActionEvent actionEvent) {
        if (representingImageFile == null || !Files.exists(representingImageFile)) {
            return;
        }
        LlmModelCardJson selectedLlModelCard = LlmModelCardManager.instance().getSelectedLlModelCard();
        if (selectedLlModelCard == null)
            return;
        try {
            // Read the image (supports multiple formats: JPG, PNG, BMP, etc.)
            BufferedImage inputImage = ImageIO.read(representingImageFile.toFile());
            if (inputImage == null) {
                System.out.println("Could not read the image. Please check the file format.");
                return;
            }

            Path modelCardsDirectory = SettingsManager.instance().getPathToLlmModelCards();
            Path path = modelCardsDirectory.resolve(selectedLlModelCard.getModelCardName() + ".png");
            Files.deleteIfExists(path);

            ImageIO.write(inputImage, "png", path.toFile());
        } catch (IOException e) {
            System.err.println("Error occurred while processing the image: " + e.getMessage());
        }
    }

    private void onContextMenuRequested(ContextMenuEvent contextMenuEvent) {
        contextMenu.show(this, contextMenuEvent.getScreenX(), contextMenuEvent.getScreenY());
    }

    private void menuItemEnlargeClicked(ActionEvent actionEvent) {
        try {
            URL resource = getClass().getResource("/de/vrauchhaupt/chatbotfx/view/ImageWindow.fxml");
            FXMLLoader loader = new FXMLLoader(resource);
            Pane imageMainNode = loader.load();
            ImageWindow settingsWindow = loader.getController();
            Image curImage = imageView.getImage();
            settingsWindow.imageView.setFitWidth(curImage.getWidth());
            settingsWindow.imageView.setFitHeight(curImage.getHeight());
            settingsWindow.imageView.setImage(curImage);
            Stage dialogStage = new Stage();
            dialogStage.setTitle("Settings");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(ChatBot.mainStage);
            dialogStage.setScene(new Scene(imageMainNode));
            dialogStage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void menuItemRemoveClicked(ActionEvent actionEvent) {
        if (representingImageFile != null) {
            try {
                Files.deleteIfExists(representingImageFile);
            } catch (IOException e) {
                throw new RuntimeException("Cannot delete file image file '" + representingImageFile.toAbsolutePath() + "'", e);
            }
        }
        if (this.getParent() instanceof Pane) {
            ((Pane) this.getParent()).getChildren().remove(LoadableImageView.this);
        }
    }

    public void setImage(Image image, Path representingImageFile) {
        double factor = StableDiffusionManager.GENERATED_IMAGE_WIDTH / image.getWidth();
        double perfectHeight = (image.getHeight() * factor) + 2.0;
        setPrefHeight(perfectHeight);
        setMinHeight(perfectHeight);
        setMaxHeight(perfectHeight);
        imageView.setImage(image);
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(StableDiffusionManager.GENERATED_IMAGE_WIDTH);
        this.representingImageFile = representingImageFile;
    }
}
