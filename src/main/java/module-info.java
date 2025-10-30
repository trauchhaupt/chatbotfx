module chatbotfx {
    requires jakarta.validation;
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires java.net.http;
    requires org.jsoup;
    requires org.apache.commons.io;
    requires io.github.ollama4j;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires com.fasterxml.jackson.databind;
    requires java.desktop;

    exports de.vrauchhaupt.chatbotfx to javafx.controls, javafx.graphics, javafx.fxml;
    exports de.vrauchhaupt.chatbotfx.view to javafx.controls, javafx.graphics, javafx.fxml;
    exports de.vrauchhaupt.chatbotfx.manager to javafx.controls, javafx.fxml, javafx.graphics;
    exports de.vrauchhaupt.chatbotfx.model to com.fasterxml.jackson.databind;

    opens de.vrauchhaupt.chatbotfx.view;
    opens de.vrauchhaupt.chatbotfx.manager;
}