package ru.spbau.mit.ftp.protocol;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import static ru.spbau.mit.ftp.protocol.NIOProcedures.readLong;
import static ru.spbau.mit.ftp.protocol.NIOProcedures.writeLong;

public final class GetResponse extends Response {
    private final String path;

    private GetResponse(String path) {
        this.path = path;
    }

    public static GetResponse serverGetResponse(String sourcePath) {
        GetResponse response = new GetResponse(sourcePath);
        response.setInitialized();
        return response;
    }

    public static GetResponse clientGetResponse(String outputPath) {
        return new GetResponse(outputPath);
    }

    @Override
    public void write(WritableByteChannel out) throws IOException {
        checkForNonEmptyness();
        File sourceFile = new File(path);
        if (!sourceFile.exists() || sourceFile.isDirectory()) {
            writeLong(out, 0);
        } else {
            try (FileChannel sourceChannel = new FileInputStream(sourceFile).getChannel()) {
                writeLong(out, sourceChannel.size());
                sourceChannel.transferTo(0, sourceChannel.size(), out);
            }
        }
    }

    @Override
    public void read(ReadableByteChannel in) throws IOException {
        try (FileChannel outputChannel = new FileOutputStream(new File(path)).getChannel()) {
            long size = readLong(in);
            long written = outputChannel.transferFrom(in, 0, size);
            if (written != size) {
                throw new SentEntityException("expected to write into file in one call..");
            }
            outputChannel.force(true);
        }
    }

    @Override
    public String debugString() {
        return path;
    }
}
