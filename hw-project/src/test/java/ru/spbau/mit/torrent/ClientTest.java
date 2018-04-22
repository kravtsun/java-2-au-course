package ru.spbau.mit.torrent;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.omg.CORBA.TIMEOUT;

import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static ru.spbau.mit.torrent.TestCommons.*;
import static ru.spbau.mit.torrent.Utils.getNumParts;

public class ClientTest {
    private static final Logger LOGGER = LogManager.getLogger("ClientTest");
    private static final long OPERATION_TIMEOUT = 300;
    private Tracker emptyTracker;

    @Rule
    public final TemporaryFolder tmpFolder = new TemporaryFolder();

    @Before
    public void setUp() throws Exception {
        while (true) {
            try {
                emptyTracker = new Tracker(getTrackerAddress());
            } catch (BindException ignored) {
                Thread.sleep(BIND_TIMEOUT);
                continue;
            }
            break;
        }
    }

    @Test
    public void simpleConnectionToTracker() throws Exception {
        try (Client ignored = getEmptyClient()) {
            Thread.sleep(OPERATION_TIMEOUT); // wait for hand-shaking to happen.
        }
        emptyTracker.close();
    }

    @Test(expected = NIOException.class)
    public void failsOnTrackerClosing() throws Exception {
        try (Client client = getEmptyClient()) {
            assertTrue(client.executeUpdate());
            emptyTracker.close();
            Thread.sleep(OPERATION_TIMEOUT);
            assertFalse(client.executeUpdate());
        }
    }

    @Test
    public void emptyTrackerTest() throws Exception {
        try (Client client = getEmptyClient()) {
            assertEquals(Collections.emptyList(), client.executeList());
            assertEquals(Collections.emptyList(), client.executeSources(-1));
            assertEquals(Collections.emptyList(), client.executeSources(0));
        }
    }

//    @Test
//    public void uploadSmallFileTest() throws Exception {
//        testFile(CRLF_FILE, Collections.singletonList(0));
//    }
//
//    @Test
//    public void uploadBigFileTest() throws Exception {
//        List<Integer> parts = new ArrayList<>();
//        for (int i = 0; i < getNumParts(BIG_RANDOM_FILE); i++) {
//            parts.add(i);
//        }
//        testFile(BIG_RANDOM_FILE, parts);
//    }
//
//
//    private void testFile(File file, List<Integer> parts) throws Exception {
//        tmpFolder.delete();
//        tmpFolder.create();
//
//        try (Client client = getEmptyClient()) {
//            String filename = file.getAbsolutePath();
//            int fileId = client.executeUpload(filename);
//            assertEquals(0, fileId);
//            assertEquals(Collections.singletonList(client.getAddress()), client.executeSources(fileId));
//            final FileProxy fileProxy = client.getFile(0);
//            assertEquals(fileId, fileProxy.getId());
//            assertEquals(filename, fileProxy.getName());
//            assertEquals(file.length(), fileProxy.getSize());
//
//            try (Client client1 = getEmptyClient()) {
//                client1.connect(client.getAddress());
//                assertEquals(Collections.singletonList(fileId), client1.executeStat(fileId));
//                final File client1File = tmpFolder.newFile();
//                client1.executeGet(fileId, 0, client1File.getAbsolutePath());
//                assertTrue(FileUtils.contentEquals(file, client1File));
//                client1.disconnect();
//
//                FileProxy client1FileProxy = client1.getFile(fileId);
//                assertEquals(fileProxy.getSize(), client1FileProxy.getSize());
//                assertEquals(fileProxy.getId(), client1FileProxy.getId());
//                assertEquals(Collections.singletonList(0), client1.getFileParts(fileId));
//
//                final File client1Config = tmpFolder.newFile();
//                client1.writeConfig(client1Config.getAbsolutePath());
//                client1.initEmpty();
//                assertEquals(null, client1.getFile(fileId));
//                assertEquals(Collections.emptyList(), client1.getFileParts(fileId));
//                client1.readConfig(client1Config.getAbsolutePath());
//
//                client1FileProxy = client1.getFile(fileId);
//                assertEquals(fileProxy.getSize(), client1FileProxy.getSize());
//                assertEquals(fileProxy.getId(), client1FileProxy.getId());
//                assertEquals(Collections.singletonList(0), client1.getFileParts(fileId));
//            }
//        }
//    }

    @Test
    public void testBigFile() throws Exception {
        tmpFolder.delete();
        tmpFolder.create();
        File file = BIG_RANDOM_FILE;

        try (Client client = getEmptyClient()) {
            String filename = file.getAbsolutePath();
            int fileId = client.executeUpload(filename);

            try (Client client1 = getEmptyClient()) {
                List<InetSocketAddress> seedAddresses = client1.executeSources(fileId);
                InetSocketAddress otherClientAddress = seedAddresses.get(0);
                client1.connect(otherClientAddress);
                File client1File = tmpFolder.newFile();
                List<Integer> parts = client1.executeStat(fileId);
                for (Integer ipart: parts) {
                    client1.executeGet(fileId, ipart, client1File.getAbsolutePath());
                }
//                client1.connect(client.getAddress());
//                client1.executeGetAll(fileId, client1File.getAbsolutePath());
                System.out.println("Checking correctness...");
                assertTrue(FileUtils.contentEquals(file, client1File));
            }
        }
    }

    @Test
    public void testBigFile2() throws Exception {
        tmpFolder.delete();
        tmpFolder.create();
        File file = BIG_RANDOM_FILE;
        String filename = file.getAbsolutePath();
        Client client0 = getEmptyClient();
        InetSocketAddress clientAddress = client0.getAddress();

        String clientConfigFilename = tmpFolder.newFile().getAbsolutePath();
        client0.setConfig(clientConfigFilename);
        int fileId = client0.executeUpload(filename);
        try (Client client1 = getEmptyClient()) {
            List<InetSocketAddress> seedAddresses1 = client1.executeSources(fileId);
            client1.connect(seedAddresses1.get(0));
            File client1File = tmpFolder.newFile();
            new Thread(() -> {
                try {
                    for (Integer ipart : client1.executeStat(fileId)) {
                        System.out.println("Reading ipart#" + ipart);
                        client1.executeGet(fileId, ipart, client1File.getAbsolutePath());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
            Thread.sleep(500);
            client0.close();
            System.out.println("Parts downloaded: " + client1.getFileParts(fileId).size());
            client1.disconnect();
            try (Client client2 = getEmptyClient()) {
                List<InetSocketAddress> seedAddresses2 = client1.executeSources(fileId);
                client2.connect(seedAddresses2.get(1));
                String client2ConfigFilename = tmpFolder.newFile().getAbsolutePath();
                File client2File = tmpFolder.newFile();

                for (Integer ipart : client2.executeStat(fileId)) {
                    client2.executeGet(fileId, ipart, client2File.getAbsolutePath());
                }

                System.out.println("### WRITE CONFIG FOR SECOND CLIENT ###");
                String client1ConfigFilename = tmpFolder.newFile().getAbsolutePath();
                client1.writeConfig(client1ConfigFilename);

                System.out.println("### WRITE CONFIG FOR THIRD CLIENT ###");
                client2.writeConfig(client2ConfigFilename);

                assertEquals(client1.getFileParts(fileId), client2.getFileParts(fileId));

                System.out.println("Sleeping");
                Thread.sleep(OPERATION_TIMEOUT);
                System.out.println("### READ CONFIG FOR FIRST CLIENT ###");
                new Client(clientAddress, emptyTracker.getAddress(), clientConfigFilename);
                System.out.println("Wakeup");

                client1.connect(seedAddresses1.get(0));

                for (int i = 1; i < getNumParts(file); ++i) {
                    client1.executeGet(fileId, i, client1File.getAbsolutePath());
                    client2.executeGet(fileId, i, client2File.getAbsolutePath());
                }
                System.out.println("Checking correctness...");
                assertTrue(FileUtils.contentEquals(file, client1File));
                assertTrue(FileUtils.contentEquals(file, client2File));
                client2.executeUpdate(); // насильно.
            }
            client1.executeUpdate(); // насильно.
        }

        System.out.println("### WRITING CONFIG FOR TRACKER");
        emptyTracker.writeConfig(tmpFolder.newFile().getAbsolutePath());
        // 2 сценарий:
        //запуск трекера
        //запуск клиента, публикация большого файла
        //запуск второго клиента, старт скачивания этого файла, остановка первого клиента так, чтобы файл не успел скачаться
        //запуск третьего клиента, старт скачивания этого файла, проверка, что состояние файла у второго и третьего клиента одинаковое
        //запуск первого клиента, докачивание файла вторым и третьим клиентом, проверка на корректность
    }

    @After
    public void tearDown() throws Exception {
        emptyTracker.close();
    }

    private Client getEmptyClient() throws IOException, NIOException, InterruptedException {
        Client client = new Client();
        for (int i = 0;; ++i) {
            try {
                client.bind(getClientAddress());
                break;
            } catch (BindException e) {
                LOGGER.debug("Binding try #" + i + " failed");
            }
        }
        client.connectToTracker(emptyTracker.getAddress());
        Thread.sleep(OPERATION_TIMEOUT); // wait for hand-shaking to happen.
        return client;
    }
}
