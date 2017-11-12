package ru.spbau.mit.ftp;

import java.io.IOException;

public abstract class AbstractClient {
    public static class ClientNotConnectedException extends Exception {
        ClientNotConnectedException() {
            super("not connected");
        }
    }
    public static class ClientException extends Exception {
        ClientException(String message) {
            super(message);
        }
    }

    public abstract void connect(String hostName, int port) throws IOException;

    public abstract void disconnect() throws ClientNotConnectedException, IOException;

    public abstract void executeList(String path) throws ClientNotConnectedException, IOException;

    public abstract void executeGet(String path, String outputPath) throws ClientNotConnectedException, IOException;
}
