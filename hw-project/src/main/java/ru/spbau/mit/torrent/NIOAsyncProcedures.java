package ru.spbau.mit.torrent;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.Future;

import static ru.spbau.mit.torrent.Utils.iPFromInt;
import static ru.spbau.mit.torrent.Utils.intFromIP;

final class NIOAsyncProcedures {
    private static final int INTEGER_SIZE = 4;
    private static final int LONG_SIZE = 8;

    private NIOAsyncProcedures() {}

    static void writeString(AsynchronousSocketChannel out, String message) throws Exception {
        byte[] messageBytes = message.getBytes();
        writeInt(out, messageBytes.length);
        ByteBuffer buffer = ByteBuffer.allocate(messageBytes.length);
        buffer.put(messageBytes);
        writeUntil(buffer, out);
    }

    static String readString(AsynchronousSocketChannel in) throws Exception {
        int stringLength = readInt(in);
        ByteBuffer buffer = ByteBuffer.allocate(stringLength);
        readUntil(buffer, in);
        return new String(buffer.array());
    }

    static void writeInt(AsynchronousSocketChannel out, int value) throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(INTEGER_SIZE);
        buffer.putInt(value);
        writeUntil(buffer, out);
    }

    static int readInt(AsynchronousSocketChannel in) throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(INTEGER_SIZE);
        readUntil(buffer, in);
        return buffer.getInt();
    }

    static void writeLong(AsynchronousSocketChannel out, long value) throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(LONG_SIZE);
        buffer.putLong(value);
        writeUntil(buffer, out);
    }

    static long readLong(AsynchronousSocketChannel in) throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(LONG_SIZE);
        readUntil(buffer, in);
        return buffer.getLong();
    }

    static InetSocketAddress readAddress(AsynchronousSocketChannel in) throws Exception {
        byte[] ipBytes = iPFromInt(readInt(in));
        int port = readInt(in);
        return new InetSocketAddress(InetAddress.getByAddress(ipBytes), port);
    }

    static void writeAddress(AsynchronousSocketChannel out, InetSocketAddress address) throws Exception {
        writeInt(out, intFromIP(address.getAddress().getAddress()));
        writeInt(out, address.getPort());
    }

    static FileProxy readProxy(AsynchronousSocketChannel in) throws Exception {
        int fileId = readInt(in);
        String filename = readString(in);
        long size = readLong(in);
        return new FileProxy(fileId, filename, size);
    }

    static void writeProxy(AsynchronousSocketChannel out, FileProxy fileProxy) throws Exception {
        writeInt(out, fileProxy.getId());
        writeString(out, fileProxy.getName());
        writeLong(out, fileProxy.getSize());
    }

    static void writeUntil(ByteBuffer buffer, AsynchronousSocketChannel out) throws Exception {
        if (buffer.position() != buffer.capacity()) {
            throw new NIOException("Buffer is not full.");
        }
        buffer.flip();
        Future future = out.write(buffer);
        future.get();
    }

    static void readUntil(ByteBuffer buffer, AsynchronousSocketChannel in) throws Exception {
        if (buffer.position() == buffer.limit()) {
            throw new NIOException("Buffer is full.");
        }
        Future future = in.read(buffer);
        future.get();
        buffer.flip();
    }

    public static class NIOException extends RuntimeException {
        NIOException(String message) {
            super(message);
        }
    }
}
