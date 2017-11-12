package ru.spbau.mit.ftp.protocol;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public abstract class SentEntity {
    public static class SentEntityException extends RuntimeException {
        SentEntityException(String message) {
            super(message);
        }
    }

    private boolean initialized = false;

    public abstract void write(DataOutputStream out) throws IOException;

    public abstract void read(DataInputStream in) throws IOException;

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


}

