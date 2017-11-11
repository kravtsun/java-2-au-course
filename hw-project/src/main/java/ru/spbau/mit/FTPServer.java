package ru.spbau.mit;

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
import ru.spbau.mit.FTPProtocol.Request;

public class FTPServer implements Closeable {
    private static Logger logger = LogManager.getLogger("server");
    private final ExecutorService executorService;
    private final File rootDir;
    private volatile boolean interrupt = false;

    public static void main(String[] args) {
        CommandLineParser parser = new DefaultParser();
        Options options = new Options();
        options.addRequiredOption(null, "port", true, "Port to start listening at");
        options.addOption("j", "threads", true, "number of worker threads to run");
        int portNumber = 0;
        int nthreads = 0;
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
        try (FTPServer server = new FTPServer(portNumber, nthreads, new File("/home/kravtsun"))) {
            Scanner scanner = new Scanner(System.in);
            while (true) {
                String command = scanner.nextLine();
                System.out.println("server command: " + command);
                if (command.equals("exit")) {
                    break;
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    public FTPServer(int portNumber, int nthreads, File rootDir) throws FTPServerException {
        if (!rootDir.isDirectory()) {
            throw new FTPServerException("invalid root directory: " + rootDir);
        }
        this.rootDir = rootDir;
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
        private static Logger logger = LogManager.getLogger("session");
        private static AtomicInteger sessionsCount = new AtomicInteger(0);
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
                logger.info(logMessage("initializing FTPProtocol"));
                out.println("Hello from server, session #" + sessionId);

                logger.info(logMessage("starting IO loop"));
                while (true) {
                    String inputLine = in.readLine();
                    if (inputLine == null) {
                        break;
                    }
                    logger.info(logMessage("received: " + inputLine));
                    FTPProtocol.SentEntity response;
                    try {
                        Request request = FTPProtocol.parseRequest(inputLine);
                        if (request instanceof FTPProtocol.ListRequest) {
                            String path = ((FTPProtocol.ListRequest) request).getPath();
                            File[] files = new File(path).listFiles();
                            response = new FTPProtocol.ListResponse(files);
//                        } else if (request instanceof FTPProtocol.SimpleRequest) {
//                            response = new FTPProtocol.SimpleResponse("received request: " + request.requestBody());
                        } else {
                            response = new FTPProtocol.SimpleResponse("Unknown request: " + inputLine);
                        }
                    } catch (FTPProtocol.FTPProtocolException e) {
                        logger.error(logMessage(e.getMessage()));
                        response = new FTPProtocol.SimpleResponse("FTPProtocol exception: " + e.getMessage());
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

    public class FTPServerException extends Exception {
        FTPServerException(String message) {
            super(message);
        }
    }
}
