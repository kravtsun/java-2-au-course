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
import java.nio.MappedByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static ru.spbau.mit.torrent.NIOProcedures.*;
import static ru.spbau.mit.torrent.Utils.*;

public class Client extends Server implements AbstractClient, Configurable, FileParter, Closeable {
    private static final Logger LOGGER = LogManager.getLogger("clientApp");
    private Logger logger = LogManager.getLogger("client");

    private AsynchronousSocketChannel trackerChannel;
    // monitor for operations with trackerChannel.
    // contract: trackerMonitor's synchronized comes before synchronized(this).
    private final Object trackerMonitor = new Object();
    private Thread trackerUpdateThread;

    private AsynchronousSocketChannel otherClientChannel;
//    private final Object otherClientMonitor = new Object();

    private String configFilename;
    private Map<Integer, List<Integer>> fileParts;
    private Map<Integer, FileProxy> files;
    // contract: fileParts.keySet() == file.keySet().

    public Client() throws IOException {
        initEmpty();
    }

    public Client(InetSocketAddress listeningAddress, InetSocketAddress trackerAddress, String configFilename) throws NIOException, IOException {
        initEmpty();
        bind(listeningAddress);
        if (configFilename == null) {
            logger.warn("No config file specified, loading fresh client.");
        } else {
            readConfig(configFilename);
        }
        connectToTracker(trackerAddress);
    }

    @Override
    public void bind(InetSocketAddress listeningAddress) throws IOException {
        super.bind(listeningAddress);
        logger = LogManager.getLogger("client:" + listeningAddress.getPort());
    }

    @Override
    public synchronized void setConfig(String configFilename) {
        this.configFilename = configFilename;
    }

    @Override
    public synchronized void initEmpty() {
        fileParts = new HashMap<>();
        files = new HashMap<>();
    }

    @Override
    public void readConfig(String configFilename) throws IOException, NIOException {
        if (configFilename == null) {
            logger.warn("No configuration file was specified. Default settings...");
            return;
        }
        logger.debug("reading configuration from " + configFilename);
        initEmpty();
        try (RandomAccessFile file = new RandomAccessFile(configFilename, "r")) {
            FileChannel fileChannel = file.getChannel();
            int count;
            count = readInt(fileChannel);
            synchronized (this) {
                for (int i = 0; i < count; i++) {
                    int fileId = readInt(fileChannel);
                    int partsCount = readInt(fileChannel);
                    List<Integer> parts = new ArrayList<>();
                    for (int j = 0; j < partsCount; j++) {
                        parts.add(readInt(fileChannel));
                    }
                    logger.debug("File parts for fileId " + fileId + ": " + Arrays.toString(parts.toArray()));
                    fileParts.put(fileId, parts);
                }

                count = readInt(fileChannel);
                for (int i = 0; i < count; i++) {
                    int fileId = readInt(fileChannel);
                    FileProxy fileProxy = readProxy(fileChannel);
                    logger.debug("fileProxy for fileId "  + fileId + ": " + fileProxy);
                    files.put(fileId, fileProxy);
                }
            }
        } catch (FileNotFoundException e) {
            logger.warn("Failed to find file: " + configFilename);
        }
        setConfig(configFilename);
    }

    @Override
    public void writeConfig(String configFilename) throws ClientException {
        if (configFilename == null) {
            LOGGER.warn("No configuration file specified, no settings saved.");
            return;
        }
        logger.debug("writing configuration to " + configFilename);
        try (RandomAccessFile file = new RandomAccessFile(configFilename, "rw")) {
            FileChannel fileChannel = file.getChannel();
            synchronized (this) {
                logger.debug("fileParts: ");
                writeInt(fileChannel, fileParts.size());
                for (Integer fileId : fileParts.keySet()) {
                    logger.debug("fileId: " + fileId);
                    writeInt(fileChannel, fileId);
                    List<Integer> parts = fileParts.get(fileId);
                    writeInt(fileChannel, parts.size());
                    for (Integer part : parts) {
                        logger.debug("part " + part);
                        writeInt(fileChannel, part);
                    }
                    logger.debug("File parts for fileId " + fileId + ": " + Arrays.toString(parts.toArray()));
                }

                logger.debug("files: ");
                writeInt(fileChannel, files.size());
                for (Map.Entry<Integer, FileProxy> e : files.entrySet()) {
                    int fileId = e.getKey();
                    FileProxy fileProxy = e.getValue();
                    writeInt(fileChannel, fileId);
                    NIOProcedures.writeProxy(fileChannel, fileProxy);
                    logger.debug("fileProxy for fileId "  + fileId + ": " + fileProxy);
                }
            }
        } catch (IOException | NIOException e) {
            String message = "Failed to write configuration to file " + configFilename;
            throw new ClientException(message, e);
        }
        setConfig(configFilename);
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
            e.printStackTrace();
        }
    }

    @Override
    public List<Integer> executeStat(int fileId) throws NIOException {
        logger.info("executeStat");
        if (!isConnected()) {
            throw new ClientException("Not connected to any client.");
        }
        writeInt(otherClientChannel, CODE_STAT);
        writeInt(otherClientChannel, fileId);
        int count = readInt(otherClientChannel);
        List<Integer> parts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            parts.add(readInt(otherClientChannel));
        }
        return parts;
    }

    @Override
    public void executeGet(int fileId, int partId, String filename) throws IOException, NIOException {
        logger.info("executeGet");
        if (!isConnected()) {
            throw new ClientException("Not connected to any client.");
        }
        List<Integer> parts;
        FileProxy fileProxy;

        synchronized (this) {
            parts = fileParts.get(fileId);
            fileProxy = files.get(fileId);
            if (parts == null || fileProxy == null) {
                String message = "Need to execute list to tracker as file seems to be unknown";
                throw new ClientException(message);
            }

            if (filename != null && !filename.isEmpty()) {
                fileProxy.setName(filename);
            }
        }

        File realFile = fileFromProxy(fileProxy);
        logger.debug("Writing part#" + partId + " of fileId: " + fileId + " to file: " + realFile);
        synchronized (this) {
            try (RandomAccessFile file = new RandomAccessFile(realFile, "rw")) {
                writeInt(otherClientChannel, CODE_GET);
                writeInt(otherClientChannel, fileId);
                writeInt(otherClientChannel, partId);
                long start = partId * FILE_PART_SIZE;
                long size = readLong(otherClientChannel);
                if (size == 0) {
                    logger.warn("No bytes were read for fileId " + fileId + ":" + partId);
                } else {
                    // FIXME size is incorrect if file's length is not divisible by FILE_PART_SIZE!!!
                    MappedByteBuffer buffer = file.getChannel().map(FileChannel.MapMode.READ_WRITE, start, size);
                    readUntil(buffer, otherClientChannel);
                }
            }
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
            fileProxies.forEach((fileProxy -> {
                int fileId = fileProxy.getId();
                FileProxy thisClientFileProxy = fileProxy.clone();
                thisClientFileProxy.setName(null);
                if (!files.containsKey(fileId)) {
                    files.put(fileId, thisClientFileProxy);
                    if (fileParts.containsKey(fileId)) {
                        String message = "fileParts contains fileId: " + fileId + "which is nonsense";
                        throw new IllegalStateException(message);
                    }
                    fileParts.put(fileId, new ArrayList<>());
                }
            }));
        }
        return fileProxies;
    }

    @Override
    public int executeUpload(String filename) throws NIOException {
        logger.info("executeUpload");
        int fileId;
        File file = new File(filename);
        if (!file.exists()) {
            throw new ClientException("File \"" + filename + "\" does not exist");
        }
        long size = file.length();
        synchronized (trackerMonitor) {
            writeInt(trackerChannel, CODE_UPLOAD);
            writeString(trackerChannel, filename);
            writeLong(trackerChannel, size);
            fileId = readInt(trackerChannel);
        }
        FileProxy fileProxy = new FileProxy(fileId, filename, size);
        String message = "uploaded file with proxy: " + fileProxy;
        logger.info(message);
        synchronized (this) {
            files.put(fileId, fileProxy);
            int partsCount = (int) ((size + FILE_PART_SIZE - 1)/ FILE_PART_SIZE);
            List<Integer> parts = new ArrayList<>();
            for (int i = 0; i < partsCount; i++) {
                parts.add(i);
            }
            fileParts.put(fileId, parts);
        }
        return fileId;
    }

    @Override
    public List<InetSocketAddress> executeSources(int fileId) throws NIOException {
        logger.info("executeSources");
        List<InetSocketAddress> addresses = new ArrayList<>();
        synchronized (trackerMonitor) {
            writeInt(trackerChannel, CODE_SOURCES);
            writeInt(trackerChannel, fileId);
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
//            if (!trackerChannel.isOpen()) {
//                LOGGER.warn("channel to tracker is closed.");
//                return false;
//            }
            writeInt(trackerChannel, CODE_UPDATE);
            try {
                writeAddress(trackerChannel, getAddress());
            } catch (IOException e) {
                throw new ClientException("Failed to get address", e);
            }
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
        trackerUpdateThread.interrupt();
        synchronized (trackerMonitor) {
            trackerChannel.close();
        }
        disconnect();
    }

    @Override
    public void connect(InetSocketAddress otherClientAddress) throws IOException {
        if (isConnected()) {
            throw new IOException("Already connected to client: " + otherClientChannel.getRemoteAddress());
        }
        otherClientChannel = AsynchronousSocketChannel.open();
        Future connectionFuture = otherClientChannel.connect(otherClientAddress);
        try {
            Object connectionResult = connectionFuture.get();
            if (connectionResult != null) {
                LOGGER.warn("while getting trackerConnectionFuture: connectionResult != null");
            }
        } catch (InterruptedException | ExecutionException e) {
            final String message = "Error while waiting for connecting to other client with address "
                    + otherClientAddress + ": " + e;
            disconnect();
            throw new ClientException(message, e);
        }
        LOGGER.info("connected to other client: " + otherClientAddress);
    }

    @Override
    public boolean isConnected() {
        if (otherClientChannel != null) {
            return true;
        }
        return false;
    }

    @Override
    public void disconnect() throws IOException {
        if (otherClientChannel != null) {
            otherClientChannel.close();
        }
        otherClientChannel = null;
    }

    private static void executeCommandLoop(Scanner scanner, Client client) {
        // TODO print help with available commands and their syntax.
        main_loop:
        while (true) {
            String command = scanner.next();
            int fileId;
            String filename;

            final String otherClientAddressErrorMessage = "Error while retrieving other client's address";

            try {
                switch (command) {
                    case COMMAND_CONNECT:
                        LOGGER.info("Connecting to other client");
                        if (client.isConnected()) {
                            LOGGER.error("already connected to client " + client.otherClientChannel.getRemoteAddress());
                        }
                        try {
                            LOGGER.info("Other client host: ");
                            String host = scanner.next();
                            LOGGER.info("port: ");
                            int port = scanner.nextInt();
                            InetSocketAddress otherClientAddress =
                                    new InetSocketAddress(InetAddress.getByName(host), port);
                            client.connect(otherClientAddress);
                        } catch (Exception e) {
                            LOGGER.error("Failed to connect to other client: " + e);
                        }
                        break;
                    case COMMAND_DISCONNECT:
                        LOGGER.info("Disconnecting from other client, if any.");
                        client.disconnect();
                        break;
                    case COMMAND_EXIT:
                        LOGGER.info("Exiting...");
                        break main_loop;
                    case COMMAND_LIST:
                        LOGGER.info("List request");
                        Utils.infoList(LOGGER, client.executeList());
                        break;
                    case COMMAND_SOURCES:
                        LOGGER.info("Sources request");
                        LOGGER.info("fileId: ");
                        fileId = scanner.nextInt();
                        LOGGER.info("Sources request for fileId: " + fileId);
                        Utils.infoSources(LOGGER, client.executeSources(fileId));
                        break;
                    case COMMAND_UPLOAD:
                        LOGGER.info("Upload request");
                        LOGGER.info("filename: ");
                        filename = scanner.next();
                        fileId = client.executeUpload(filename);
                        LOGGER.info("Up loaded, fileId: " + fileId);
                        break;
                    case COMMAND_GET:
                        LOGGER.info("Get command: ");
                        LOGGER.info("fileId: ");
                        fileId = scanner.nextInt();
                        LOGGER.info("partId: ");
                        int partId = scanner.nextInt();
                        LOGGER.info("filename (optional but not skippable on first get with this fileId): ");
                        filename = scanner.next();
                        client.executeGet(fileId, partId, filename);
                        break;
                    case COMMAND_STAT:
                        LOGGER.info("Stat command: ");
                        LOGGER.info("fileId: ");
                        fileId = scanner.nextInt();
                        List<Integer> parts = client.executeStat(fileId);
                        LOGGER.info("Parts received: " + parts.size());
                        for (Integer part : parts) {
                            LOGGER.info("part#" + part);
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
            } catch (ClientException e) {
                LOGGER.error("ClientException: " + e);
                e.printStackTrace();
            }
        }
    }

    @Override
    public void connectToTracker(InetSocketAddress trackerAddress) throws ClientException, NIOException {
        String notBindedErrorMessage = "Failed to get serving address";
        try {
            if (getAddress() == null) {
               throw new ClientException(notBindedErrorMessage);
            }
        } catch (IOException e) {
            throw new ClientException(notBindedErrorMessage, e);
        }

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

            final String initialUpdateErrorMessage = "Failed to execute initial update";
            try {
                if (!executeUpdate()) {
                    throw new ClientException(initialUpdateErrorMessage);
                }
            } catch (NIOException e) {
                throw new ClientException(initialUpdateErrorMessage, e);
            }

            trackerUpdateThread = new Thread(() -> {
                synchronized (trackerMonitor) {
                    while (trackerChannel.isOpen() && !Thread.interrupted()) {
                        try {
                            trackerMonitor.wait(UPDATE_TIMEOUT);
                            boolean updateStatus = executeUpdate();
                            if (!updateStatus) {
                                logger.warn("update failed");
                            }
                        } catch (BufferUnderflowException e) {
                            logger.warn("trackerChannel seems to be closed: " + e);
                            return;
                        } catch (InterruptedException e) {
                            logger.info("trackerUpdateThread interrupted");
                            return;
                        } catch (Exception e) {
                            logger.error("update failed with error: " + e);
                        }
                    }
                }
            });
            trackerUpdateThread.setDaemon(true);
            trackerUpdateThread.start();
        }
        logger.info("connected to tracker: " + trackerAddress);
        executeList();
    }

    @Override
    void serve(AsynchronousSocketChannel channel) {
        try (ClientSession session = new ClientSession(this, channel)) {
            session.run();
        } catch (Exception e) {
            LOGGER.error("ClientSession error: " + e);
            e.printStackTrace();
        }
    }

    @Override
    public synchronized FileProxy getFile(int fileId) {
        // use clone?
        return files.get(fileId);
    }

    @Override
    public synchronized List<Integer> getFileParts(int fileId) {
        List<Integer> parts = fileParts.get(fileId);
        if (parts != null) {
            parts = new ArrayList<>(parts);
        } else {
            parts = Collections.emptyList();
        }
        return parts;
    }

    @Override
    public File fileFromProxy(FileProxy fileProxy) {
        if (fileProxy.getName() == null) {
            String message = "Trying to write into file with unknown name for proxy: " + fileProxy;
            throw new ClientException(message);
        }
        return new File(fileProxy.getName());
    }
}
