package ru.spbau.mit.torrent;

import org.apache.logging.log4j.core.util.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import java.io.File;
import java.lang.reflect.Field;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.util.*;

public class TrackerTest {
    static final int TIME_LIMIT = 2000;
    static final int BIND_TIMEOUT = 100;
    static final String TEST_DIR = "test";
    static final int DEFAULT_PORT = 8081;
    private Tracker tracker;
    private InetSocketAddress listeningAddress;

    static final String HOSTNAME = "localhost";
    static final String EMPTY_CONFIG = Paths.get(TEST_DIR, "empty_tracker.config").toString();
    static final List<Integer> CLIENT_PORTS = Arrays.asList(8082, 8083, 8084, 8085);

    static final File EMPTY_FILE = new File(Paths.get(TEST_DIR, "empty.txt").toString());
    static final File CRLF_FILE = new File(Paths.get(TEST_DIR, "crlf.txt").toString());

    private static int fileIdBase;

    static InetSocketAddress getClientAddress(int index) throws UnknownHostException {
        return new InetSocketAddress(InetAddress.getByName(HOSTNAME), CLIENT_PORTS.get(index));
    }

    static InetSocketAddress getTrackerAddress() throws UnknownHostException {
        return new InetSocketAddress(InetAddress.getByName(HOSTNAME), DEFAULT_PORT);
    }

    @Before
    public void setUp() throws Exception {
        listeningAddress = getTrackerAddress();
        while (true) {
            try {
                tracker = new Tracker(listeningAddress, null);
            } catch (BindException ignored) {
                continue;
            }
            break;
        }

        fileIdBase = 0;
    }

    @After
    public void tearDown() throws Exception {
        tracker.readConfig(EMPTY_CONFIG);
        tracker.close();
        Thread.sleep(BIND_TIMEOUT);
    }

    private Object getTrackerFieldCollection(String fieldString) throws ReflectiveOperationException {
        Field fileProxiesField = tracker.getClass().getDeclaredField(fieldString);
        fileProxiesField.setAccessible(true);
        return fileProxiesField.get(tracker);
    }

    private List getFileProxies() throws ReflectiveOperationException {
        return (List) getTrackerFieldCollection("fileProxies");
    }

    private HashMap getFileSeeds() throws ReflectiveOperationException {
        return (HashMap) getTrackerFieldCollection("fileSeeds");
    }

    @Test(timeout = TIME_LIMIT)
    public void cleanStart() throws Exception {
        Assert.isEmpty(tracker.list());
        assertTrue(getFileProxies().isEmpty());
        assertTrue(getFileSeeds().isEmpty());
//        tracker.writeConfig(EMPTY_CONFIG);
    }

    @Test(expected = TrackerException.class, timeout = TIME_LIMIT)
    public void sourcesFailsOnNonExistentFile() throws Exception {
        cleanStart();
        Assert.isEmpty(tracker.sources(-1));
        cleanStart();
    }

    @Test(timeout = TIME_LIMIT)
    public void emptyConfigLoading() throws Exception {
        cleanStart();
        tracker.readConfig(EMPTY_CONFIG);
        cleanStart();
    }

    @Test(timeout = TIME_LIMIT)
    public void emptyConfigStoring() throws Exception {
        cleanStart();
        tracker.writeConfig(EMPTY_CONFIG);
        cleanStart();
        tracker.readConfig(EMPTY_CONFIG);
        cleanStart();
    }

    private void uploadFile(File file) throws Exception {
        final int fileHitCount = 3;
        while (true) {
            try (Client client = new Client(getClientAddress(fileIdBase / fileHitCount), tracker.getAddress(), null)) {
                for (int i = 0; i < fileHitCount; i++) {
                    int fileId = tracker.upload(client.getAddress(), file.toString(), file.length());
                    int expectedId = i + fileIdBase;
                    assertEquals(expectedId, fileId);
                    FileProxy fileProxy = tracker.list().get(expectedId);
                    assertEquals(fileProxy.getName(), file.toString());
                    assertEquals(fileProxy.getSize(), file.length());
                    assertEquals(Collections.singletonList(client.getAddress()), tracker.sources(fileId));
                }
            } catch (BindException ignored) {
                Thread.sleep(BIND_TIMEOUT);
                continue;
            }
            break;
        }
        fileIdBase += fileHitCount;
        List<FileProxy> fileProxies = tracker.list();
        assertEquals(fileIdBase, fileProxies.size());
    }

    @Test(timeout = TIME_LIMIT)
    public void upload() throws Exception {
        uploadFile(CRLF_FILE);
        uploadFile(EMPTY_FILE);
    }
}