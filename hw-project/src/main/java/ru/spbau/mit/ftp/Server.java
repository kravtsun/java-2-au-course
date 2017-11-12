package ru.spbau.mit.ftp;

import org.apache.commons.cli.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.StandardSocketOptions;
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

public class Server extends AbstractServer implements Closeable {
    private static final Logger logger = LogManager.getLogger("server");
    private final ExecutorService executorService;
    private final List<SocketChannel> sockets = new ArrayList<>();
    private ServerSocketChannel serverSocketChannel;

    public Server(int nthreads) throws ServerException {
        try {
            executorService = Executors.newFixedThreadPool(nthreads);
        }
        catch (Exception e) {
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
            logger.debug("Parsing options: " + options);
            CommandLine commandLine = parser.parse(options, args);
            hostName = commandLine.getOptionValue("host", "localhost");
            String portString = commandLine.getOptionValue("port");
            portNumber = Integer.parseInt(portString);
            String nthreadsString = commandLine.getOptionValue("threads", "5");
            nthreads = Integer.parseInt(nthreadsString);
        } catch (Throwable t) {
            logger.error("Failed to parse arguments: " + t);
            return;
        }

        try (Server server = new Server(nthreads)) {
            server.start(hostName, portNumber);
            Scanner scanner = new Scanner(System.in);
            while (true) {
                String command = scanner.nextLine();
                logger.debug("server command: " + command);
                if (command.equals(EchoRequest.EXIT_MESSAGE)) {
                    break;
                }
            }
        } catch (Exception e) {
            logger.error(e);
        }
    }

    @Override
    public synchronized void start(String hostName, int port) throws IOException, ServerException {
        if (serverSocketChannel != null && serverSocketChannel.isOpen()) {
            throw new ServerException("server already running");
        }

        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(hostName, port));
        logger.info("Starting socket for " + serverSocketChannel.getLocalAddress());
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
                logger.info("closing serverSocketChannel by stop() request");
            } catch (Exception e) {
                logger.error(e);
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
        private static final Logger logger = LogManager.getLogger("session");
        private static final AtomicInteger sessionsCount = new AtomicInteger(0);
        private final SocketChannel socketChannel;
        private final int sessionId;

        FTPServerSession(SocketChannel socketChannel) throws IOException {
            this.socketChannel = socketChannel;
            this.sessionId = sessionsCount.incrementAndGet();
        }

        @Override
        public void run() {
            try {
                EchoResponse.INIT_RESPONSE.write(socketChannel);
//                SentEntity.writeString(socketChannel, "Hello from server, session #" + sessionId);
                logger.info(logMessage("starting IO loop"));
                while (true) {
                    SentEntity response;
                    boolean receivedExitMessage = false;
                    Request request = Request.parse(socketChannel);
                    logger.info("received: " + request + ": " + request.debugString());
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
                    logger.debug(logMessage("sent: " + response + ": " + response.debugString()));
                    if (receivedExitMessage) {
                        break;
                    }
                }
                logger.info(logMessage("closing socket"));
            } catch (Exception e) {
                logger.error(logMessage(e.toString()));
                try {
                    EchoResponse.EXIT_RESPONSE.write(socketChannel);
                } catch (IOException e1) {
                    logger.error(logMessage(e1.toString()));
                }
            }
            try {
                socketChannel.close();
            } catch (IOException e) {
                logger.error("Error while trying to close socketChannel: " + e);
            }
            logger.info(logMessage("exiting..."));
        }

        private String logMessage(String message) {
            return String.format("#%d: %s", sessionId, message);
        }
    }
}
