package ru.spbau.mit.ftp;

import org.apache.commons.cli.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.spbau.mit.ftp.protocol.*;

public class Server extends AbstractServer {
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
            logger.info("Parsing options: " + options);
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
        Server server = null;
        try {
            server = new Server(portNumber, nthreads);
        } catch (IOException e) {
            logger.error("Failed to start server: " + e.getMessage());
        }
        try {
            server.start();
            Scanner scanner = new Scanner(System.in);
            while (!server.isFinished) {
                String command = scanner.nextLine();
                logger.info("server command: " + command);
                if (command.equals(SimpleRequest.EXIT_MESSAGE)) {
                    break;
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        } finally {
            server.stop();
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
                logger.error(e.getMessage());
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

    private static class FTPServerSession implements Runnable {
        private static final Logger logger = LogManager.getLogger("session");
        private static final AtomicInteger sessionsCount = new AtomicInteger(0);
//        private final ServerSocketChannel socket;
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
                    try {
                        Request request = Request.parse(socketChannel);
                        logger.info("received: " + request + ": " + request.debugString());
                        if (request instanceof PathRequest) {
                            String path = ((PathRequest) request).getPath();
                            File[] files = new File(path).listFiles();
                            response = new ListResponse(files);
                        } else if (request instanceof SimpleRequest) {
                            String receivedMessage = ((SimpleRequest) request).getMessage();
                            receivedExitMessage = receivedMessage.equals(SimpleRequest.EXIT_MESSAGE);
                            response = new SimpleResponse("received request with message: " + receivedMessage);
                        } else {
                            response = new SimpleResponse("Unknown request: " + request);
                        }
                    } catch (Exception e) {
                        logger.error(logMessage(e.getMessage()));
                        response = new SimpleResponse("Dealing request exception: " + e.getMessage());
                    }
                    response.write(socketChannel);
                    logger.info(logMessage("sent: " + response + ": " + response.debugString()));
                    if (receivedExitMessage) {
                        break;
                    }
                }
                logger.info(logMessage("closing socket"));
            } catch (Exception e) {
                logger.error(logMessage(e.getMessage()));
            }
            logger.info(logMessage("exiting..."));
        }

        private String logMessage(String message) {
            return String.format("#%d: %s", sessionId, message);
        }
    }
}
