package ru.spbau.mit.ftp.protocol;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import static ru.spbau.mit.ftp.protocol.NIOProcedures.readString;
import static ru.spbau.mit.ftp.protocol.NIOProcedures.writeString;

public abstract class PathRequest extends Request {
    private String path;

    PathRequest() {}

    PathRequest(String path) {
        this.path = path;
        setInitialized();
    }

    @Override
    protected void writeOther(WritableByteChannel out) throws IOException {
        writeString(out, path);
    }

    public String getPath() {
        return path;
    }

    @Override
    protected void readOther(ReadableByteChannel in) throws IOException {
        path = readString(in);
    }

    @Override
    public String debugString() {
        return path;
    }
}
