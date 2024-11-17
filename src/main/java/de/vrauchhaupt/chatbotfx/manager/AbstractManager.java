package de.vrauchhaupt.chatbotfx.manager;

import de.vrauchhaupt.chatbotfx.IMessaging;

public abstract class AbstractManager implements IMessaging {
    public abstract boolean isWorking();
}
