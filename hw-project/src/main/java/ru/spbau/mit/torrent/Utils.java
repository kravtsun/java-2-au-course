package ru.spbau.mit.torrent;

import org.apache.commons.cli.CommandLine;
import org.apache.logging.log4j.Logger;
import org.omg.SendingContext.RunTime;

import java.net.InetSocketAddress;
import java.util.List;

public class Utils {
    static final long FILE_PART_SIZE = 10*1024*1024;
    static final String COMMAND_EXIT = "exit";
    static final String COMMAND_LIST = "list";
    static final String COMMAND_SOURCES = "sources";
    static final String COMMAND_UPLOAD = "upload";
    static final String COMMAND_UPDATE = "update";
    static final String COMMAND_GET = "get";
    static final String COMMAND_STAT = "stat";

    static InetSocketAddress addressFromCommandLine(CommandLine commandLine, String hostParameter, String portParameter) {
        String portString = commandLine.getOptionValue(portParameter);
        int portNumber = Integer.parseInt(portString);
        String hostName = commandLine.getOptionValue(hostParameter, "localhost");
        return new InetSocketAddress(hostName, portNumber);
    }

    static void infoList(Logger logger, List<FileProxy> files) {
        logger.info("Received files: " + files.size());
        for (FileProxy fileProxy: files) {
            System.out.println(fileProxy);
        }
    }

    static void infoSources(Logger logger, List<InetSocketAddress> sources) {
        logger.info("Sources processed: " + sources.size());
        for (InetSocketAddress address: sources) {
            logger.info(address.getHostName() + ":" + address.getPort());
        }
    }

    static int intFromIP(byte[] bytes) {
        if (bytes.length != 4) {
            throw new UtilsException("expected 4 bytes for ip, IPv not supported.");
        }
        int result = 0;
        for (int i = 0; i < 4; ++i) {
            result <<= 8;
            result += bytes[i];
        }
        return result;
    }

    static byte[] IPFromInt(int integerIP) {
        byte[] bytes = new byte[4];

        for (int i = 0; i < 4; ++i) {
            bytes[3-i] = (byte) (integerIP & 0xFF);
        }
        assert integerIP == 0;
        return bytes;
    }

    public static class UtilsException extends RuntimeException {
        UtilsException(String message) {
            super(message);
        }
    }
}
