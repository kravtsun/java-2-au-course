package ru.spbau.mit.torrent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.List;

import static ru.spbau.mit.torrent.NIOProcedures.*;
import static ru.spbau.mit.torrent.Utils.*;
import static ru.spbau.mit.torrent.Utils.COMMAND_SOURCES;
import static ru.spbau.mit.torrent.Utils.COMMAND_UPDATE;

class TrackerSession extends AbstractTrackerSession {
    private static final Logger LOGGER = LogManager.getLogger("TrackerSession");
    private final AbstractTracker tracker;
    private InetSocketAddress address;
    private long lastUpdated;
    private final Thread updateCheckerThread;

    TrackerSession(AbstractTracker tracker, AsynchronousSocketChannel channel) throws NIOException {
        super(channel);
        this.tracker = tracker;

        // protocol: on connecting to tracker client should firstly send update request.
        // currently there are no response from tracker on that greeting.
        int opCode = readInt(channel);
        if (opCode != CODE_UPDATE) {
            throw new TrackerException("update expected");
        }
        proceedUpdate();
        updateCheckerThread = new Thread(() -> {
            while (channel.isOpen()) {
                try {
                    synchronized (this) {
                        if (System.currentTimeMillis() - lastUpdated > UPDATE_TIMEOUT_LIMIT) {
                            channel.close();
                            break;
                        }
                        wait(UPDATE_TIMEOUT);
                    }
                } catch (InterruptedException e) {
                    LOGGER.debug("InterruptedException for update checker: " + e);
                    break;
                } catch (IOException e) {
                    LOGGER.warn("Error while trying to close channel: " + e);
                    break;
                }
            }
        });
        updateCheckerThread.setDaemon(true);
        updateCheckerThread.start();
    }

    @Override
    public synchronized void close() throws IOException {
        super.close();
        updateCheckerThread.interrupt();
    }

    @Override
    synchronized void proceedList() throws NIOException {
        LOGGER.info("Proceeding: " + COMMAND_LIST);
        List<FileProxy> files = tracker.list();
        writeInt(getChannel(), files.size());
        for (FileProxy file: files) {
            writeProxy(getChannel(), file);
        }
    }

    @Override
    synchronized void proceedUpload() throws NIOException {
        LOGGER.info("Proceeding: " + COMMAND_UPLOAD);
        String filename = readString(getChannel());
        long size = readLong(getChannel());
        int fileId = tracker.upload(address, filename, size);
        writeInt(getChannel(), fileId);
    }

    @Override
    synchronized void proceedUpdate() throws NIOException {
        LOGGER.info("Proceeding: " + COMMAND_UPDATE);
        address = readAddress(getChannel());
        int count = readInt(getChannel());
        int[] fileParts = new int[count];
        for (int i = 0; i < count; i++) {
            fileParts[i] = readInt(getChannel());
        }
        boolean updateStatus = tracker.update(address, fileParts);

        writeInt(getChannel(), updateStatus ? 1 : 0);
        if (updateStatus) {
            lastUpdated = System.currentTimeMillis();
        }
    }

    @Override
    synchronized void proceedSources() throws NIOException {
        LOGGER.info("Proceeding: " + COMMAND_SOURCES);
        int fileId = readInt(getChannel());
        List<InetSocketAddress> seeds = tracker.sources(fileId);
        writeInt(getChannel(), seeds.size());
        for (InetSocketAddress address: seeds) {
            writeAddress(getChannel(), address);
        }
    }
}
