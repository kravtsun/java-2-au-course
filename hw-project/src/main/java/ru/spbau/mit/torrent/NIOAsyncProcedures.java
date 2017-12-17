package ru.spbau.mit.torrent;

import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.Future;

import static ru.spbau.mit.torrent.Utils.IPFromInt;
import static ru.spbau.mit.torrent.Utils.intFromIP;

final class NIOAsyncProcedures {
    private static final int INTEGER_SIZE = 4;
    private static final int LONG_SIZE = 8;

    private NIOAsyncProcedures() {}

    static void writeString(AsynchronousSocketChannel out, String message) throws IOException {
        byte[] messageBytes = message.getBytes();
        writeInt(out, messageBytes.length);
        ByteBuffer buffer = ByteBuffer.allocate(messageBytes.length);
        buffer.put(messageBytes);
        writeUntil(buffer, out);
    }

    static String readString(AsynchronousSocketChannel in) throws IOException {
        int stringLength = readInt(in);
        ByteBuffer buffer = ByteBuffer.allocate(stringLength);
        readUntil(buffer, in);
        return new String(buffer.array());
    }

    static void writeInt(AsynchronousSocketChannel out, int value) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(INTEGER_SIZE);
        buffer.putInt(value);
        writeUntil(buffer, out);
    }

    static int readInt(AsynchronousSocketChannel in) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(INTEGER_SIZE);
        readUntil(buffer, in);
        return buffer.getInt();
    }

    static void writeLong(AsynchronousSocketChannel out, long value) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(LONG_SIZE);
        buffer.putLong(value);
        writeUntil(buffer, out);
    }

    static long readLong(AsynchronousSocketChannel in) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(LONG_SIZE);
        readUntil(buffer, in);
        return buffer.getLong();
    }

    static InetSocketAddress readAddress(AsynchronousSocketChannel in) throws IOException {
        byte[] ipBytes = IPFromInt(readInt(in));
        int port = readInt(in);
        return new InetSocketAddress(InetAddress.getByAddress(ipBytes), port);
    }

    static void writeAddress(AsynchronousSocketChannel out, InetSocketAddress address) throws IOException {
        writeInt(out, intFromIP(address.getAddress().getAddress()));
        writeInt(out, address.getPort());
    }

    static FileProxy readProxy(AsynchronousSocketChannel in) throws IOException {
        int fileId = readInt(in);
        String filename = readString(in);
        long size = readLong(in);
        return new FileProxy(fileId, filename, size);
    }

    static void writeProxy(AsynchronousSocketChannel out, FileProxy fileProxy) throws IOException {
        writeInt(out, fileProxy.getId());
        writeString(out, fileProxy.getName());
        writeLong(out, fileProxy.getSize());
    }

    static void writeUntil(ByteBuffer buffer, AsynchronousSocketChannel out) throws IOException {
        if (buffer.position() != buffer.capacity()) {
            throw new NIOException("Buffer is not full.");
        }
        buffer.flip();
        Future future = out.write(buffer);
        while (!future.isDone()) {
            out.write(buffer);
        }
    }

    static void readUntil(ByteBuffer buffer, AsynchronousSocketChannel in) throws IOException {
        if (buffer.position() == buffer.limit()) {
            throw new NIOException("Buffer is full.");
        }
        Future future = in.read(buffer);
        while (!future.isDone()) {
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
