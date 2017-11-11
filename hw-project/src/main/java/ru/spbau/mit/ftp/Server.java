package ru.spbau.mit.ftp;

import org.apache.commons.cli.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.spbau.mit.ftp.protocol.*;

public class Server implements Closeable {
    private static final Logger logger = LogManager.getLogger("server");
    private final ExecutorService executorService;
    private volatile boolean interrupt = false;

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
        try (Server server = new Server(portNumber, nthreads)) {
            Scanner scanner = new Scanner(System.in);
            while (true) {
                String command = scanner.nextLine();
                System.out.println("server command: " + command);
                if (command.equals("exit")) {
                    server.interrupt = true;
                    break;
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    public Server(int portNumber, int nthreads) {
        executorService = Executors.newFixedThreadPool(nthreads);
        new Thread(() -> {
            while (!interrupt) {
                try (ServerSocket serverSocket = new ServerSocket(portNumber)) {
                    executorService.submit(new FTPServerSession(serverSocket.accept()));
                } catch (IOException e) {
                    System.err.println("Could not listen on port " + portNumber);
                    System.exit(-1);
                }
            }
        }).start();
    }

    @Override
    public void close() throws IOException {
    }

    private static class FTPServerSession implements Runnable {
        private static final Logger logger = LogManager.getLogger("session");
        private static final AtomicInteger sessionsCount = new AtomicInteger(0);
        private final Socket socket;
        private final int sessionId;

        FTPServerSession(Socket socket) {
            this.socket = socket;
            this.sessionId = sessionsCount.incrementAndGet();
        }

        @Override
        public void run() {
            try (
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
            ) {
                logger.info(logMessage("initializing Protocol"));
                out.println("Hello from server, session #" + sessionId);

                logger.info(logMessage("starting IO loop"));
                while (true) {
                    String inputLine = in.readLine();
                    if (inputLine == null) {
                        break;
                    }
                    logger.info(logMessage("received: " + inputLine));
                    SentEntity response;
                    try {
                        Request request = Protocol.parseRequest(inputLine);
                        if (request instanceof ListRequest) {
                            String path = ((ListRequest) request).getPath();
                            File[] files = new File(path).listFiles();
                            response = new ListResponse(files);
//                        } else if (request instanceof Protocol.SimpleRequest) {
//                            response = new Protocol.SimpleResponse("received request: " + request.requestBody());
                        } else {
                            response = new SimpleResponse("Unknown request: " + inputLine);
                        }
                    } catch (Protocol.FTPProtocolException e) {
                        logger.error(logMessage(e.getMessage()));
                        response = new SimpleResponse("Protocol exception: " + e.getMessage());
                    }
                    out.println(response.str());
                    logger.info(logMessage("sent: " + response.str()));
                }
                logger.info(logMessage("closing socket"));
                socket.close();
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