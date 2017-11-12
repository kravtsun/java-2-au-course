package ru.spbau.mit.ftp.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public class EchoResponse extends Response {
    private String message;

    public EchoResponse() {}

    public EchoResponse(String message) {
        this.message = message;
        setInitialized();
    }

    @Override
    public void write(WritableByteChannel out) throws IOException {
        checkForNonEmptyness();
        writeString(out, message);
    }

    @Override
    public void read(ReadableByteChannel in) throws IOException {
        checkForEmptyness();
        message = readString(in);
    }

    @Override
    public String debugString() {
        return message;
    }
}
