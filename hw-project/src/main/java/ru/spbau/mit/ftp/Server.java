package ru.spbau.mit.ftp;

import org.apache.commons.cli.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.spbau.mit.ftp.protocol.*;

public class Server extends AbstractServer {
    private static final Logger LOGGER = LogManager.getLogger("server");
    private final ExecutorService executorService;
    private final List<SocketChannel> sockets = new ArrayList<>();
    private ServerSocketChannel serverSocketChannel;

    public Server(int nthreads) throws ServerException {
        try {
            executorService = Executors.newFixedThreadPool(nthreads);
        } catch (Exception e) {
            throw new ServerException(e);
        }
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

        try (AbstractServer server = new Server(nthreads)) {
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
                        sockets.add(socketChannel);
                        executorService.submit(new FTPServerSession(socketChannel));
                    }
                }
            } catch (AsynchronousCloseException ignored) {
                LOGGER.info("closing serverSocketChannel by stop() request");
            } catch (Exception e) {
                LOGGER.error(e);
                try {
                    stop();
                } catch (IOException e1) {
                    throw new RuntimeException(e1);
                }
            }
        }).start();
        while (!isStarted[0]) {
            Thread.yield();
        }
    }

    @Override
    public synchronized void stop() throws IOException {
        if (serverSocketChannel != null) {
            for (SocketChannel socket : sockets) {
                socket.close();
            }
            sockets.clear();
            serverSocketChannel.close();
        }
    }

    @Override
    public void close() throws IOException {
        stop();
    }

    private static class FTPServerSession implements Runnable {
        private static final Logger LOGGER = LogManager.getLogger("session");
        private static final AtomicInteger SESSIONS_COUNT = new AtomicInteger(0);
        private final SocketChannel socketChannel;
        private final int sessionId;

        FTPServerSession(SocketChannel socketChannel) {
            this.socketChannel = socketChannel;
            this.sessionId = SESSIONS_COUNT.incrementAndGet();
        }

        @Override
        public void run() {
            try {
                EchoResponse.INIT_RESPONSE.write(socketChannel);
                LOGGER.info(logMessage("starting IO loop"));
                while (true) {
                    SentEntity response;
                    boolean receivedExitMessage = false;
                    Request request = Request.parse(socketChannel);
                    LOGGER.info("received: " + request + ": " + request.debugString());
                    if (request instanceof EchoRequest) {
                        String receivedMessage = ((EchoRequest) request).getMessage();
                        if (receivedMessage.equals(EchoRequest.EXIT_MESSAGE)) {
                            receivedExitMessage = true;
                            response = EchoResponse.EXIT_RESPONSE;
                        } else {
                            response = new EchoResponse(receivedMessage);
                        }
                    } else if (request instanceof ListRequest) {
                        String path = ((ListRequest) request).getPath();
                        File[] files = new File(path).listFiles();
                        response = new ListResponse(files);
                    } else if (request instanceof GetRequest) {
                        String path = ((GetRequest) request).getPath();
                        File file = new File(path);
                        if (!file.exists() || file.isDirectory()) {
                            throw new ServerException("file " + path + " is not regular");
                        }
                        response = GetResponse.serverGetResponse(path);
                    } else {
                        throw new ServerException("Unknown request: " + request);
                    }
                    response.write(socketChannel);
                    LOGGER.debug(logMessage("sent: " + response + ": " + response.debugString()));
                    if (receivedExitMessage) {
                        break;
                    }
                }
                LOGGER.info(logMessage("closing socket"));
            } catch (Exception e) {
                LOGGER.error(logMessage(e.toString()));
                try {
                    EchoResponse.EXIT_RESPONSE.write(socketChannel);
                } catch (IOException e1) {
                    LOGGER.error(logMessage(e1.toString()));
                }
            }
            try {
                socketChannel.close();
            } catch (IOException e) {
                LOGGER.error("Error while trying to close socketChannel: " + e);
            }
            LOGGER.info(logMessage("exiting..."));
        }

        private String logMessage(String message) {
            return String.format("#%d: %s", sessionId, message);
        }
    }
}
