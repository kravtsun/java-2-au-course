package ru.spbau.mit.ftp.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public class SimpleRequest extends Request {
    public static final String EXIT_MESSAGE = "exit";

    private String message;

    public SimpleRequest() {}

    public SimpleRequest(String message) {
        this.message = message;
        setInitialized();
    }

    @Override
    public RequestCode code() {
        return RequestCode.SIMPLE;
    }

    @Override
    protected void writeOther(WritableByteChannel out) throws IOException {
        writeString(out, message);
    }

    @Override
    protected void readOther(ReadableByteChannel in) throws IOException {
        message = readString(in);
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String debugString() {
        return message;
    }
}
