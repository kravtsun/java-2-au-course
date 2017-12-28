package ru.spbau.mit.torrent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class Server implements Closeable {
    private static final Logger LOGGER = LogManager.getLogger("Server");
    private AsynchronousServerSocketChannel servingChannel;
    private final List<AsynchronousSocketChannel> channels = new ArrayList<>();
    private final AsynchronousChannelGroup group;

    Server() throws IOException {
        ExecutorService trackerPool = Executors.newCachedThreadPool();
        group = AsynchronousChannelGroup.withCachedThreadPool(trackerPool, 4);
        servingChannel = AsynchronousServerSocketChannel.open(group);
    }

    InetSocketAddress getAddress() throws IOException {
        if (servingChannel.isOpen()) {
            return (InetSocketAddress) servingChannel.getLocalAddress();
        } else {
            return null;
        }
    }

    public void bind(InetSocketAddress listeningAddress) throws IOException {
        servingChannel.bind(listeningAddress);
        LOGGER.info("Bound to address: " + listeningAddress);

        servingChannel.accept(null,
                new CompletionHandler<AsynchronousSocketChannel, Object>() {
                    public void completed(AsynchronousSocketChannel channel, Object attachment) {
                        System.out.println("Accepted a connection");

                        // accept the next connection
                        servingChannel.accept(null, this);

                        synchronized (this) {
                            channels.add(channel);
                        }

                        serve(channel);
                    }

                    public void failed(Throwable throwable, Object att) {
                        if (throwable.getClass() == AsynchronousCloseException.class) {
                            LOGGER.info("Exiting on AsynchronousCloseException");
                        } else {
                            System.out.println("Failed to accept connection: " + throwable);
                        }
                    }
                }
            );
    }

    abstract void serve(AsynchronousSocketChannel channel);

    @Override
    public void close() throws IOException {
        for (AsynchronousSocketChannel channel: channels) {
            channel.close();
        }
        servingChannel.close();
        group.shutdown();
    }
}
