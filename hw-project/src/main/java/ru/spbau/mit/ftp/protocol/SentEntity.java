package ru.spbau.mit.ftp.protocol;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
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

    // Put this auxiliary procedures into separate class or/and namespace.
    public static void writeString(WritableByteChannel out, String message) throws IOException {
        byte[] messageBytes = message.getBytes();
        writeInt(out, messageBytes.length);
        ByteBuffer buffer = ByteBuffer.allocate(messageBytes.length);
        buffer.put(messageBytes);
        buffer.flip();
        writeUntil(buffer, out);
    }

    public static String readString(ReadableByteChannel in) throws IOException {
        int stringLength = readInt(in);
        ByteBuffer buffer = ByteBuffer.allocate(stringLength);
        readUntil(buffer, in);
        buffer.flip();
        return new String(buffer.array());
    }

    public static void writeInt(WritableByteChannel out, int value) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(value);
        buffer.flip();
        writeUntil(buffer, out);
    }

    public static int readInt(ReadableByteChannel in) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        readUntil(buffer, in);
        buffer.flip();
        return buffer.getInt();
    }

    public static void writeLong(WritableByteChannel out, long value) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putLong(value);
        buffer.flip();
        writeUntil(buffer, out);
    }

    public static long readLong(ReadableByteChannel in) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        readUntil(buffer, in);
        buffer.flip();
        return buffer.getLong();
    }

    private static void writeUntil(ByteBuffer buffer, WritableByteChannel out) throws IOException {
//        assert buffer.position() == 0
        while (buffer.position() != buffer.limit()) {
            out.write(buffer);
        }
    }

    private static void readUntil(ByteBuffer buffer, ReadableByteChannel in) throws IOException {
//        assert buffer.position() == 0
        while (buffer.position() != buffer.capacity()) {
            in.read(buffer);
        }
    }
}

