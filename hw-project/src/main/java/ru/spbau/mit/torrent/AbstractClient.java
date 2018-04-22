package ru.spbau.mit.torrent;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

public interface AbstractClient {
//    Server.bind() should be called before connecting to tracker.
    void connectToTracker(InetSocketAddress trackerAddress) throws ClientException, NIOException;

    List<FileProxy> executeList() throws NIOException;

    List<InetSocketAddress> executeSources(int fileId) throws NIOException;

    boolean executeUpdate() throws NIOException;

    // Client-specific requests.
    void connect(InetSocketAddress otherClientAddress) throws IOException;

    boolean isConnected();

    void disconnect() throws IOException;

    int executeUpload(String filename) throws NIOException;

    List<Integer> executeStat(int fileId) throws NIOException;

    /**
     * @param fileId file id
     * @param part part number
     * @param filename where to save file part. Can be null or empty, then this Client's fileProxy is used.
     * @throws IOException on problems with opening file for writing.
     * @throws NIOException on problems with network or protocol.
     */
    void executeGet(int fileId, int part, String filename) throws IOException, NIOException;

}
