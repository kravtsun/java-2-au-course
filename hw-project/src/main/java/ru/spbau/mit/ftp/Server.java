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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.spbau.mit.ftp.protocol.*;

public class Server extends AbstractServer {
    private static final Logger LOGGER = LogManager.getLogger("server");
    private final int nthreads;
    private ExecutorService executorService;
    private final List<SocketChannel> sockets = new ArrayList<>();
    private final List<Future> futures = new ArrayList<>();
    private ServerSocketChannel serverSocketChannel;

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
        executorService = Executors.newFixedThreadPool(nthreads);

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
                        synchronized (this) {
                            sockets.add(socketChannel);
                            futures.add(executorService.submit(new FTPServerSession(socketChannel)));
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
            if (sockets.size() != futures.size()) {
                LOGGER.fatal("sockets.size() != futures.size()");
            }
            for (int i = 0; i < sockets.size(); i++) {
                SocketChannel socketChannel = sockets.get(i);
                Future future = futures.get(i);
                if (socketChannel.isOpen()) {
                    socketChannel.close();
                }
                future.cancel(true);
            }
            sockets.clear();
            futures.clear();
            serverSocketChannel.close();
        }
        executorService.shutdown();
        while (!executorService.isTerminated()) {
            try {
                executorService.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                break;
            }
        }
        LOGGER.debug("exiting stop");
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
                    Request request = Request.parse(socketChannel);
                    Response response = dealRequest(request);
                    response.write(socketChannel);
                    LOGGER.debug(logMessage("sent: " + response + ": " + response.debugString()));
                    if (response.equals(EchoResponse.EXIT_RESPONSE)) {
                        break;
                    }
                }
                LOGGER.info(logMessage("closing socket"));
            } catch (Exception e) {
                LOGGER.error(logMessage(e.toString()));
            } finally {
                try {
                    if (socketChannel.isOpen()) {
                        EchoResponse.EXIT_RESPONSE.write(socketChannel);
                        socketChannel.close();
                    }
                } catch (IOException e) {
                    LOGGER.error(logMessage(e.toString()));
                }
            }
            LOGGER.info(logMessage("exiting..."));
        }

        private Response dealRequest(Request request) throws ServerException {
            LOGGER.info(logMessage("received: " + request + ": " + request.debugString()));
            Response response;
            if (request instanceof EchoRequest) {
                String receivedMessage = ((EchoRequest) request).getMessage();
                if (receivedMessage.equals(EchoRequest.EXIT_MESSAGE)) {
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
                response = GetResponse.serverGetResponse(path);
            } else {
                throw new ServerException("Unknown request: " + request);
            }
            return response;
        }

        private String logMessage(String message) {
            return String.format("#%d: %s", sessionId, message);
        }
    }
}
