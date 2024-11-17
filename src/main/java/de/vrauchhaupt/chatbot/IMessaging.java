package de.vrauchhaupt.chatbot;

public interface IMessaging {
    default void out(String text) {
        System.out.print(text);
    }

    default void nextLine() {
        System.out.println();
    }

    default void logLn(String text) {
        System.out.println(text);
    }

    default void logLn() {
        System.out.println();
    }

    default void log(String text) {
        System.out.print(text);
    }
}
