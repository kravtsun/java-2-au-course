package ru.spbau.mit.ftp;

import java.io.IOException;

public abstract class AbstractServer {
    public abstract void start(String hostName, int port) throws IOException, ServerException;

    public abstract void stop() throws IOException;

    public static class ServerException extends Exception {
        ServerException(String message) {
            super(message);
        }

        ServerException(Throwable cause) {
            super(cause);
        }
    }
}
