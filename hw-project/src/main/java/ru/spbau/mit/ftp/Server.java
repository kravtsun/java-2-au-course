package ru.spbau.mit.ftp;

import org.apache.commons.cli.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.spbau.mit.ftp.protocol.*;

public class Server extends AbstractServer {
    private static final Logger LOGGER = LogManager.getLogger("server");
    private final int nthreads;
    private ExecutorService executorService;
    private ServerSocketChannel serverSocketChannel;
    private List<SocketChannel> sockets = new ArrayList<>();

    public Server(int nthreads) {
        this.nthreads = nthreads;
    }

    public static void main(String[] args) {
        CommandLineParser parser = new DefaultParser();
        Options options = new Options();
        options.addRequiredOption(null, "port", true, "Port to start listening at");
        options.addOption("j", "threads", true, "number of worker threads to run");
        options.addOption("h", "host", true, "hostName to bind to (should be available)");
        String hostName;
        int portNumber;
        int nthreads;
        try {
            LOGGER.debug("Parsing options: " + options);
            CommandLine commandLine = parser.parse(options, args);
            hostName = commandLine.getOptionValue("host", "localhost");
            String portString = commandLine.getOptionValue("port");
            portNumber = Integer.parseInt(portString);
            String nthreadsString = commandLine.getOptionValue("threads", "5");
            nthreads = Integer.parseInt(nthreadsString);
        } catch (Throwable t) {
            LOGGER.error("Failed to parse arguments: " + t);
            return;
        }

        try (Server server = new Server(nthreads)) {
            server.start(hostName, portNumber);
            Scanner scanner = new Scanner(System.in);
            while (true) {
                String command = scanner.nextLine();
                LOGGER.debug("server command: " + command);
                if (command.equals(EchoRequest.EXIT_MESSAGE)) {
                    break;
                }
            }
        } catch (Exception e) {
            LOGGER.error(e);
        }
    }

    @Override
    public synchronized void start(String hostName, int port) throws IOException, ServerException {

        if (serverSocketChannel != null && serverSocketChannel.isOpen()) {
            throw new ServerException("server already running");
        }
        try {
            executorService = Executors.newFixedThreadPool(nthreads);
        } catch (IllegalArgumentException e) {
            throw new ServerException(e);
        }

        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(hostName, port));
        LOGGER.info("Starting socket for " + serverSocketChannel.getLocalAddress());
        final boolean[] isStarted = {false};
        new Thread(() -> {
            try {
                isStarted[0] = true;
                while (true) {
                    if (!serverSocketChannel.isOpen()) {
                        break;
                    }
                    SocketChannel socketChannel = serverSocketChannel.accept();
                    if (socketChannel != null) {
                        executorService.submit(new ServerSession(socketChannel));
                        synchronized (this) {
                            sockets.add(socketChannel);
                        }
                    }
                }
            } catch (AsynchronousCloseException ignored) {
                LOGGER.info("closing serverSocketChannel by stop() request");
            } catch (Exception e) {
                LOGGER.error(e);
            }
        }).start();
        while (!isStarted[0]) {
            Thread.yield();
        }
    }

    @Override
    public synchronized void stop() throws IOException {
        LOGGER.debug("entering stop");
        if (serverSocketChannel != null) {
            serverSocketChannel.close();
            executorService.shutdown();
            synchronized (this) {
                for (SocketChannel socketChannel : sockets) {
                    socketChannel.close();
                }
                sockets.clear();
            }
            while (!executorService.isTerminated()) {
                try {
                    executorService.awaitTermination(1, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    break;
                }
            }
        }
        LOGGER.debug("exiting stop");
    }

    @Override
    public void close() throws IOException {
        stop();
    }
}
