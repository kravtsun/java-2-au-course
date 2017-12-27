package ru.spbau.mit.torrent;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.Channel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static ru.spbau.mit.torrent.Utils.iPFromInt;
import static ru.spbau.mit.torrent.Utils.intFromIP;

final class NIOProcedures {
    private static final int INTEGER_SIZE = 4;
    private static final int LONG_SIZE = 8;

    private NIOProcedures() {}

    static void writeString(Channel out, String message) throws NIOException {
        byte[] messageBytes = message.getBytes();
        writeInt(out, messageBytes.length);
        ByteBuffer buffer = ByteBuffer.allocate(messageBytes.length);
        buffer.put(messageBytes);
        writeUntil(buffer, out);
    }

    static String readString(Channel in) throws NIOException {
        int stringLength = readInt(in);
        ByteBuffer buffer = ByteBuffer.allocate(stringLength);
        readUntil(buffer, in);
        return new String(buffer.array());
    }

    static void writeInt(Channel out, int value) throws NIOException {
        ByteBuffer buffer = ByteBuffer.allocate(INTEGER_SIZE);
        buffer.putInt(value);
        writeUntil(buffer, out);
    }

    static int readInt(Channel in) throws NIOException {
        ByteBuffer buffer = ByteBuffer.allocate(INTEGER_SIZE);
        readUntil(buffer, in);
        return buffer.getInt();
    }

    static void writeLong(Channel out, long value) throws NIOException {
        ByteBuffer buffer = ByteBuffer.allocate(LONG_SIZE);
        buffer.putLong(value);
        writeUntil(buffer, out);
    }

    static long readLong(Channel in) throws NIOException {
        ByteBuffer buffer = ByteBuffer.allocate(LONG_SIZE);
        readUntil(buffer, in);
        return buffer.getLong();
    }

    static InetSocketAddress readAddress(Channel in) throws NIOException {
        byte[] ipBytes = iPFromInt(readInt(in));
        int port = readInt(in);
        InetAddress address;
        try {
            address = InetAddress.getByAddress(ipBytes);
        } catch (UnknownHostException e) {
            throw new NIOException("Failed to resolve host in address", e);
        }
        return new InetSocketAddress(address, port);
    }

    static void writeAddress(Channel out, InetSocketAddress address) throws NIOException {
        writeInt(out, intFromIP(address.getAddress().getAddress()));
        writeInt(out, address.getPort());
    }

    static FileProxy readProxy(Channel in) throws NIOException {
        int fileId = readInt(in);
        String filename = readString(in);
        long size = readLong(in);
        return new FileProxy(fileId, filename, size);
    }

    static void writeProxy(Channel out, FileProxy fileProxy) throws NIOException {
        writeInt(out, fileProxy.getId());
        writeString(out, fileProxy.getName());
        writeLong(out, fileProxy.getSize());
    }

    static void writeUntil(ByteBuffer buffer, Channel out) throws NIOException {
        if (buffer.position() != buffer.capacity()) {
            throw new NIOException("Buffer is not full.");
        }
        buffer.flip();
        // very blunt tool to avoid code duplication.
        if (out instanceof AsynchronousSocketChannel) {
            writeUntilAsync(buffer, (AsynchronousSocketChannel) out);
        } else if (out instanceof WritableByteChannel) {
            writeUntilBlocking(buffer, (WritableByteChannel) out);
        } else {
            throw new NIOException("Unknown type of channel for write");
        }
    }

    static void readUntil(ByteBuffer buffer, Channel in) throws NIOException {
        if (buffer.position() == buffer.limit()) {
            throw new NIOException("Buffer is full.");
        }
        // very blunt tool to avoid code duplication.
        if (in instanceof AsynchronousSocketChannel) {
            readUntilAsync(buffer, (AsynchronousSocketChannel) in);
        } else if (in instanceof ReadableByteChannel) {
            readUntilBlocking(buffer, (ReadableByteChannel) in);
        } else {
            throw new NIOException("Unknown type of channel for read");
        }
    }

    private static void readUntilBlocking(ByteBuffer buffer, ReadableByteChannel in) throws NIOException {
        try {
            while (buffer.position() != buffer.capacity()) {
                in.read(buffer);
            }
        } catch (IOException e) {
            throw new NIOException("Error while reading from buffer", e);
        }
        buffer.flip();
    }

    private static void readUntilAsync(ByteBuffer buffer, AsynchronousSocketChannel in) throws NIOException {
        Future future = in.read(buffer);
        try {
            future.get();
        } catch (InterruptedException | ExecutionException e) {
            Throwable throwable = e.getCause();
            throw new NIOException("Error while waiting for reading", throwable == null ? e : throwable);
        }
        buffer.flip();
    }

    private static void writeUntilAsync(ByteBuffer buffer, AsynchronousSocketChannel out) throws NIOException {
        Future future = out.write(buffer);
        try {
            future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new NIOException("Error while waiting for writing", e);
        }
    }

    private static void writeUntilBlocking(ByteBuffer buffer, WritableByteChannel out) throws NIOException {
        try {
            while (buffer.hasRemaining()) {
                out.write(buffer);
            }
        } catch (IOException e) {
            throw new NIOException("Error while writing to buffer", e);
        }
    }



}
