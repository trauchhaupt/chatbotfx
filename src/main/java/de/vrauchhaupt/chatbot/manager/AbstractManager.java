package de.vrauchhaupt.chatbot.manager;

import de.vrauchhaupt.chatbot.IMessaging;

public abstract class AbstractManager implements IMessaging {
    public abstract boolean isWorking();
}
