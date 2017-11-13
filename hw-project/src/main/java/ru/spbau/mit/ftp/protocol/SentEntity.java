package ru.spbau.mit.ftp.protocol;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public abstract class SentEntity {
    private boolean initialized = false;

    public abstract void write(WritableByteChannel out) throws IOException;

    public abstract void read(ReadableByteChannel in) throws IOException;

    public abstract String debugString();

    protected void setInitialized() {
        initialized = true;
    }

    protected void checkForEmptyness() {
        if (initialized) {
            throw new SentEntityException(this.getClass().getName() + " should be empty");
        }
    }

    protected void checkForNonEmptyness() {
        if (!initialized) {
            throw new SentEntityException(this.getClass().getName() + " should not be empty");
        }
    }

    static class SentEntityException extends RuntimeException {
        SentEntityException(String message) {
            super(message);
        }
    }
}

