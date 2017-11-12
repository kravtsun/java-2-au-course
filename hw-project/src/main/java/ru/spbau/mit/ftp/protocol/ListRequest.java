package ru.spbau.mit.ftp.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class ListRequest extends Request {
    private String path;

    public ListRequest() {}

    public ListRequest(String path) {
        this.path = path;
        setInitialized();
    }

    @Override
    public RequestCode code() {
        return RequestCode.LIST;
    }

    @Override
    protected void writeOther(DataOutputStream out) throws IOException {
        out.writeUTF(path);
    }

    public String getPath() {
        return path;
    }

    @Override
    protected void readOther(DataInputStream in) throws IOException {
        path = in.readUTF();
    }

    @Override
    public String debugString() {
        return path;
    }
}
