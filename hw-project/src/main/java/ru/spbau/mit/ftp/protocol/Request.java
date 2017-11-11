package ru.spbau.mit.ftp.protocol;

public abstract class Request implements SentEntity {

    @Override
    public String str() {
        return code() + " " + requestBody();
    }

    protected abstract int code();

    protected abstract String requestBody();
}
