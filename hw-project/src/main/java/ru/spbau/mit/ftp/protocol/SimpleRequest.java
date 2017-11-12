package ru.spbau.mit.ftp.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

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
    protected void writeOther(DataOutputStream out) throws IOException {
        out.writeUTF(message);
    }

    @Override
    protected void readOther(DataInputStream in) throws IOException {
        message = in.readUTF();
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String debugString() {
        return message;
    }
}
