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
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static ru.spbau.mit.torrent.NIOProcedures.*;
import static ru.spbau.mit.torrent.Utils.*;

public class Client extends Server implements AbstractClient, Configurable, Closeable {
    private static final Logger LOGGER = LogManager.getLogger("clientApp");
    private final Logger logger;
    private AsynchronousSocketChannel trackerChannel;

    // contract: trackerMonitor's synchronized comes (or inside) synchronized(this).
    private final Object trackerMonitor = new Object();

    private String configFilename;
    private Map<Integer, List<Integer>> fileParts = new HashMap<>();
    private Map<Integer, FileProxy> files = new HashMap<>();

    @Override
    public synchronized void readConfig(String configFilename) throws IOException, NIOException {
        this.configFilename = configFilename;
        if (configFilename == null) {
            LOGGER.warn("No configuration file was specified. Default settings...");
            return;
        }
        try (RandomAccessFile file = new RandomAccessFile(configFilename, "r")) {
            FileChannel fileChannel = file.getChannel();
            int count;
            count = readInt(fileChannel);
            for (int i = 0; i < count; i++) {
                int fileId = readInt(fileChannel);
                int partsCount = readInt(fileChannel);
                List<Integer> parts = new ArrayList<>();
                for (int j = 0; j < partsCount; j++) {
                    parts.add(readInt(fileChannel));
                }
                fileParts.put(fileId, parts);
            }

            count = readInt(fileChannel);
            for (int i = 0; i < count; i++) {
                int fileId = readInt(fileChannel);
                FileProxy fileProxy = readProxy(fileChannel);
                files.put(fileId, fileProxy);
            }
        }
    }

    @Override
    public synchronized void writeConfig(String configFilename) throws ClientException {
        this.configFilename = configFilename;
        if (configFilename == null) {
            LOGGER.warn("No configuration file specified, no settings saved.");
            return;
        }
        try (RandomAccessFile file = new RandomAccessFile(configFilename, "rw")) {
            FileChannel fileChannel = file.getChannel();
            writeInt(fileChannel, fileParts.size());
            for (Integer fileId : fileParts.keySet()) {
                writeInt(fileChannel, fileId);
                List<Integer> parts = fileParts.get(fileId);
                writeInt(fileChannel, parts.size());
                for (Integer part : parts) {
                    writeInt(fileChannel, part);
                }
            }

            writeInt(fileChannel, files.size());
            for (Map.Entry<Integer, FileProxy> e : files.entrySet()) {
                writeInt(fileChannel, e.getKey());
                NIOProcedures.writeProxy(fileChannel, e.getValue());
            }
        } catch (IOException | NIOException e) {
            String message = "Failed to write configuration to file " + configFilename;
            throw new ClientException(message, e);
        }
    }

    public Client(InetSocketAddress listeningAddress, InetSocketAddress trackerAddress, String configFilename) throws NIOException, IOException {
            super(listeningAddress);
        logger = LogManager.getLogger("client:" + listeningAddress.getPort());
        if (configFilename == null) {
            logger.warn("No config file specified, loading fresh client.");
        } else {
            readConfig(configFilename);
        }
        this.configFilename = configFilename;
        connectToTracker(trackerAddress);
        if (!executeUpdate()) {
            throw new ClientException("Failed to execute initial update");
        }
    }

    private static AsynchronousSocketChannel getOtherClientChannel(String host, int port) throws IOException {
        InetSocketAddress otherClientAddress =
                new InetSocketAddress(InetAddress.getByName(host), port);
        AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();
        Future connectionFuture = channel.connect(otherClientAddress);

        try {
            Object connectionResult = connectionFuture.get();
            if (connectionResult != null) {
                LOGGER.warn("while getting trackerConnectionFuture: connectionResult != null");
            }
        } catch (InterruptedException | ExecutionException e) {
            String message = "Error while waiting for connecting to other client with address "
                    + otherClientAddress + ": " + e;
            throw new ClientException(message, e);
        }
        LOGGER.info("connected to other client: " + otherClientAddress);
        return channel;
    }

    private static void executeCommandLoop(Scanner scanner, Client client) {
        // TODO print available commands.
        AsynchronousSocketChannel otherClientChannel = null;

        main_loop:
        while (true) {
            String command = scanner.next();
            int fileId;

            final String otherClientAddressErrorMessage = "Error while retrieving other client's address";

            try {
                switch (command) {
                    case "connect":
                        LOGGER.info("Connecting to other client");
                        if (otherClientChannel != null) {
                            try {
                                LOGGER.error("Already connected to client: " + otherClientChannel.getRemoteAddress());
                            } catch (IOException e) {
                                LOGGER.error(otherClientAddressErrorMessage + e);
                            }
                        }
                        try {
                            LOGGER.info("Other client address: ");
                            String host = scanner.next();
                            LOGGER.info("port: ");
                            int port = scanner.nextInt();
                            otherClientChannel = getOtherClientChannel(host, port);
                        } catch (Exception e) {
                            LOGGER.error("Failed to connect to other client: " + e);
                            otherClientChannel = null;
                        }
                        if (otherClientChannel != null) {
                            try {
                                LOGGER.info("Connection successful to client: " + otherClientChannel.getRemoteAddress());
                            } catch (IOException e) {
                                LOGGER.error(otherClientAddressErrorMessage + e);
                            }
                        }
                        break;
                    case "disconnect":
                        LOGGER.info("Disconnecting from other client");
                        if (otherClientChannel == null) {
                            LOGGER.error("Not connected to any clients");
                        } else {
                            try {
                                otherClientChannel.close();
                            } catch (IOException e) {
                                LOGGER.error("Error while closing channel: " + e);
                            } finally {
                                otherClientChannel = null;
                            }
                        }
                        break;
                    case COMMAND_EXIT:
                        LOGGER.info("Exiting...");
                        break main_loop;
                    case COMMAND_LIST:
                        LOGGER.info("List request");
                        Utils.infoList(LOGGER, client.executeList());
                        break;
                    case COMMAND_SOURCES:
                        fileId = scanner.nextInt();
                        LOGGER.info("Sources request for fileId: " + fileId);
                        Utils.infoSources(LOGGER, client.executeSources(fileId));
                        break;
                    case COMMAND_UPLOAD:
                        String filename = scanner.next();
                        LOGGER.info("Uploading, filename: " + filename);
                        fileId = client.executeUpload(filename);
                        LOGGER.info("Uploaded, fileId: " + fileId);
                        break;
                    case COMMAND_GET:
                        LOGGER.info("Get command: ");
                        LOGGER.info("fileId: ");
                        fileId = scanner.nextInt();
                        LOGGER.info("partId: ");
                        int partId = scanner.nextInt();
                        client.executeGet(otherClientChannel, fileId, partId);
                        break;
                    case COMMAND_STAT:
                        LOGGER.info("Stat command: ");

                        fileId = scanner.nextInt();
                        LOGGER.info("fileId: " + fileId);
                        List<Integer> parts = client.executeStat(otherClientChannel, fileId);
                        LOGGER.info("Parts received: " + parts.size());
                        for (Integer part : parts) {
                            LOGGER.info("#" + part);
                        }
                        break;
                    case COMMAND_UPDATE:
                        LOGGER.info("Update command");
                        boolean updateStatus = client.executeUpdate();
                        LOGGER.info("Update status: " + updateStatus);
                    default:
                        LOGGER.error("Unknown command: " + command);
                        break;
                }
            } catch (NIOException e) {
                LOGGER.error("Bad command: " + e);
                e.printStackTrace();
            } catch (IOException e) {
                LOGGER.error("Bad file: " + e);
                e.printStackTrace();
            }
        }
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
        } catch (Exception e) {
            LOGGER.error("Failed to parse: " + e);
            return;
        }

        try (Client client = new Client(listeningAddress, trackerAddress, configFilename)) {
            final Scanner scanner = new Scanner(System.in);
            executeCommandLoop(scanner, client);
        } catch (Exception e) {
            LOGGER.error("Fatal error: " + e);
        }
    }

    @Override
    public List<Integer> executeStat(AsynchronousSocketChannel in, int fileId) throws NIOException {
        logger.info("executeStat");
        synchronized (this) {
            writeInt(in, CODE_STAT);
            writeInt(in, fileId);
            int count = readInt(in);
            java.util.List<java.lang.Integer> parts = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                parts.add(readInt(in));
            }
            return parts;
        }
    }

    @Override
    public synchronized void executeGet(AsynchronousSocketChannel in, int fileId, int partId) throws IOException, NIOException {
        logger.info("executeGet");
        List<Integer> parts;
        FileProxy fileProxy;
        synchronized (this) {
            parts = fileParts.get(fileId);
            fileProxy = files.get(fileId);
        }

        if (parts == null) {
            if (fileProxy == null) {
                executeList();
            }
            synchronized (this) {
                fileProxy = files.get(fileId);
                if (fileProxy == null) {
                    throw new ClientException("Failed to retrieve file proxy, where to save?");
                }
                fileParts.put(fileId, new ArrayList<>());
                parts = fileParts.get(fileId);
            }
        }

        synchronized (this) {
            writeInt(in, CODE_GET);
            writeInt(in, fileId);
            writeInt(in, partId);

            RandomAccessFile file = new RandomAccessFile(fileProxy.getName(), "rw");
            long start = partId * FILE_PART_SIZE;
            long finish = start + FILE_PART_SIZE;
            long size = finish - start;
            // FIXME size is incorrect if file's length is not divisible by FILE_PART_SIZE!!!
            MappedByteBuffer buffer = file.getChannel().map(FileChannel.MapMode.READ_WRITE, start, size);
            readUntil(buffer, in);
            parts.add(partId);
        }
    }

    @Override
    public List<FileProxy> executeList() throws NIOException {
        logger.info("executeList");
        List<FileProxy> fileProxies = new ArrayList<>();
        synchronized (trackerMonitor) {
            writeInt(trackerChannel, CODE_LIST);
            int count = readInt(trackerChannel);
            for (int i = 0; i < count; i++) {
                fileProxies.add(readProxy(trackerChannel));
            }
        }
        synchronized (this) {
            files = null;
            fileProxies.forEach((fileProxy -> files.put(fileProxy.getId(), fileProxy)));
        }
        return fileProxies;
    }

    @Override
    public int executeUpload(String filename) throws NIOException {
        logger.info("executeUpload");
        writeInt(trackerChannel, CODE_UPLOAD);
        writeString(trackerChannel, filename);
        File file = new File(filename);
        long size = file.length();
        writeLong(trackerChannel, size);
        int fileId = readInt(trackerChannel);
        FileProxy fileProxy = new FileProxy(fileId, filename, size);
        synchronized (this) {
            files.put(fileId, fileProxy);
        }
        return fileId;
    }

    @Override
    public List<InetSocketAddress> executeSources(int fileId) throws NIOException {
        logger.info("executeSources");
        List<InetSocketAddress> addresses = new ArrayList<>();
        synchronized (trackerMonitor) {
            writeInt(trackerChannel, CODE_SOURCES);
            int count = readInt(trackerChannel);
            for (int i = 0; i < count; i++) {
                addresses.add(readAddress(trackerChannel));
            }
        }
        return addresses;
    }

    @Override
    public boolean executeUpdate() throws NIOException {
        logger.info("executeUpdate");
        synchronized (trackerMonitor) {
            writeInt(trackerChannel, CODE_UPDATE);
            writeAddress(trackerChannel, getAddress());
            synchronized (this) {
                writeInt(trackerChannel, files.size());
                for (Integer fileId : files.keySet()) {
                    writeInt(trackerChannel, fileId);
                }
            }
            int updateStatus = readInt(trackerChannel);
            return updateStatus != 0;
        }
    }

    @Override
    public void close() throws IOException {
        writeConfig(configFilename);
        trackerChannel.close();
    }

    private void connectToTracker(InetSocketAddress trackerAddress) throws ClientException {
        synchronized (trackerMonitor) {
            try {
                trackerChannel = AsynchronousSocketChannel.open();
                Future trackerConnectionFuture = trackerChannel.connect(trackerAddress);
                Object connectionResult = trackerConnectionFuture.get();
                if (connectionResult != null) {
                    throw new ClientException("Error while getting trackerConnectionFuture: connectionResult != null");
                }
            } catch (InterruptedException | ExecutionException | IOException e) {
                throw new ClientException("Failed to connect to tracker: " + e);
            }
            new Thread(() -> {
                while (trackerChannel.isOpen()) {
                    while (!Thread.interrupted()) {
                        try {
                            synchronized (trackerMonitor) {
                                trackerMonitor.wait(UPDATE_TIMEOUT);
                                boolean updateStatus = executeUpdate();
                                if (!updateStatus) {
                                    logger.warn("update failed");
                                }
                            }
                        } catch (BufferUnderflowException e) {
                            logger.warn("trackerChannel seems to be closed: " + e);
                        } catch (Exception e) {
                            logger.error("update failed with error: " + e);
                        }
                    }
                }
            }).start();
        }
        logger.info("connected to tracker: " + trackerAddress);
    }

    @Override
    void serve(AsynchronousSocketChannel channel) {
        try (ClientSession session = new ClientSession(channel)) {
            session.run();
        } catch (Exception e) {
            LOGGER.error("ClientSession error: " + e);
            e.printStackTrace();
        }
    }

    private class ClientSession extends AbstractClientSession {
        ClientSession(AsynchronousSocketChannel channel) {
            super(channel);
        }

        @Override
        public void proceedGet(int fileId, int partId) throws IOException, NIOException {
            FileProxy fileProxy;
            synchronized (this) {
                fileProxy = files.get(fileId);
            }

            try (RandomAccessFile file = new RandomAccessFile(fileProxy.getName(), "r")) {
                long length = file.length();
                long start = partId * FILE_PART_SIZE;
                if (start > length) {
                    throw new ClientException("part " + partId + " is not consistent with file length: " + length);
                }
                long finish = start + FILE_PART_SIZE;
                if (finish > length) {
                    finish = length;
                }
                ByteBuffer buffer = file.getChannel().map(FileChannel.MapMode.READ_ONLY, start, finish);
                writeUntil(buffer, getChannel());
            }
        }

        @Override
        public void proceedStat(int fileId) throws NIOException {
            List<Integer> parts = new ArrayList<>(fileParts.get(fileId));
            writeInt(getChannel(), parts.size());
            for (Integer part: parts) {
                writeInt(getChannel(), part);
            }
        }
    }
}
