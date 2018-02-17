package ru.spbau.mit.torrent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.FileChannel;
import java.util.List;

import static ru.spbau.mit.torrent.NIOProcedures.writeInt;
import static ru.spbau.mit.torrent.NIOProcedures.writeLong;
import static ru.spbau.mit.torrent.NIOProcedures.writeUntil;
import static ru.spbau.mit.torrent.Utils.*;

class ClientSession extends AbstractClientSession {
    private static final Logger LOGGER = LogManager.getLogger("ClientSession");
    private final Client client;

    ClientSession(Client client, AsynchronousSocketChannel channel) {
        super(channel);
        this.client = client;
    }

    @Override
    public void proceedGet(int fileId, int partId) throws NIOException {
        LOGGER.info("Proceeding: " + COMMAND_GET + " fileId: " + fileId + ", partId: " + partId);
        FileProxy fileProxy = client.getFile(fileId);
        File realFile = client.fileFromProxy(fileProxy);
        try (RandomAccessFile file = new RandomAccessFile(realFile, "r")) {
            long length = file.length();
            long start = partId * FILE_PART_SIZE;
            if (start > length) {
                String message = "part " + partId + " is not consistent with file length: " + length;
                throw new ClientException(message);
            }
            long finish = start + FILE_PART_SIZE;
            if (finish > length) {
                finish = length;
            }
            long size = finish - start;
            ByteBuffer buffer = file.getChannel().map(FileChannel.MapMode.READ_ONLY, start, size);
            LOGGER.debug("send size: " + size);
            writeLong(getChannel(), size);
            writeUntil(buffer, getChannel());
        } catch (IOException e) {
            // if file does not exist or we can't upload its part.
            writeLong(getChannel(), 0);
        }
    }

    @Override
    public void proceedStat(int fileId) throws NIOException {
        LOGGER.info("Proceeding: " + COMMAND_STAT);
        List<Integer> parts = client.getFileParts(fileId);
        writeInt(getChannel(), parts.size());
        for (Integer part: parts) {
            writeInt(getChannel(), part);
        }
    }
}
