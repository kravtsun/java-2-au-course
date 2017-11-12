package ru.spbau.mit.ftp;

import ru.spbau.mit.ftp.protocol.EchoResponse;
import ru.spbau.mit.ftp.protocol.GetResponse;
import ru.spbau.mit.ftp.protocol.ListResponse;

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

    public abstract EchoResponse connect(String hostName, int port) throws IOException;

    public abstract void disconnect() throws IOException;

    public abstract ListResponse executeList(String path) throws ClientNotConnectedException, IOException;

    public abstract GetResponse executeGet(String path, String outputPath) throws ClientNotConnectedException, IOException;
}
