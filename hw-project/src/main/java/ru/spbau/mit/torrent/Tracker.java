package ru.spbau.mit.torrent;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.BufferUnderflowException;
import java.nio.channels.*;
import java.util.*;

import static ru.spbau.mit.torrent.NIOProcedures.*;
import static ru.spbau.mit.torrent.Utils.*;

public class Tracker extends Server implements AbstractTracker, AutoCloseable, Configurable {
    private static final Logger LOGGER = LogManager.getLogger("trackerApp");
    private String configFilename;

    private List<FileProxy> fileProxies = new ArrayList<>();
    private Map<Integer, Set<InetSocketAddress>> fileSeeds = new HashMap<>();

    public Tracker() throws IOException {
        initEmpty();
    }

    public Tracker(InetSocketAddress listeningAddress, String configFilename) throws IOException, NIOException {
        this();
        bind(listeningAddress);
        try {
            readConfig(configFilename);
        } catch (FileNotFoundException e) {
            LOGGER.warn("Failed to read configuration from file: " + configFilename);
        }
    }

    public Tracker(InetSocketAddress listeningAddress) throws IOException, NIOException {
        this(listeningAddress, null);
    }

    @Override
    public synchronized void initEmpty() {
        fileProxies = new ArrayList<>();
        fileSeeds = new HashMap<>();
    }

    @Override
    public synchronized void setConfig(String configFilename) {
        this.configFilename = configFilename;
    }

    @Override
    public synchronized void readConfig(String configFilename) throws IOException, NIOException {
        if (configFilename == null) {
            LOGGER.warn("No configuration file was specified. Default settings...");
            return;
        }
        try (RandomAccessFile file = new RandomAccessFile(configFilename, "r")) {
            LOGGER.info("Reading configuration from file: " + configFilename);
            FileChannel fileChannel = file.getChannel();
            int count;
            initEmpty();
            count = readInt(fileChannel);
            LOGGER.debug("Number of proxies: " + count);
            for (int i = 0; i < count; i++) {
                FileProxy fileProxy = NIOProcedures.readProxy(fileChannel);
                LOGGER.debug("proxy: " + fileProxy);
                fileProxies.add(fileProxy);
            }
            int maxFileId = -1;
            count = readInt(fileChannel);
            LOGGER.debug("Number of seeds for proxies: " + count);
            for (int i = 0; i < count; i++) {
                int fileId = readInt(fileChannel);
                if (fileId > maxFileId) {
                    maxFileId = fileId;
                }
                int seedsCount = readInt(fileChannel);
                Set<InetSocketAddress> seeds = new HashSet<>();
                for (int j = 0; j < seedsCount; j++) {
                    InetSocketAddress address = NIOProcedures.readAddress(fileChannel);
                    seeds.add(address);
                }
                LOGGER.debug("fileId: " + fileId + ", seeds: " + fileSeeds);
                fileSeeds.put(fileId, seeds);
            }
            LOGGER.debug("maxFileId: " + maxFileId);
            if (maxFileId != -1 && !fileSeeds.containsKey(maxFileId)) {
                String message = "Something's not right, maxFileId: " + maxFileId + " but seeds are absent";
                LOGGER.warn(message);
            }

            if (maxFileId != -1 && fileProxies.size() != maxFileId) {
                String message = "Something's not right, maxFileId: " + maxFileId + " but fileProxies.size() = " + fileProxies.size();
                LOGGER.warn(message);
            }

            FileProxy.FILE_ID.set(maxFileId + 1);
        }
        setConfig(configFilename);
    }

    @Override
    public synchronized void writeConfig(String configFilename) throws IOException, NIOException {
        if (configFilename == null) {
            LOGGER.warn("No configuration file specified, no settings saved.");
            return;
        }
        try (RandomAccessFile file = new RandomAccessFile(configFilename, "rw")) {
            LOGGER.info("Writing configuration to file: " + configFilename);
            FileChannel fileChannel = file.getChannel();
            writeInt(fileChannel, fileProxies.size());
            LOGGER.debug("Number of proxies: " + fileProxies.size());
            for (FileProxy fileProxy : fileProxies) {
                NIOProcedures.writeProxy(fileChannel, fileProxy);
                LOGGER.debug("proxy: " + fileProxy);
            }

            writeInt(fileChannel, fileSeeds.size());
            LOGGER.debug("Number of seeds for proxies: " + fileSeeds.size());
            for (Map.Entry<Integer, Set<InetSocketAddress>> entry : fileSeeds.entrySet()) {
                writeInt(fileChannel, entry.getKey());
                int seedsCount = entry.getValue().size();
                writeInt(fileChannel, seedsCount);
                for (InetSocketAddress address : entry.getValue()) {
                    NIOProcedures.writeAddress(fileChannel, address);
                }
                LOGGER.debug("fileId: " + entry.getKey() + ", seeds: " + entry.getValue());
            }
        }
    }

    public static void main(String[] args) {
        CommandLineParser parser = new DefaultParser();
        Options options = new Options();

        // TODO final variables for string constants (used twice in here, whatever).
        options.addRequiredOption(null, "thost", true, "Tracker's host address");
        options.addOption(null, "tport", true, "Tracker's port to start knocking");
        options.addOption(null, "config", true, "Where to store/load config");

        InetSocketAddress trackerAddress;
        String configFilename;

        try {
            LOGGER.info("Parsing options: " + options);
            CommandLine commandLine = parser.parse(options, args);
            trackerAddress = Utils.addressFromCommandLine(commandLine, "thost", "tport");

            configFilename = "tracker_" + trackerAddress.getPort() + ".config";
            configFilename = commandLine.getOptionValue("config", configFilename);
        } catch (Exception e) {
            LOGGER.error("Failed to parse: " + e);
            return;
        }

        try (Tracker tracker = new Tracker(trackerAddress, configFilename)) {
            Scanner scanner = new Scanner(System.in);
            main_loop:
            while (true) {
                String command = scanner.next();
                // TODO replace with switch.
                switch (command) {
                    case COMMAND_EXIT:
                        LOGGER.info("Exiting...");
                        break main_loop;
                    case COMMAND_LIST:
                        LOGGER.info("List request");
                        Utils.infoList(LOGGER, tracker.list());
                        break;
                    case COMMAND_SOURCES:
                        LOGGER.info("Sources request");
                        int fileId = scanner.nextInt();
                        Utils.infoSources(LOGGER, tracker.sources(fileId));
                        break;
                    default:
                        LOGGER.error("Unknown command: " + command);
                        break;
                }
            }
        } catch (IOException | NIOException e) {
            LOGGER.error("Tracker error: " + e);
            e.printStackTrace();
        }
    }

    @Override
    public synchronized void close() throws IOException {
        super.close();
        try {
            writeConfig(configFilename);
        } catch (NIOException e) {
            throw new IOException(e);
        }
    }

    @Override
    public synchronized List<FileProxy> list() {
        return new ArrayList<>(fileProxies);
    }

    @Override
    public int upload(InetSocketAddress address, String filename, long size) {
        final FileProxy fileProxy = new FileProxy(filename, size);
        Set<InetSocketAddress> addressList = new HashSet<>();
        addressList.add(address);
        synchronized (this) {
            fileProxies.add(fileProxy);
            fileSeeds.put(fileProxy.getId(), addressList);
        }
        return fileProxy.getId();
    }

    @Override
    public synchronized List<InetSocketAddress> sources(int fileId) {
        Set<InetSocketAddress> seeds = fileSeeds.get(fileId);
        if (seeds == null) {
            LOGGER.warn("No seeds present for fileId: " + fileId);
            return new ArrayList<>();
        }
        return new ArrayList<>(seeds);
    }

    @Override
    public synchronized boolean update(InetSocketAddress clientAddress, int[] fileIds) {
        try {
            // as if once updated with some file the client will be treated as its seed forever.
            for (int fileId : fileIds) {
                fileSeeds.get(fileId).add(clientAddress);
            }
        } catch (Exception e) {
            LOGGER.warn("Update failed: " + e);
            return false;
        }
        return true;
    }

    @Override
    void serve(AsynchronousSocketChannel channel) {
        try (TrackerSession session = new TrackerSession(this, channel)) {
            session.run();
        } catch (BufferUnderflowException e) {
            LOGGER.info("Sessions seems to be closed by " + e.getMessage());
        } catch (Exception e) {
            LOGGER.error("TrackerSession error: " + e);
            e.printStackTrace();
        }
    }
}
