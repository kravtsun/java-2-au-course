package ru.spbau.mit.torrent;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import static ru.spbau.mit.torrent.NIOProcedures.writeInt;
import static ru.spbau.mit.torrent.NIOProcedures.writeUntil;
import static ru.spbau.mit.torrent.Utils.FILE_PART_SIZE;

class ClientSession extends AbstractClientSession {
    private final Client client;

    ClientSession(Client client, AsynchronousSocketChannel channel) {
        super(channel);
        this.client = client;
    }

    @Override
    public void proceedGet(int fileId, int partId) throws IOException, NIOException {
        FileProxy fileProxy = client.getFile(fileId);
        assert fileProxy != null;
        // TODO send length of part before the part entities themselves.
        // on empty fileProxy send 0.

        try (RandomAccessFile file = new RandomAccessFile(fileProxy.getName(), "r")) {
            long length = file.length();
            long start = partId * FILE_PART_SIZE;
            if (start > length) {
                throw new ClientException("part " + partId + " is not consistent with file length: " + length);
            }
            long finish = start + FILE_PART_SIZE;
            if (finish > length) {
                finish = length;
            }
            ByteBuffer buffer = file.getChannel().map(FileChannel.MapMode.READ_ONLY, start, finish);
            writeUntil(buffer, getChannel());
        }
    }

    @Override
    public void proceedStat(int fileId) throws NIOException {
        List<Integer> parts = client.getFileParts(fileId);
        writeInt(getChannel(), parts.size());
        for (Integer part: parts) {
            writeInt(getChannel(), part);
        }
    }
}
