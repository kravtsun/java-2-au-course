package ru.spbau.mit;

import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.Socket;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

public class FTPClient {
    private static Logger logger = LogManager.getLogger("client");

    public static void main(String []args)
    {
        CommandLineParser parser = new DefaultParser();
        Options options = new Options();
        options.addRequiredOption(null, "host", true, "Host address");
        options.addRequiredOption(null, "port", true, "Port to start listening at");
        int portNumber;
        String hostName;
        Socket socket;
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

        try {
            logger.info(String.format("Starting socket for %s:%d", hostName, portNumber));
            socket = new Socket(hostName, portNumber);
        } catch (IOException e) {
            logger.error("Failed to establish connection: " + e.getMessage());
            return;
        }

        String listRequestPrefix = "list ";
        try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        ) {
            Scanner scanner = new Scanner(System.in);
            String msg = in.readLine();
            logger.info("received: " + msg);

            String command;
            while ((command = scanner.nextLine()) != null) {
                FTPProtocol.Request request;
                if (command.length() > listRequestPrefix.length() &&
                        command.substring(0, listRequestPrefix.length()).equals(listRequestPrefix)) {
                    request = new FTPProtocol.ListRequest(command.substring(5));
                } else {
                    request = new FTPProtocol.SimpleRequest(command);
                }
                logger.info("sent: " + request.str());
                out.println(request.str());

                msg = in.readLine();
                logger.info("received: " + msg);

                if (command.equals("exit")) {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
