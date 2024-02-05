package io.jenkins.plugins.monokle.cli.setup;

import java.io.PrintStream;

public class MonokleLogger {
    private static MonokleLogger instance = null;

    private PrintStream printStream;

    private MonokleLogger() {
        // private constructor to prevent instantiation
    }

    private static MonokleLogger getInstance() {
        if (instance == null) {
            instance = new MonokleLogger();
        }
        return instance;
    }

    public static void init(PrintStream printStream) {
        getInstance().printStream = printStream;
    }

    public static void println(String msg) {
        if (getInstance().printStream == null) {
            return;
        }
        getInstance().printStream.println("[Monokle] " + msg);
    }

    public static PrintStream getPrintStream() {
        return getInstance().printStream;
    }
}
