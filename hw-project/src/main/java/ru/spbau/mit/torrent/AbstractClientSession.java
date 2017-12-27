package ru.spbau.mit.torrent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.AsynchronousSocketChannel;

import static ru.spbau.mit.torrent.NIOProcedures.readInt;
import static ru.spbau.mit.torrent.Utils.*;

public abstract class AbstractClientSession implements Runnable, Closeable {
    private static final Logger LOGGER = LogManager.getLogger("clientSession");

    private final AsynchronousSocketChannel channel;

    protected AbstractClientSession(AsynchronousSocketChannel channel) {
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
                int requestType = readInt(channel);
                int fileId;
                int partId;
                LOGGER.info("Proceeding: " + COMMAND_LIST);
                switch (requestType) {
                    case CODE_STAT:
                        fileId = readInt(channel);
                        LOGGER.info("Proceeding: " + COMMAND_STAT);
                        proceedStat(fileId);
                        break;
                    case CODE_GET:
                        LOGGER.info("Proceeding: " + COMMAND_GET);
                        fileId = readInt(channel);
                        partId = readInt(channel);
                        proceedGet(fileId, partId);
                        break;
                    default:
                        throw new ClientException("Unknown type of request; " + requestType);
                }
            } catch (Exception e) {
                LOGGER.error("Error while proceeding request: " + e);
            }
        }
    }

    protected AsynchronousSocketChannel getChannel() {
        return channel;
    }

    abstract void proceedGet(int fileId, int partId) throws IOException, NIOException;

    abstract void proceedStat(int fileId) throws Exception;
}
