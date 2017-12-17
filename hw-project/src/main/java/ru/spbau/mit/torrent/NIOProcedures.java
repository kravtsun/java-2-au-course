package ru.spbau.mit.torrent;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import static ru.spbau.mit.torrent.Utils.iPFromInt;
import static ru.spbau.mit.torrent.Utils.intFromIP;

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

    static FileProxy readProxy(ReadableByteChannel in) throws IOException {
        int fileId = readInt(in);
        String filename = readString(in);
        long size = readLong(in);
        return new FileProxy(fileId, filename, size);
    }

    static void writeProxy(WritableByteChannel out, FileProxy fileProxy) throws IOException {
        writeInt(out, fileProxy.getId());
        writeString(out, fileProxy.getName());
        writeLong(out, fileProxy.getSize());
    }

    static InetSocketAddress readAddress(ReadableByteChannel in) throws IOException {
        byte[] ipBytes = iPFromInt(readInt(in));
        int port = readInt(in);
        return new InetSocketAddress(InetAddress.getByAddress(ipBytes), port);
    }

    static void writeAddress(WritableByteChannel out, InetSocketAddress address) throws IOException {
        writeInt(out, intFromIP(address.getAddress().getAddress()));
        writeInt(out, address.getPort());
    }

    private static void writeUntil(ByteBuffer buffer, WritableByteChannel out) throws IOException {
        if (buffer.position() != buffer.capacity()) {
            throw new NIOException("Buffer is not full.");
        }
        buffer.flip();
        while (buffer.hasRemaining()) {
            out.write(buffer);
        }
    }

    private static void readUntil(ByteBuffer buffer, ReadableByteChannel in) throws IOException {
        if (buffer.position() == buffer.limit()) {
            throw new NIOException("Buffer is full.");
        }
        while (buffer.position() != buffer.capacity()) {
            in.read(buffer);
        }
        buffer.flip();
    }

    public static class NIOException extends RuntimeException {
        NIOException(String message) {
            super(message);
        }
    }
}
