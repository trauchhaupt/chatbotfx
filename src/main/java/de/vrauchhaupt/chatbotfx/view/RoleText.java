package de.vrauchhaupt.chatbotfx.view;

import de.vrauchhaupt.chatbotfx.model.DisplayRole;
import javafx.scene.text.Text;

public class RoleText extends Text {
    public RoleText(DisplayRole role) {
        super(role.name()+ " : ");
        getStyleClass().add("roleText");
        setStyle("-fx-font-weight: bold; -fx-text-fill: " + role.getBackgroundColor() + ";");
    }
}
