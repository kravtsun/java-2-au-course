package ru.spbau.mit.torrent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.channels.AsynchronousSocketChannel;

import static ru.spbau.mit.torrent.NIOAsyncProcedures.readInt;
import static ru.spbau.mit.torrent.Utils.*;

public abstract class AbstractTrackerSession implements Closeable, Runnable {
    private static final Logger LOGGER = LogManager.getLogger("trackerApp");
    private final AsynchronousSocketChannel channel;

    AbstractTrackerSession(AsynchronousSocketChannel channel) {
        this.channel = channel;
    }

    @Override
    public void close() throws IOException {
        try {
            channel.close();
        } catch (IOException e) {
            LOGGER.error("Error while trying to close socket from client: " + e);
            throw e;
        }
    }

    @Override
    public void run() {
        while (channel.isOpen()) {
            try {
                int requestType;
                try {
                    requestType = readInt(channel);
                } catch (BufferUnderflowException e) {
                    LOGGER.warn("Channel seems to be closed.");
                    return;
                }
                switch (requestType) {
                    case CODE_LIST:
                        proceedList();
                        break;
                    case CODE_UPLOAD:
                        proceedUpload();
                        break;
                    case CODE_SOURCES:
                        proceedSources();
                        break;
                    case CODE_UPDATE:
                        proceedUpdate();
                        break;
                    default:
                        throw new TrackerException("Unknown type of request; " + requestType);

                }
            } catch (Exception e) {
                LOGGER.error("Error while proceeding request: " + e);
            }
        }
    }

    protected AsynchronousSocketChannel getChannel() {
        return channel;
    }

    abstract void proceedList() throws Exception;

    abstract void proceedUpload() throws Exception;

    abstract void proceedUpdate() throws Exception;

    abstract void proceedSources() throws Exception;
}
