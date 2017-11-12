package ru.spbau.mit.ftp.protocol;

public class GetRequest extends PathRequest {
    public GetRequest(String path) {
        super(path);
    }

    public GetRequest() {}

    @Override
    protected RequestCode code() {
        return RequestCode.GET;
    }
}
