package ru.spbau.mit.ftp.protocol;

public class SimpleRequest extends Request {
    private final String message;

    public SimpleRequest(String message) {
        this.message = message;
    }

    @Override
    public int code() {
        return 0;
    }

    @Override
    public String requestBody() {
        return message;
    }
}
