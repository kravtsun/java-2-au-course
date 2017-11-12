package ru.spbau.mit.ftp.protocol;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import static ru.spbau.mit.ftp.protocol.NIOProcedures.readString;
import static ru.spbau.mit.ftp.protocol.NIOProcedures.writeString;

public class EchoResponse extends Response {
    public static final EchoResponse INIT_RESPONSE = new EchoResponse("hello");
    public static final EchoResponse EXIT_RESPONSE = new EchoResponse("bye");

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

    @Override
    public boolean equals(Object o) {
        return o instanceof EchoResponse && ((EchoResponse) o).message.equals(message);
    }

    @Override
    public int hashCode() {
        return message.hashCode();
    }
}
