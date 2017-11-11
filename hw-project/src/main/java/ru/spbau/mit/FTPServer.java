package ru.spbau.mit;
import com.sun.security.ntlm.Server;
import org.apache.commons.cli.*;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FTPServer implements Closeable {
    private static Logger logger = LogManager.getLogger("server");
    private final ExecutorService executorService;
    private final File rootDir;
    private volatile boolean interrupt = false;

    public static void main(String []args) {
        CommandLineParser parser = new DefaultParser();
        Options options = new Options();
        options.addRequiredOption(null, "port", true, "Port to start listening at");
        options.addOption("j", "threads",true,"number of worker threads to run");
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
        private final Socket socket;

        FTPServerSession(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
            ) {
                String inputLine, outputLine;
                logger.info("initializing FTPProtocol");
                FTPProtocol ftpProtocol = new FTPProtocol();
                outputLine = ftpProtocol.processInput(null);
                out.println(outputLine);

                logger.info("starting IO loop");
                while ((inputLine = in.readLine()) != null) {
                    logger.info("received: " + inputLine);
                    outputLine = ftpProtocol.processInput(inputLine);
                    out.println(outputLine);
                    logger.info("sent: " + outputLine);
                    if (outputLine.equals("Bye")) {
                        break;
                    }
                }
                logger.info("closing socket");
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            logger.info("exiting...");
        }
    }


    private static class FTPProtocol {
        String processInput(String clientInput) {
            if (clientInput == null) {
                // protocol initiation
                return "Hi from server";
            } else if (clientInput.equals("exit")) {
                // client wants to exit.
                return "Bye";
            } else {
                return "Unknown command: " + clientInput;
            }
        };
    }

    public class FTPServerException extends Exception {
        FTPServerException(String message) {
            super(message);
        }
    }
}
