package ru.spbau.mit.ftp;

import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.spbau.mit.ftp.protocol.ListRequest;
import ru.spbau.mit.ftp.protocol.Request;
import ru.spbau.mit.ftp.protocol.SimpleRequest;

import java.io.*;
import java.net.Socket;
import java.util.Base64;
import java.util.Scanner;

public class Client extends AbstractClient {
    private static final Logger logger = LogManager.getLogger("client");
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    public static void main(String []args)
    {
        CommandLineParser parser = new DefaultParser();
        Options options = new Options();
        options.addRequiredOption(null, "host", true, "Host address");
        options.addRequiredOption(null, "port", true, "Port to start listening at");
        int portNumber;
        String hostName;

        try {
            logger.info("Parsing options: " + options);
            CommandLine commandLine = parser.parse(options, args);
            String portString = commandLine.getOptionValue("port");
            portNumber = Integer.parseInt(portString);
            hostName = commandLine.getOptionValue("host", "localhost");
        } catch (Exception e) {
            logger.error("Failed to parse: " + e.getMessage());
            return;
        }

        Client client = new Client();
        try {
            client.connect(hostName, portNumber);
        } catch (IOException e) {
            logger.error("Failed to establish connection: " + e.getMessage());
            return;
        }

        final String listRequestPrefix = "list ";
        final Scanner scanner = new Scanner(System.in);
        try {
            String command;
            while ((command = scanner.nextLine()) != null) {
                if (command.length() > listRequestPrefix.length() &&
                        command.substring(0, listRequestPrefix.length()).equals(listRequestPrefix)) {
                    client.executeList(command.substring(listRequestPrefix.length()));
                } else {
                    client.executeSimple(command);
                }

                if (command.equals("exit")) {
                    break;
                }
            }
        }
        catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    @Override
    public void connect(String hostName, int port) throws IOException {
        logger.info(String.format("Starting socket for %s:%d", hostName, port));
        socket = new Socket(hostName, port);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        String msg = in.readLine();
        // receiving hello from server.
        logger.info("received: " + msg);
    }

    @Override
    public void disconnect() throws ClientNotConnectedException, IOException {
        if (socket == null) {
            throw new ClientNotConnectedException();
        } else {
            in.close();
            out.close();
            socket.close();
            socket = null;
        }
    }

    @Override
    public void executeList(String path) throws ClientNotConnectedException, IOException {
        if (socket == null) {
            throw new ClientNotConnectedException();
        }
        ListRequest request = new ListRequest(path);
        executeRequest(request);
    }

    @Override
    public void executeGet(String path) throws ClientNotConnectedException, IOException {
        if (socket == null) {
            throw new ClientNotConnectedException();
        }
        assert false;
    }

    public void executeSimple(String message) throws ClientNotConnectedException, IOException {
        if (socket == null) {
            throw new ClientNotConnectedException();
        }
        SimpleRequest request = new SimpleRequest(message);
        executeRequest(request);
    }

    private void executeRequest(Request request) throws IOException {
        logger.info("sent: " + request.str());
        out.println(request.str());
        String msg = in.readLine();
        byte[] bytes = Base64.getMimeDecoder().decode(msg);
        String bytesString = new String(bytes);
        logger.info("received: " + bytesString);
    }
}
