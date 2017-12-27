package ru.spbau.mit.torrent;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.*;

import static ru.spbau.mit.torrent.NIOProcedures.*;
import static ru.spbau.mit.torrent.Utils.*;

public class Tracker extends Server implements AbstractTracker, AutoCloseable, Configurable {
    private static final Logger LOGGER = LogManager.getLogger("trackerApp");
    private String configFilename;

    private List<FileProxy> fileProxies = new ArrayList<>();
    private Map<Integer, Set<InetSocketAddress>> fileSeeds = new HashMap<>();
    private final Object monitor = new Object();

    public Tracker() throws IOException {}

    public Tracker(InetSocketAddress listeningAddress, String configFilename) throws IOException, NIOException {
        bind(listeningAddress);
        initEmpty();
        readConfig(configFilename);
    }

    public Tracker(InetSocketAddress listeningAddress) throws IOException, NIOException {
        this(listeningAddress, null);
    }

    private void initEmpty() {
        synchronized (monitor) {
            fileProxies = new ArrayList<>();
            fileSeeds = new HashMap<>();
        }
    }

    @Override
    public synchronized void readConfig(String configFilename) throws IOException, NIOException {
        this.configFilename = configFilename;
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
            for (int i = 0; i < count; i++) {
                fileProxies.add(NIOProcedures.readProxy(fileChannel));
            }
            count = readInt(fileChannel);
            for (int i = 0; i < count; i++) {
                int fileId = readInt(fileChannel);
                int seedsCount = readInt(fileChannel);
                fileSeeds.put(fileId, new HashSet<>());
                for (int j = 0; j < seedsCount; j++) {
                    InetSocketAddress address = NIOProcedures.readAddress(fileChannel);
                    fileSeeds.get(fileId).add(address);
                }
            }
        }
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
            for (FileProxy fileProxy : fileProxies) {
                NIOProcedures.writeProxy(fileChannel, fileProxy);
            }

            writeInt(fileChannel, fileSeeds.size());
            for (Map.Entry<Integer, Set<InetSocketAddress>> entry : fileSeeds.entrySet()) {
                writeInt(fileChannel, entry.getKey());
                int seedsCount = entry.getValue().size();
                writeInt(fileChannel, seedsCount);
                for (InetSocketAddress address : entry.getValue()) {
                    NIOProcedures.writeAddress(fileChannel, address);
                }
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
        synchronized (monitor) {
            try {
                writeConfig(configFilename);
            } catch (NIOException e) {
                throw new IOException(e);
            }
        }
    }

    @Override
    public List<FileProxy> list() {
        synchronized (monitor) {
            return new ArrayList<>(fileProxies);
        }
    }

    @Override
    public int upload(InetSocketAddress address, String filename, long size) {
        final FileProxy fileProxy = new FileProxy(filename, size);
        Set<InetSocketAddress> addressList = new HashSet<>();
        addressList.add(address);
        synchronized (monitor) {
            fileProxies.add(fileProxy);
            fileSeeds.put(fileProxy.getId(), addressList);
        }
        return fileProxy.getId();
    }

    @Override
    public List<InetSocketAddress> sources(int fileId) {
        synchronized (monitor) {
            Set<InetSocketAddress> seeds = fileSeeds.get(fileId);
            if (seeds == null) {
                throw new TrackerException("No seeds present for fileId: " + fileId);
            }
            return new ArrayList<>(seeds);
        }
    }

    @Override
    public boolean update(InetSocketAddress clientAddress, int[] fileIds) {
        synchronized (monitor) {
            try {
                // as if once updated with some file the client will be treated as its seed forever.
                for (int fileId : fileIds) {
                    fileSeeds.get(fileId).add(clientAddress);
                }
            } catch (Exception e) {
                LOGGER.warn("Update failed: " + e);
                return false;
            }
        }
        return true;
    }

    @Override
    void serve(AsynchronousSocketChannel channel) {
        try (TrackerSession session = new TrackerSession(this, channel)) {
            session.run();
        } catch (Exception e) {
            LOGGER.error("TrackerSession error: " + e);
            e.printStackTrace();
        }
    }
}
