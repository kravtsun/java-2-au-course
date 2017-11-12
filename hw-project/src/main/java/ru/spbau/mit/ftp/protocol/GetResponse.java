package ru.spbau.mit.ftp.protocol;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public class GetResponse extends Response {
    private final FileChannel channel;
    private final String filename;

    public GetResponse(File file) throws FileNotFoundException {
        filename = file.getName();
        this.channel = new RandomAccessFile(file, "r").getChannel();
        setInitialized();
    }

    @Override
    public void write(WritableByteChannel out) throws IOException {
        checkForNonEmptyness();
        writeLong(out, channel.size());
        channel.transferTo(0, channel.size(), out);
    }

    @Override
    public void read(ReadableByteChannel in) throws IOException {
        throw new Request.RequestException("read operation is not allowed on GET response: " +
                "file size can be really big...");
//        checkForEmptyness();
//        long size = readLong(in);
//        ByteBuffer buffer = ByteBuffer.allocate((int)size);
//        in.read(buffer);
//        buffer.flip();
//        bytes = buffer.array();
    }

    @Override
    public String debugString() {
        return filename;
    }
}
