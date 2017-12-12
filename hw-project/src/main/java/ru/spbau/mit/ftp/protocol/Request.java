package ru.spbau.mit.ftp.protocol;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import static ru.spbau.mit.ftp.protocol.NIOProcedures.readInt;
import static ru.spbau.mit.ftp.protocol.NIOProcedures.writeInt;

public abstract class Request extends SentEntity {
    public static Request parse(ReadableByteChannel in) throws IOException {
        int code = readInt(in);
        Request request;
        if (code == RequestCode.ECHO.intValue) {
            request = new EchoRequest();
        } else if (code == RequestCode.LIST.intValue) {
            request = new ListRequest();
        } else if (code == RequestCode.GET.intValue) {
            request = new GetRequest();
        } else {
            throw new RequestException("Unknown code: " + code);
        }
        request.readOther(in);
        return request;
    }

    @Override
    public void write(WritableByteChannel out) throws IOException {
        checkForNonEmptyness();
        writeInt(out, code().intValue);
        writeOther(out);
    }

    protected abstract RequestCode code();

    protected abstract void writeOther(WritableByteChannel out) throws IOException;

    @Override
    public void read(ReadableByteChannel in) throws IOException {
        checkForEmptyness();
        int code = readInt(in);
        if (code != code().intValue) {
            throw new RequestException(String.format("Wrong code: %d, expected %d", code, code().intValue));
        }
        readOther(in);
    }

    protected abstract void readOther(ReadableByteChannel in) throws IOException;

    protected enum RequestCode {
        ECHO(0),
        LIST(1),
        GET(2);

        private final int intValue;

        RequestCode(int value) {
            this.intValue = value;
        }
    }

    public static class RequestException extends RuntimeException {
        RequestException(String message) {
            super(message);
        }
    }
}
