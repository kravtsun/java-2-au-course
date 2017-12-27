package ru.spbau.mit.torrent;

import org.apache.logging.log4j.core.util.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static ru.spbau.mit.torrent.TestCommons.*;

import java.io.File;
import java.lang.reflect.Field;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.util.*;

public class TrackerTest {
    private Tracker tracker;
    private InetSocketAddress listeningAddress;
    private static int fileIdBase;

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
            InetSocketAddress currentClientAddress = getClientAddress(fileIdBase / fileHitCount);
            try (Client client = new Client(currentClientAddress, tracker.getAddress(), null)) {
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
