package ru.spbau.mit.ftp;

import org.apache.commons.cli.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
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
    private final ServerSocketChannel serverSocketChannel;
    private volatile boolean isInterrupted = false;
    private volatile boolean isFinished = false;

    public static void main(String[] args) {
        CommandLineParser parser = new DefaultParser();
        Options options = new Options();
        options.addRequiredOption(null, "port", true, "Port to start listening at");
        options.addOption("j", "threads", true, "number of worker threads to run");
        int portNumber;
        int nthreads;
        try {
            logger.debug("Parsing options: " + options);
            CommandLine commandLine = parser.parse(options, args);
            String portString = commandLine.getOptionValue("port");
            portNumber = Integer.parseInt(portString);
            String nthreadsString = commandLine.getOptionValue("threads", "5");
            nthreads = Integer.parseInt(nthreadsString);
        } catch (Throwable t) {
            logger.error("Failed to parse arguments: " + t);
            return;
        }

        logger.info(String.format("Starting server on localhost:%d, with %d threads", portNumber, nthreads));
        try (Server server = new Server(portNumber, nthreads)) {
            server.start();
            Scanner scanner = new Scanner(System.in);
            while (!server.isFinished) {
                String command = scanner.nextLine();
                logger.debug("server command: " + command);
                if (command.equals(SimpleRequest.EXIT_MESSAGE)) {
                    break;
                }
            }
        } catch (Exception e) {
            logger.error(e);
        }
    }

    public Server(int port, int nthreads) throws IOException {
        executorService = Executors.newFixedThreadPool(nthreads);
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress("localhost", port));
        logger.info("Starting socket for " + serverSocketChannel.getLocalAddress());
    }

    @Override
    public void start() {
        new Thread(() -> {
            try {
                while (!isInterrupted) {
                    executorService.submit(new FTPServerSession(serverSocketChannel));
                }
            } catch (Exception e) {
                logger.error(e);
                isInterrupted = true;
            }
            synchronized (this) {
                isFinished = true;
                notifyAll();
            }
        }).start();
    }

    @Override
    public void stop() {
        isInterrupted = true;
        synchronized (this) {
            // if still haven't exited the main server loop.
            if (!isFinished) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        serverSocketChannel.close();
    }

    private static class FTPServerSession implements Runnable, Closeable {
        private static final Logger logger = LogManager.getLogger("session");
        private static final AtomicInteger sessionsCount = new AtomicInteger(0);
        private final SocketChannel socketChannel;
        private final int sessionId;

        FTPServerSession(ServerSocketChannel serverSocketChannel) throws IOException {
            socketChannel = serverSocketChannel.accept();
            this.sessionId = sessionsCount.incrementAndGet();
        }

        @Override
        public void run() {
            try {
                SentEntity.writeString(socketChannel, "Hello from server, session #" + sessionId);
                logger.info(logMessage("starting IO loop"));
                while (true) {
                    SentEntity response;
                    boolean receivedExitMessage = false;
                    Request request = Request.parse(socketChannel);
                    logger.info("received: " + request + ": " + request.debugString());
                    if (request instanceof SimpleRequest) {
                        String receivedMessage = ((SimpleRequest) request).getMessage();
                        receivedExitMessage = receivedMessage.equals(SimpleRequest.EXIT_MESSAGE);
                        response = new SimpleResponse("received request with message: " + receivedMessage);
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
            }
            logger.info(logMessage("exiting..."));
        }

        @Override
        public void close() throws IOException {
            socketChannel.close();
        }

        private String logMessage(String message) {
            return String.format("#%d: %s", sessionId, message);
        }

    }
}
