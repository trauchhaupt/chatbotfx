package de.vrauchhaupt.chatbotfx.manager;

import de.vrauchhaupt.chatbotfx.model.ControlledThread;
import de.vrauchhaupt.chatbotfx.model.ErrorDto;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.util.function.Consumer;

public class ThreadManager extends AbstractManager implements Thread.UncaughtExceptionHandler {
    private static ThreadManager INSTANCE = new ThreadManager();
    private final ObservableList<ErrorDto> errors = FXCollections.observableArrayList();

    public ThreadManager() {
        errors.addListener((ListChangeListener<? super ErrorDto>) this::newErrorRecognized);
    }

    public static ThreadManager instance() {
        return INSTANCE;
    }

    private void newErrorRecognized(ListChangeListener.Change<? extends ErrorDto> change) {
        while (change.next()) {
            if (!change.wasAdded()) {
                continue;
            }
            for (ErrorDto errorDto : change.getAddedSubList()) {
                System.err.println(errorDto.getErrorText());
                if (errorDto.getException() != null)
                    errorDto.getException().printStackTrace();
            }
        }
    }

    @Override
    public boolean isWorking() {
        return false;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        e.printStackTrace();
        errors.add(new ErrorDto(e.getMessage(), e));
    }

    public Thread startThread(String threadName, Runnable runnable, Consumer<Thread> onThreadFinished) {
        System.out.println("Starting worker thread '" + threadName + "'");
        final Thread returnValue = new Thread(() -> {
            try {
                runnable.run();
            } catch (Exception e) {
                if (e instanceof InterruptedException)
                    System.out.println("Interruppted thread " + threadName);
                else
                    this.uncaughtException(Thread.currentThread(), e);
            } finally {
                if (onThreadFinished != null)
                    onThreadFinished.accept(Thread.currentThread());
                System.out.println("Finished with worker thread '" + threadName + "'");
            }
        });
        returnValue.setName(threadName);
        returnValue.setUncaughtExceptionHandler(this);
        returnValue.start();
        return returnValue;
    }

    public ControlledThread startEndlessThread(String threadName, Consumer<ControlledThread> runnable) {
        System.out.println("Starting endless thread '" + threadName + "'");
        ControlledThread controlledThread = new ControlledThread(runnable);
        controlledThread.setName(threadName);
        controlledThread.setUncaughtExceptionHandler(ThreadManager.instance());
        controlledThread.start();
        return controlledThread;
    }
}
