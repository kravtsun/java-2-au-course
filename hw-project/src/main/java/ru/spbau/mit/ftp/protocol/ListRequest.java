package ru.spbau.mit.ftp.protocol;

public class ListRequest extends Request {
    private final String path;

    public ListRequest(String path) {
        this.path = path;
    }

    @Override
    public int code() {
        return 1;
    }

    @Override
    public String requestBody() {
        return getPath();
    }

    public String getPath() {
        return path;
    }
}
