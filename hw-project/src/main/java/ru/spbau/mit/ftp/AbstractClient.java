package ru.spbau.mit.ftp;

import ru.spbau.mit.ftp.protocol.EchoResponse;
import ru.spbau.mit.ftp.protocol.GetResponse;
import ru.spbau.mit.ftp.protocol.ListResponse;

import java.io.IOException;

public abstract class AbstractClient {
    public abstract EchoResponse connect(String hostName, int port) throws IOException;

    public abstract void disconnect() throws IOException;

    public abstract ListResponse executeList(String path)
            throws ClientNotConnectedException, IOException;

    public abstract GetResponse executeGet(String path, String outputPath)
            throws ClientNotConnectedException, IOException;

    public abstract EchoResponse executeExit();

    public abstract EchoResponse executeEcho(String message)
            throws ClientNotConnectedException, IOException;

    static class ClientNotConnectedException extends Exception {
        ClientNotConnectedException() {
            super("not connected");
        }
    }
}
