package de.vrauchhaupt.chatbotfx.model;

import de.vrauchhaupt.chatbotfx.manager.ThreadManager;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class ControlledThread extends Thread {
    private final Consumer<ControlledThread> task;
    AtomicBoolean goon = new AtomicBoolean(true);

    public ControlledThread( Consumer<ControlledThread> task) {
        super(() -> runInternally());
        this.task = task;
    }

    private static void runInternally() {
        ControlledThread thread = (ControlledThread) Thread.currentThread();
        while (thread.shallThreadRun()) {
            try {
                thread.task.accept(thread);
                thread.waitAWhile();
            } catch (Exception e) {
                ThreadManager.instance().uncaughtException(thread, e);
            }
        }
    }

    public void stopThread() {
        this.goon.set(false);
    }

    public boolean shallThreadRun() {
        return this.goon.get();
    }

    public void waitAWhile() {
        try {
            Thread.sleep(75);
        } catch (InterruptedException e) {
            stopThread();
        }
    }
}
