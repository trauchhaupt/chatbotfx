package de.vrauchhaupt.chatbotfx.view;

import javafx.application.Platform;

public interface IChatBoxViewComponent {
    default void assertFxThread()
    {
        if (!Platform.isFxApplicationThread()) {
            throw new RuntimeException("Method must be called within FxTread, but was executed on " + Thread.currentThread().getName());
        }
    }
}
