package ru.spbau.mit.torrent;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class Tracker extends AbstractTracker {
    private static final Logger LOGGER = LogManager.getLogger("trackerApp");
    private final InetSocketAddress listeningAddress;

    public Tracker(InetSocketAddress listeningAddress) {
        this.listeningAddress = listeningAddress;
    }

    public static void main(String[] args) {
        CommandLineParser parser = new DefaultParser();
        Options options = new Options();

        // TODO final variables for string constants (used twice in here, whatever).
        options.addRequiredOption(null, "thost", true, "Tracker's host address");
        options.addRequiredOption(null, "tport", true, "Tracker's port to start knocking");
        options.addOption(null, "config", true, "Where to store/load config");

        Client client;
        InetSocketAddress trackerAddress;

        String configFilename;

        try {
            LOGGER.info("Parsing options: " + options);
            CommandLine commandLine = parser.parse(options, args);
            trackerAddress = Utils.addressFromCommandLine(commandLine, "thost", "tport");

            configFilename = "tracker_" + trackerAddress.getPort() + ".config";
            configFilename = commandLine.getOptionValue("config", configFilename);
        } catch (Exception e) {
            LOGGER.error("Failed to parse: " + e);
            return;
        }

        AsynchronousServerSocketChannel servingChannel;
        AsynchronousChannelGroup group = null;
        ExecutorService trackerPool = Executors.newCachedThreadPool();
        try {
            group = AsynchronousChannelGroup.withThreadPool(trackerPool);
            servingChannel = AsynchronousServerSocketChannel.open(group);
            servingChannel.bind(trackerAddress);
            LOGGER.info("Tracker bound to address: " + trackerAddress);

            servingChannel.accept(null,
                    new CompletionHandler<AsynchronousSocketChannel, Object>() {
                        public void completed(AsynchronousSocketChannel ch, Object attachment) {
                            System.out.println("Accepted a connection");

                            // accept the next connection
                            servingChannel.accept(null, this);

                            while (ch.isOpen()) {
                                // TODO serving this connection.
                                ByteBuffer buffer = ByteBuffer.allocate(32);
                                Future result = ch.read(buffer);
                                while (!result.isDone()) {
                                    // do nothing
                                }

                                buffer.flip();
                                String message = new String(buffer.array()).trim();
                                System.out.println(message);
                            }
                        }

                        public void failed(Throwable exc, Object att) {
                            System.out.println("Failed to accept connection");
                        }
                    });
//            Tracker tracker = new Tracker(trackerAddress);
            group.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOGGER.error("Error while waiting for channel group to terminate: " + e);
        } catch (Exception e) {
            LOGGER.error("Error while trying to setup tracker: ", e);
            e.printStackTrace();
        }

//        final Scanner scanner = new Scanner(System.in);

    }

    @Override
    public List<FileProxy> list() {
        return null;
    }

    @Override
    public int upload(String filename, long size) throws Exception {
        return 0;
    }

    @Override
    public List<InetSocketAddress> sources(int fileId) {
        return null;
    }

    @Override
    public boolean update(int clientPort, List<Integer> fileIds) {
        return false;
    }
}
