package de.vrauchhaupt.chatbot.view;

import de.vrauchhaupt.chatbot.manager.StableDiffusionManager;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class LoadableImageView extends StackPane {

    private Path representingImageFile = null;
    private ImageView imageView = new ImageView();

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
            imageView.setImage(new Image(is));
        } catch (IOException e) {
            throw new RuntimeException("Cannot load loading image 'LoadingAnimation.gif'", e);
        }

        setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) { // Double-click detected
                if (representingImageFile != null) {
                    try {
                        Files.deleteIfExists(representingImageFile);
                    } catch (IOException e) {
                        throw new RuntimeException("Cannot delete file image file '" + representingImageFile.toAbsolutePath() + "'", e);
                    }
                }
                if (this.getParent() instanceof Pane)
                    ((Pane) this.getParent()).getChildren().remove(LoadableImageView.this);
            }
        });
    }

    public void setImage(Image image, Path representingImageFile) {
        imageView.setImage(image);
        this.representingImageFile = representingImageFile;
    }
}
