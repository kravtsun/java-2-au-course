package ru.spbau.mit.ftp.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public abstract class Request extends SentEntity {
    public static Request parse(DataInputStream in) throws IOException {
        int code = in.readInt();
        Request request;
        if (code == RequestCode.SIMPLE.intValue) {
            request = new SimpleRequest();
        } else if (code == RequestCode.LIST.intValue) {
            request = new ListRequest();
        } else {
            throw new RequestException("Unknown code: " + code);
        }
        request.readOther(in);
        return request;
    }

    public static class RequestException extends RuntimeException {
        RequestException(String message) {
            super(message);
        }
    }

    protected enum RequestCode {
        SIMPLE(0),
        LIST(1),
        GET(2);

        private final int intValue;

        RequestCode(int value) {
            this.intValue = value;
        }
    }

    @Override
    public void write(DataOutputStream out) throws IOException {
        checkForNonEmptyness();
        out.writeInt(code().intValue);
        writeOther(out);
    }

    protected abstract RequestCode code();

    protected abstract void writeOther(DataOutputStream out) throws IOException;

    @Override
    public void read(DataInputStream in) throws IOException {
        checkForEmptyness();
        int code = in.readInt();
        if (code != code().intValue) {
            throw new RequestException(String.format("Wrong code: %d, expected %d", code, code()));
        }
        readOther(in);
    }

    protected abstract void readOther(DataInputStream in) throws IOException;
}
