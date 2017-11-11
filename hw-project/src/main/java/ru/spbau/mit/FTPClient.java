package ru.spbau.mit;

import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

public class FTPClient {
    private static Logger logger = LogManager.getLogger("client");

    public static void main(String []args) {
        CommandLineParser parser = new DefaultParser();
        Options options = new Options();
        options.addRequiredOption(null, "host", true, "Host address");
        options.addRequiredOption(null, "port", true, "Port to start listening at");
        int portNumber = 0;
        String hostName = null;
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

        try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        ) {
            Scanner scanner = new Scanner(System.in);
            String msg = in.readLine();
            logger.info("received: " + msg);

            String command;
            while ((command = scanner.nextLine()) != null) {
                logger.info("sent: " + command);
                out.println(command);
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
