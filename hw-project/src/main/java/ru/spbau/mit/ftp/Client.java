package ru.spbau.mit.ftp;

import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.spbau.mit.ftp.protocol.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.Scanner;

public class Client extends AbstractClient {
    private static final Logger LOGGER = LogManager.getLogger("client");
    private SocketChannel socketChannel;

    public Client() {
    }

    public static void main(String[] args) {
        CommandLineParser parser = new DefaultParser();
        Options options = new Options();
        options.addRequiredOption(null, "host", true, "Host address");
        options.addRequiredOption(null, "port", true, "Port to start listening at");
        int portNumber;
        String hostName;

        try {
            LOGGER.info("Parsing options: " + options);
            CommandLine commandLine = parser.parse(options, args);
            String portString = commandLine.getOptionValue("port");
            portNumber = Integer.parseInt(portString);
            hostName = commandLine.getOptionValue("host", "localhost");
        } catch (Exception e) {
            LOGGER.error("Failed to parse: " + e);
            return;
        }

        final String getRequestPrefix = "get ";
        final String listRequestPrefix = "list ";
        final Scanner scanner = new Scanner(System.in);

        try (AbstractClient client = new Client()) {
            try {
                client.connect(hostName, portNumber);
            } catch (IOException e) {
                LOGGER.error("Failed to establish connection: " + e);
                return;
            }
            String command;
            while ((command = scanner.nextLine()) != null) {
                if (command.isEmpty()) {
                    continue;
                }
                if (command.length() > getRequestPrefix.length()
                        && command.substring(0, getRequestPrefix.length()).equals(getRequestPrefix)) {
                    String path = command.substring(getRequestPrefix.length());
                    File outputFile = File.createTempFile("get_", ".result");
                    client.executeGet(path, outputFile.getPath());
                } else if (command.length() > listRequestPrefix.length()
                        && command.substring(0, listRequestPrefix.length()).equals(listRequestPrefix)) {
                    String path = command.substring(listRequestPrefix.length());
                    client.executeList(path);
                } else if (command.equals(EchoRequest.EXIT_MESSAGE)) {
                    client.executeExit();
                    break;
                } else {
                    client.executeEcho(command);
                }
            }
        } catch (Exception e) {
            LOGGER.error(e);
        }
    }

    @Override
    public synchronized EchoResponse connect(String hostName, int port) throws IOException {
        LOGGER.info(String.format("Starting socket for %s:%d", hostName, port));
        socketChannel = SocketChannel.open();
        socketChannel.connect(new InetSocketAddress(hostName, port));
        EchoResponse response = new EchoResponse();
        response.read(socketChannel);
        LOGGER.debug("received hello: " + response.debugString());
        return response;
    }

    @Override
    public synchronized void disconnect() throws IOException {
        executeExit();
        if (socketChannel != null) {
            if (socketChannel.isConnected()) {
                executeExit();
                socketChannel.close();
            }
            socketChannel = null;
        }
    }

    @Override
    public synchronized void close() throws IOException {
        disconnect();
    }

    @Override
    public synchronized ListResponse executeList(String path)
            throws ClientNotConnectedException, IOException {
        if (!isConnected()) {
            throw new ClientNotConnectedException();
        }
        ListRequest request = new ListRequest(path);
        ListResponse response = new ListResponse();
        executeRequest(request, response);
        return response;
    }

    @Override
    public synchronized GetResponse executeGet(String path, String outputPath)
            throws ClientNotConnectedException, IOException {
        if (!isConnected()) {
            throw new ClientNotConnectedException();
        }
        GetRequest request = new GetRequest(path);
        GetResponse response = GetResponse.clientGetResponse(outputPath);
        executeRequest(request, response);
        LOGGER.info(path + " got and saved into " + outputPath);
        return response;
    }

    public synchronized EchoResponse executeEcho(String message)
            throws ClientNotConnectedException, IOException {
        if (!isConnected()) {
            throw new ClientNotConnectedException();
        }
        EchoRequest request = new EchoRequest(message);
        EchoResponse response = new EchoResponse();
        executeRequest(request, response);
        return response;
    }

    public synchronized EchoResponse executeExit() {
        if (!isConnected()) {
            return null;
        }
        try {
            EchoRequest request = new EchoRequest(EchoRequest.EXIT_MESSAGE);
            EchoResponse response = new EchoResponse();
            executeRequest(request, response);
            return response;
        } catch (Exception e) {
            LOGGER.info("Suppressing error on exiting client: " + e);
            return null;
        }
    }

    synchronized boolean isConnected() {
        return socketChannel != null && socketChannel.isConnected();
    }

    private void executeRequest(Request request, Response response) throws IOException {
        LOGGER.debug("sent: " + request.toString() + ": " + request.debugString());
        request.write(socketChannel);
        response.read(socketChannel);
        LOGGER.debug("received: " + response.toString() + ": " + response.debugString());
    }
}
