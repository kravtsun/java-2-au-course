package ru.spbau.mit.ftp.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class SimpleResponse extends Response {
    private String message;

    public SimpleResponse() {}

    public SimpleResponse(String message) {
        this.message = message;
        setInitialized();
    }

    @Override
    public void write(DataOutputStream out) throws IOException {
        checkForNonEmptyness();
        out.writeUTF(message);
    }

    @Override
    public void read(DataInputStream in) throws IOException {
        checkForEmptyness();
        message = in.readUTF();
    }

    @Override
    public String debugString() {
        return message;
    }
}
