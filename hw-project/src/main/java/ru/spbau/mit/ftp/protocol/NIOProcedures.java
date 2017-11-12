package ru.spbau.mit.ftp.protocol;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

final class NIOProcedures {
    private static final int INTEGER_SIZE = 4;
    private static final int LONG_SIZE = 8;

    private NIOProcedures() {}

    static void writeString(WritableByteChannel out, String message) throws IOException {
        byte[] messageBytes = message.getBytes();
        writeInt(out, messageBytes.length);
        ByteBuffer buffer = ByteBuffer.allocate(messageBytes.length);
        buffer.put(messageBytes);
        writeUntil(buffer, out);
    }

    static String readString(ReadableByteChannel in) throws IOException {
        int stringLength = readInt(in);
        ByteBuffer buffer = ByteBuffer.allocate(stringLength);
        readUntil(buffer, in);
        return new String(buffer.array());
    }

    static void writeInt(WritableByteChannel out, int value) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(INTEGER_SIZE);
        buffer.putInt(value);
        writeUntil(buffer, out);
    }

    static int readInt(ReadableByteChannel in) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(INTEGER_SIZE);
        readUntil(buffer, in);
        return buffer.getInt();
    }

    static void writeLong(WritableByteChannel out, long value) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(LONG_SIZE);
        buffer.putLong(value);
        writeUntil(buffer, out);
    }

    static long readLong(ReadableByteChannel in) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(LONG_SIZE);
        readUntil(buffer, in);
        return buffer.getLong();
    }

    private static void writeUntil(ByteBuffer buffer, WritableByteChannel out) throws IOException {
        if (buffer.position() != buffer.capacity()) {
            throw new SentEntity.SentEntityException("Buffer is not full.");
        }
        buffer.flip();
        while (buffer.hasRemaining()) {
            out.write(buffer);
        }
    }

    private static void readUntil(ByteBuffer buffer, ReadableByteChannel in) throws IOException {
        if (buffer.position() == buffer.limit()) {
            throw new SentEntity.SentEntityException("Buffer is full.");
        }
        while (buffer.position() != buffer.capacity()) {
            in.read(buffer);
        }
        buffer.flip();
    }
}
