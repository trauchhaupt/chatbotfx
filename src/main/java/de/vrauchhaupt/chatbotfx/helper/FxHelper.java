package de.vrauchhaupt.chatbotfx.helper;

import javafx.scene.layout.Region;

public class FxHelper {

    public static void setAllWidths(Region region,double width )
    {
        region.setMinWidth(width);
        region.setMaxWidth(width);
        region.setPrefWidth(width);
    }
}
