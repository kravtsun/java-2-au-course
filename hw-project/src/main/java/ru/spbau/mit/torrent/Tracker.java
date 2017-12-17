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

import static ru.spbau.mit.torrent.NIOAsyncProcedures.*;
import static ru.spbau.mit.torrent.Utils.*;

public class Tracker extends Server implements AbstractTracker, AutoCloseable, Configurable {
    private static final Logger LOGGER = LogManager.getLogger("trackerApp");
    private final String configFilename;

    private List<FileProxy> fileProxies = new ArrayList<>();
    private Map<Integer, Set<InetSocketAddress>> fileSeeds = new HashMap<>();
    private final Object monitor = new Object();

    public Tracker(InetSocketAddress listeningAddress, String configFilename) throws IOException {
        super(listeningAddress);
        this.configFilename = configFilename;
        try {
            readConfig(this.configFilename);
        } catch (IOException e) {
            LOGGER.error("Failed to read config file: " + this.configFilename);
        }
    }

    @Override
    public synchronized void readConfig(String configFilename) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(configFilename, "r")) {
            FileChannel fileChannel = file.getChannel();
            int count;
            count = NIOProcedures.readInt(fileChannel);
            for (int i = 0; i < count; i++) {
                fileProxies.add(NIOProcedures.readProxy(fileChannel));
            }

            count = NIOProcedures.readInt(fileChannel);
            for (int i = 0; i < count; i++) {
                int fileId = NIOProcedures.readInt(fileChannel);
                int seedsCount = NIOProcedures.readInt(fileChannel);
                for (int j = 0; j < seedsCount; j++) {
                    InetSocketAddress address = NIOProcedures.readAddress(fileChannel);
                    fileSeeds.get(fileId).add(address);
                }
            }
        }
    }

    @Override
    public synchronized void writeConfig(String configFilename) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(configFilename, "rw")) {
            FileChannel fileChannel = file.getChannel();
            NIOProcedures.writeInt(fileChannel, fileProxies.size());
            for (FileProxy fileProxy : fileProxies) {
                NIOProcedures.writeProxy(fileChannel, fileProxy);
            }

            NIOProcedures.writeInt(fileChannel, fileSeeds.size());
            for (Map.Entry<Integer, Set<InetSocketAddress>> entry : fileSeeds.entrySet()) {
                NIOProcedures.writeInt(fileChannel, entry.getKey());
                int seedsCount = entry.getValue().size();
                NIOProcedures.writeInt(fileChannel, seedsCount);
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
        options.addRequiredOption(null, "tport", true, "Tracker's port to start knocking");
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
        } catch (IOException e) {
            LOGGER.error("Tracker error: " + e);
        }
    }

    @Override
    public synchronized void close() throws IOException {
        super.close();
        synchronized (monitor) {
            writeConfig(configFilename);
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
            return new ArrayList<>(fileSeeds.get(fileId));
        }
    }

    @Override
    public boolean update(InetSocketAddress clientAddress, int[] fileIds) {
        synchronized (monitor) {
            // as if once updated with some file the client will be treated as its seed forever.
            for (int fileId : fileIds) {
                fileSeeds.get(fileId).add(clientAddress);
            }
        }
        return false;
    }

    @Override
    void serve(AsynchronousSocketChannel channel) {
        try (TrackerSession session = new TrackerSession(channel)) {
            session.run();
        } catch (Exception e) {
            LOGGER.error("TrackerSession error: " + e);
            e.printStackTrace();
        }
    }

    private class TrackerSession extends AbstractTrackerSession {
        private InetSocketAddress address;

        TrackerSession(AsynchronousSocketChannel channel) throws Exception {
            super(channel);
            int opCode = readInt(channel);
            if (opCode != CODE_UPDATE) {
                throw new TrackerException("update expected");
            }
            proceedUpdate();
        }

        @Override
        void proceedList() throws Exception {
            LOGGER.info("Proceeding: " + COMMAND_LIST);
            List<FileProxy> files = list();
            // TODO protobuf
            writeInt(getChannel(), files.size());
            for (FileProxy file: files) {
                writeProxy(getChannel(), file);
            }
        }

        @Override
        void proceedUpload() throws Exception {
            String filename = readString(getChannel());
            long size = readLong(getChannel());
            int fileId = upload(address, filename, size);
            writeInt(getChannel(), fileId);
        }

        @Override
        void proceedUpdate() throws Exception {
            LOGGER.info("Proceeding: " + COMMAND_UPDATE);
            // TODO protobuf
            int count = readInt(getChannel());
            address = readAddress(getChannel());
            int[] fileParts = new int[count];
            for (int i = 0; i < count; i++) {
                fileParts[i] = readInt(getChannel());
            }
            boolean updateStatus = update(address, fileParts);
            writeInt(getChannel(), updateStatus ? 1 : 0);
        }

        @Override
        void proceedSources() throws Exception {
            LOGGER.info("Proceeding: " + COMMAND_SOURCES);
            int fileId = readInt(getChannel());
            List<InetSocketAddress> seeds = sources(fileId);
            // TODO protobuf
            writeInt(getChannel(), seeds.size());
            for (InetSocketAddress address: seeds) {
                writeAddress(getChannel(), address);
            }
        }
    }
}
