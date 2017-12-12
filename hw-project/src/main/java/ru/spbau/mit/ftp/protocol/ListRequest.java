package ru.spbau.mit.ftp.protocol;

public class ListRequest extends PathRequest {
    public ListRequest() {}

    public ListRequest(String path) {
        super(path);
    }

    @Override
    protected RequestCode code() {
        return RequestCode.LIST;
    }
}
