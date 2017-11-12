package ru.spbau.mit.ftp.protocol;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public abstract class SentEntity {
    public static class SentEntityException extends RuntimeException {
        SentEntityException(String message) {
            super(message);
        }
    }

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

    // TODO Put this auxiliary procedures into separate class or/and namespace.
    protected static void writeString(WritableByteChannel out, String message) throws IOException {
        byte[] messageBytes = message.getBytes();
        writeInt(out, messageBytes.length);
        ByteBuffer buffer = ByteBuffer.allocate(messageBytes.length);
        buffer.put(messageBytes);
        writeUntil(buffer, out);
    }

    protected static String readString(ReadableByteChannel in) throws IOException {
        int stringLength = readInt(in);
        ByteBuffer buffer = ByteBuffer.allocate(stringLength);
        readUntil(buffer, in);
        return new String(buffer.array());
    }

    protected static void writeInt(WritableByteChannel out, int value) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(value);
        writeUntil(buffer, out);
    }

    protected static int readInt(ReadableByteChannel in) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        readUntil(buffer, in);
        return buffer.getInt();
    }

    protected static void writeLong(WritableByteChannel out, long value) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putLong(value);
        writeUntil(buffer, out);
    }

    protected static long readLong(ReadableByteChannel in) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        readUntil(buffer, in);
        return buffer.getLong();
    }

    private static void writeUntil(ByteBuffer buffer, WritableByteChannel out) throws IOException {
        if (buffer.position() != buffer.capacity()) {
            throw new SentEntityException("Buffer is not full.");
        }
        buffer.flip();
        while (buffer.hasRemaining()) {
            out.write(buffer);
        }
    }

    private static void readUntil(ByteBuffer buffer, ReadableByteChannel in) throws IOException {
        if (buffer.position() == buffer.limit()) {
            throw new SentEntityException("Buffer is full.");
        }
        while (buffer.position() != buffer.capacity()) {
            in.read(buffer);
        }
        buffer.flip();
    }
}

