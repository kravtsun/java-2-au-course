package ru.spbau.mit.torrent;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.util.Collections;

import static org.junit.Assert.*;
import static ru.spbau.mit.torrent.TestCommons.*;

public class ClientTest {
    private static final Logger LOGGER = LogManager.getLogger("ClientTest");
    private static final long OPERATION_TIMEOUT = 100;
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

    @Test
    public void uploadSmallFileTest() throws Exception {
        tmpFolder.delete();
        tmpFolder.create();
        try (Client client = getEmptyClient()) {
            String filename = CRLF_FILE.getAbsolutePath();
            int fileId = client.executeUpload(filename);
            assertEquals(0, fileId);
            assertEquals(Collections.singletonList(client.getAddress()), client.executeSources(fileId));
            final FileProxy fileProxy = client.getFile(0);
            assertEquals(fileId, fileProxy.getId());
            assertEquals(filename, fileProxy.getName());
            assertEquals(CRLF_FILE.length(), fileProxy.getSize());

            try (Client client1 = getEmptyClient()) {
                client1.connect(client.getAddress());
                assertEquals(Collections.singletonList(fileId), client1.executeStat(fileId));
                final File client1File = tmpFolder.newFile();
                client1.executeGet(fileId, 0, client1File.getAbsolutePath());
                assertTrue(FileUtils.contentEquals(CRLF_FILE, client1File));
                client1.disconnect();

                FileProxy client1FileProxy = client1.getFile(fileId);
                assertEquals(fileProxy.getSize(), client1FileProxy.getSize());
                assertEquals(fileProxy.getId(), client1FileProxy.getId());
                assertEquals(Collections.singletonList(0), client1.getFileParts(fileId));

                final File client1Config = tmpFolder.newFile();
                client1.writeConfig(client1Config.getAbsolutePath());
                client1.initEmpty();
                assertEquals(null, client1.getFile(fileId));
                assertEquals(Collections.emptyList(), client1.getFileParts(fileId));
                client1.readConfig(client1Config.getAbsolutePath());

                client1FileProxy = client1.getFile(fileId);
                assertEquals(fileProxy.getSize(), client1FileProxy.getSize());
                assertEquals(fileProxy.getId(), client1FileProxy.getId());
                assertEquals(Collections.singletonList(0), client1.getFileParts(fileId));
            }
        }
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
