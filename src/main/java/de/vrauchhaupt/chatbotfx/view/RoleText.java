package de.vrauchhaupt.chatbotfx.view;

import de.vrauchhaupt.chatbotfx.model.DisplayRole;
import javafx.scene.text.Text;

public class RoleText extends Text {
    private final DisplayRole role;
    public RoleText(DisplayRole role) {
        super(role.name()+ " : ");
        this.role = role;
        getStyleClass().add("roleText");
        setStyle("-fx-font-weight: bold; -fx-text-fill: " + role.getBackgroundColor() + ";");
    }

    public DisplayRole getRole() {
        return role;
    }
}
