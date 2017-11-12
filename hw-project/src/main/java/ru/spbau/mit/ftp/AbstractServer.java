package ru.spbau.mit.ftp;

public abstract class AbstractServer {
    public static class ServerException extends Exception {
        ServerException(String message) {
            super(message);
        }
    }

    public abstract void start();
    public abstract void stop();
}
