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
import java.nio.MappedByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import static ru.spbau.mit.torrent.NIOAsyncProcedures.*;
import static ru.spbau.mit.torrent.Utils.*;

public class Client extends Server implements AbstractClient, Configurable, Closeable {
    private static final Logger LOGGER = LogManager.getLogger("clientApp");
    private final Logger logger;
    private final InetSocketAddress trackerAddress;
    private AsynchronousSocketChannel trackerChannel;

    // contract: trackerMonitor's synchronized comes before synchronized(this)
    private final Object trackerMonitor = new Object();

    private String configFilename;
    private Map<Integer, List<Integer>> fileParts = new HashMap<>();
    private Map<Integer, FileProxy> files = new HashMap<>();

    @Override
    public synchronized void readConfig(String configFilename) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(configFilename, "r")) {
            FileChannel fileChannel = file.getChannel();
            int count;
            count = NIOProcedures.readInt(fileChannel);
            for (int i = 0; i < count; i++) {
                int fileId = NIOProcedures.readInt(fileChannel);
                int partsCount = NIOProcedures.readInt(fileChannel);
                List<Integer> parts = new ArrayList<>();
                for (int j = 0; j < partsCount; j++) {
                    parts.add(NIOProcedures.readInt(fileChannel));
                }
                fileParts.put(fileId, parts);
            }

            count = NIOProcedures.readInt(fileChannel);
            for (int i = 0; i < count; i++) {
                int fileId = NIOProcedures.readInt(fileChannel);
                FileProxy fileProxy = NIOProcedures.readProxy(fileChannel);
                files.put(fileId, fileProxy);
            }
        }
    }

    @Override
    public synchronized void writeConfig(String configFilename) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(configFilename, "rw")) {
            FileChannel fileChannel = file.getChannel();
            NIOProcedures.writeInt(fileChannel, fileParts.size());
            for (Integer fileId : fileParts.keySet()) {
                NIOProcedures.writeInt(fileChannel, fileId);
                List<Integer> parts = fileParts.get(fileId);
                NIOProcedures.writeInt(fileChannel, parts.size());
                for (Integer part : parts) {
                    NIOProcedures.writeInt(fileChannel, part);
                }
            }

            NIOProcedures.writeInt(fileChannel, files.size());
            for (Map.Entry<Integer, FileProxy> e : files.entrySet()) {
                NIOProcedures.writeInt(fileChannel, e.getKey());
                NIOProcedures.writeProxy(fileChannel, e.getValue());
            }
        }
    }

    public Client(InetSocketAddress listeningAddress, InetSocketAddress trackerAddress, String configFilename)
            throws Exception {
        super(listeningAddress);
        logger = LogManager.getLogger("client:" + listeningAddress.getPort());
        this.trackerAddress = trackerAddress;
        this.configFilename = configFilename;
        try {
            readConfig(this.configFilename);
        } catch (IOException e) {
            logger.error("Failed to read config file: " + this.configFilename);
        }
        connectToTracker();
        if (!executeUpdate()) {
            throw new ClientException("Failed to execute initial update");
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
            int fileId;

            main_loop:
            while (true) {
                String command = scanner.next();
                AsynchronousSocketChannel otherClientChannel;

                Callable<AsynchronousSocketChannel> getOtherClientChannel = () -> {
                    LOGGER.info("Other client address: ");
                    String host = scanner.next();
                    LOGGER.info("port: ");
                    int port = scanner.nextInt();
                    InetSocketAddress otherClientAddress =
                            new InetSocketAddress(InetAddress.getByName(host), port);
                    AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();
                    Future connectionFuture = channel.connect(otherClientAddress);
                    Object connectionResult = connectionFuture.get();
                    if (connectionResult != null) {
                        throw new ClientException(
                                "Error while getting trackerConnectionFuture: connectionResult != null");
                    }
                    LOGGER.info("connected to other client: " + otherClientAddress);
                    return channel;
                };

                switch (command) {
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
                        otherClientChannel = getOtherClientChannel.call();
                        LOGGER.info("fileId: ");
                        fileId = scanner.nextInt();
                        LOGGER.info("partId: ");
                        int partId = scanner.nextInt();
                        client.executeGet(otherClientChannel, fileId, partId);
                        break;
                    case COMMAND_STAT:
                        LOGGER.info("Stat command: ");
                        otherClientChannel = getOtherClientChannel.call();
                        fileId = scanner.nextInt();
                        LOGGER.info("fileId: " + fileId);
                        List<Integer> parts = client.executeStat(otherClientChannel, fileId);
                        LOGGER.info("Parts received: " + parts.size());
                        for (Integer part: parts) {
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
            }
        } catch (Exception e) {
            LOGGER.error("Client error: " + e);
            e.printStackTrace();
        }
    }

    @Override
    public List<Integer> executeStat(AsynchronousSocketChannel in, int fileId) throws Exception {
        writeInt(in, CODE_STAT);
        writeInt(in, fileId);
        int count = readInt(in);
        java.util.List<java.lang.Integer> parts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            parts.add(readInt(in));
        }
        return parts;
    }

    @Override
    public synchronized void executeGet(AsynchronousSocketChannel in, int fileId, int partId) throws Exception {
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
            MappedByteBuffer buffer = file.getChannel().map(FileChannel.MapMode.READ_WRITE, start, finish);
            readUntil(buffer, in);
            parts.add(partId);
            if (parts.contains(partId)) {
                logger.warn("Part " + partId + " is already downloaded for fileId: " + fileId);
                return;
            }
        }
    }

    @Override
    public List<FileProxy> executeList() throws Exception {
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
    public int executeUpload(String filename) throws Exception {
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
    public List<InetSocketAddress> executeSources(int fileId) throws Exception {
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
    public boolean executeUpdate() throws Exception {
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

    private void connectToTracker() throws Exception {
        synchronized (trackerMonitor) {
            trackerChannel = AsynchronousSocketChannel.open();
            Future trackerConnectionFuture = trackerChannel.connect(trackerAddress);
            Object connectionResult = trackerConnectionFuture.get();
            if (connectionResult != null) {
                throw new ClientException("Error while getting trackerConnectionFuture: connectionResult != null");
            }
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

        ClientSession(AsynchronousSocketChannel channel) throws Exception {
            super(channel);
        }

        public void proceedGet(int fileId, int partId) throws Exception {
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
            } catch (Exception e) {
                LOGGER.error("Failed to proceed get request: " + e);
                throw e;
            }
        }

        public void proceedStat(int fileId) throws Exception {
            List<Integer> parts = new ArrayList<>(fileParts.get(fileId));
            writeInt(getChannel(), parts.size());
            for (Integer part: parts) {
                writeInt(getChannel(), part);
            }
        }
    }
}
