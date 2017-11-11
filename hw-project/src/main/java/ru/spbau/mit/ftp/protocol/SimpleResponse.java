package ru.spbau.mit.ftp.protocol;

public class SimpleResponse implements SentEntity {
    private final String message;

    public SimpleResponse(String message) {
        this.message = message;
    }

    @Override
    public String str() {
        return message;
    }
}
