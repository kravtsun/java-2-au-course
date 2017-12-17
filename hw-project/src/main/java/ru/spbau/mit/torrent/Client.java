package ru.spbau.mit.torrent;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static ru.spbau.mit.torrent.Utils.addressFromCommandLine;

public class Client extends AbstractClient {
    private static final Logger LOGGER = LogManager.getLogger("clientApp");
    private final Logger logger = LogManager.getLogger("client:" + getAddress().getPort());
    private final InetSocketAddress trackerAddress;
    private String configFilename;

    public class ClientException extends RuntimeException {
        ClientException(String message) {
            super(message);
        }
    }

    // TODO boolean isAvailableToServe()
    public Client(InetSocketAddress listeningAddress, InetSocketAddress trackerAddress, String storageFilename) throws IOException {
        super(listeningAddress);
        this.trackerAddress = trackerAddress;
        this.configFilename = storageFilename;
        readConfig();
    }

    public static void main(String[] args) {
        CommandLineParser parser = new DefaultParser();
        Options options = new Options();

        // TODO final variables for string constants (used twice in here, whatever).
        options.addRequiredOption(null, "host", true, "Host address");
        options.addRequiredOption(null, "port", true, "Port to start listening at");

        options.addRequiredOption(null, "thost", true, "Tracker's host address");
        options.addRequiredOption(null, "tport", true, "Tracker's port to start knocking");

        options.addOption(null, "config", true, "Where to store/load config");

//        Client client;
        InetSocketAddress listeningAddress;
        InetSocketAddress trackerAddress;
        String configFilename;

        try {
            LOGGER.info("Parsing options: " + options);
            CommandLine commandLine = parser.parse(options, args);
            listeningAddress = addressFromCommandLine(commandLine, "host", "port");
            trackerAddress = addressFromCommandLine(commandLine, "thost", "tport");

            configFilename = "client_" + listeningAddress.getPort() + ".config";
            configFilename = commandLine.getOptionValue("config", configFilename);
//            client = new Client(listeningAddress, trackerAddress, configFilename);
        } catch (Exception e) {
            LOGGER.error("Failed to parse: " + e);
            return;
        }

        AsynchronousSocketChannel trackerChannel = null;
        try {
            trackerChannel = AsynchronousSocketChannel.open();
            Future trackerConnectionFuture = trackerChannel.connect(trackerAddress);
            Object connectionResult = trackerConnectionFuture.get();
            if (connectionResult != null) {
                throw new IOException("Error while getting trackerConnectionFuture: connectionResult != null");
            }
            LOGGER.info("connected to tracker: " + trackerAddress);
//            trackerChannel.connect()
        } catch (Exception e) {
            LOGGER.error("Error while trying to connect to tracker: ", e);
            e.printStackTrace();
        }

        final Scanner scanner = new Scanner(System.in);

        while (true) {
            String message = scanner.nextLine();
            ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());
            assert trackerChannel != null;
//            AsynchronousSocketChannel finalTrackerChannel = trackerChannel;
//            Future result = trackerChannel.write(buffer, null, new CompletionHandler<Integer, Object>() {
//                @Override
//                public void completed(Integer result, Object attachment) {
//                    while (buffer.hasRemaining()) {
//                        finalTrackerChannel.write(buffer);
//                    }
//                }
//
//                @Override
//                public void failed(Throwable exc, Object attachment) {
//                    exc.printStackTrace();
//                    LOGGER.error("Error while sending message: " + exc);
//                }
//            });
            Future result = trackerChannel.write(buffer);

            while (!result.isDone()) {
                System.out.println("... ");
            }
            System.out.println("Sent message: " + message);
            buffer.clear();
        }

//        final String getRequestPrefix = "get ";
//        final String listRequestPrefix = "list ";

//
//        try (AbstractClient client = new Client()) {
//            try {
//                client.connect(hostName, portNumber);
//            } catch (IOException e) {
//                LOGGER.error("Failed to establish connection: " + e);
//                return;
//            }
//            String command;
//            while ((command = scanner.nextLine()) != null) {
//                if (command.isEmpty()) {
//                    continue;
//                }
//                if (command.length() > getRequestPrefix.length()
//                        && command.substring(0, getRequestPrefix.length()).equals(getRequestPrefix)) {
//                    String path = command.substring(getRequestPrefix.length());
//                    File outputFile = File.createTempFile("get_", ".result");
//                    client.executeGet(path, outputFile.getPath());
//                } else if (command.length() > listRequestPrefix.length()
//                        && command.substring(0, listRequestPrefix.length()).equals(listRequestPrefix)) {
//                    String path = command.substring(listRequestPrefix.length());
//                    client.executeList(path);
//                } else if (command.equals(EchoRequest.EXIT_MESSAGE)) {
//                    client.executeExit();
//                    break;
//                } else {
//                    client.executeEcho(command);
//                }
//            }
//        } catch (Exception e) {
//            LOGGER.error(e);
//        }
    }

    @Override
    public boolean isUpdated() {
        return false;
    }

    @Override
    public void setUpdated() {

    }

    @Override
    public List<Integer> stat(int fileId) {
        return null;
    }

    @Override
    public void get(int fileId, int part, OutputStream out) {

    }

    @Override
    public void close() throws IOException {
        writeConfig();
    }

    private void readConfig() throws IOException {
        Scanner in = new Scanner(new FileInputStream(configFilename));
    }

    private void writeConfig() throws IOException {
        PrintWriter writer = new PrintWriter(new FileOutputStream(configFilename));

    }
}
