package ru.spbau.mit.torrent;

import org.apache.commons.cli.CommandLine;

import java.net.InetSocketAddress;

public class Utils {
    static InetSocketAddress addressFromCommandLine(CommandLine commandLine, String hostParameter, String portParameter) {
        String portString = commandLine.getOptionValue(portParameter);
        int portNumber = Integer.parseInt(portString);
        String hostName = commandLine.getOptionValue(hostParameter, "localhost");
        return new InetSocketAddress(hostName, portNumber);
    }
}
