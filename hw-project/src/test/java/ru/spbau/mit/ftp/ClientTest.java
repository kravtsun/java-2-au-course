package ru.spbau.mit.ftp;

import org.junit.*;
import ru.spbau.mit.ftp.AbstractClient.ClientNotConnectedException;
import ru.spbau.mit.ftp.protocol.EchoResponse;
import ru.spbau.mit.ftp.protocol.GetResponse;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.commons.io.FileUtils.contentEquals;
import static org.junit.Assert.*;
import static ru.spbau.mit.ftp.ServerTest.HOST_NAME;
import static ru.spbau.mit.ftp.ServerTest.PORT;
import static ru.spbau.mit.ftp.ServerTest.TIMEOUT;

public class ClientTest {
    public static final String RESULT_SUFFIX = ".result";
    private static final int FIRST_PORT = PORT;
    private static final int SECOND_PORT = PORT + 1;
    private static final String NON_EXISTENT_FILE_PATH = "non_existent_file.txt";
    private static final String TEST_DIR_PATH = "test_files";
    private static Server firstServer;
    private static Server secondServer;
    private Client client;

    @BeforeClass
    public static void beforeClass() throws Exception {
        firstServer = new Server();
        firstServer.start(HOST_NAME, FIRST_PORT);
        secondServer = new Server();
        secondServer.start(HOST_NAME, SECOND_PORT);
    }

    @AfterClass
    public static void afterClass() throws Exception {
        if (secondServer != null) {
            secondServer.stop();
        }
        if (firstServer != null) {
            firstServer.stop();
        }
    }

    @Before
    public void setUp() throws Exception {
        client = new Client();
    }

    @After
    public void tearDown() throws Exception {
        if (client != null && client.isConnected()) {
            assertEquals(EchoResponse.EXIT_RESPONSE, client.executeExit());
            client.close();
        }
    }

    @Test
    public void simpleConnect() throws Exception {
        assertEquals(EchoResponse.INIT_RESPONSE, client.connect(HOST_NAME, FIRST_PORT));
    }

    @Test
    public void repeatConnectTest() throws Exception {
        assertEquals(EchoResponse.INIT_RESPONSE, client.connect(HOST_NAME, FIRST_PORT));
        client.disconnect();
        assertEquals(EchoResponse.INIT_RESPONSE, client.connect(HOST_NAME, FIRST_PORT));
    }

    @Test
    public void repeatConnectToOtherServerTest() throws Exception {
        assertEquals(EchoResponse.INIT_RESPONSE, client.connect(HOST_NAME, FIRST_PORT));
        client.disconnect();
        assertEquals(EchoResponse.INIT_RESPONSE, client.connect(HOST_NAME, SECOND_PORT));
    }

    @Test(timeout = TIMEOUT, expected = IllegalArgumentException.class)
    public void connectFailsOnWrongHost() throws Exception {
        client.connect("dummy_host", FIRST_PORT);
    }

    @Test(timeout = TIMEOUT, expected = IOException.class)
    public void connectFailsOnWrongPort() throws Exception {
        // hoping SECOND_PORT + 1 is not busy.
        client.connect(HOST_NAME, SECOND_PORT + 1);
    }

    @Test(timeout = TIMEOUT)
    public void simpleDisconnect() throws Exception {
        client.connect(HOST_NAME, FIRST_PORT);
        client.disconnect();
    }

    @Test(timeout = TIMEOUT)
    public void disconnectPassesOnFreshClientTest() throws Exception {
        client.disconnect();
    }

    @Test(timeout = TIMEOUT)
    public void closePassesOnFreshClientTest() throws Exception {
        client.close();
    }

    @Test(timeout = TIMEOUT)
    public void closePassesTwiceTest() throws Exception {
        client.connect(HOST_NAME, FIRST_PORT);
        client.close();
        client.close();
    }

    @Test(timeout = TIMEOUT)
    public void executeList() throws Exception {
        client.connect(HOST_NAME, FIRST_PORT);
        List<String> srcFiles = Arrays.asList(
                "main 1",
                "test 1"
        );
        List<String> filenamesList = listResult("src");
        assertEquals(srcFiles, filenamesList);
    }

    @Test(timeout = TIMEOUT)
    public void executeHardList() throws Exception {
        client.connect(HOST_NAME, FIRST_PORT);
        List<String> srcFiles = Arrays.asList(
                "\" 0",
                "a.out 0",
                "first\nsecond 0",
                "mandarin 0",
                "русский 0"
        );
        List<String> filenamesList = listResult(TEST_DIR_PATH);
        assertEquals(srcFiles, filenamesList);
    }

    @Test(timeout = TIMEOUT)
    public void executeListOnNonExistentTest() throws Exception {
        client.connect(HOST_NAME, FIRST_PORT);
        String[] filenames = client.executeList(NON_EXISTENT_FILE_PATH).getFileNames();
        assertArrayEquals(new String[0], filenames);
    }

    @Test(timeout = TIMEOUT)
    public void executeListOnFileTest() throws Exception {
        final String textFilePath = "src/main/java/ru/spbau/mit/ftp/protocol/Response.java";
        assertTrue(new File(textFilePath).exists());
        client.connect(HOST_NAME, FIRST_PORT);
        String[] filenames = client.executeList(textFilePath).getFileNames();
        assertArrayEquals(new String[0], filenames);
    }

    @Test(timeout = TIMEOUT)
    public void executeGetTest() throws Exception {
        client.connect(HOST_NAME, FIRST_PORT);
        File[] listFiles = new File(TEST_DIR_PATH).listFiles();
        assertNotNull(listFiles);
        Arrays.stream(listFiles).forEach((file) -> {
            String path = file.getPath();
            System.out.println(file.getName());
            try {
                File tmpFile = File.createTempFile("get_", RESULT_SUFFIX);
                GetResponse response = client.executeGet(path, tmpFile.getPath());
                assertEquals(tmpFile.getPath(), response.debugString());
                assertTrue(contentEquals(tmpFile, file));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test(timeout = TIMEOUT)
    public void executeGetOnNonExistentTest() throws Exception {
        client.connect(HOST_NAME, FIRST_PORT);
        File resultFile = File.createTempFile("empty_", RESULT_SUFFIX);
        assertTrue(resultFile.exists());
        GetResponse response = client.executeGet(NON_EXISTENT_FILE_PATH, resultFile.getPath());
        assertEquals(resultFile.getPath(), response.debugString());
        File otherEmptyFile = File.createTempFile("empty_", ".txt");
        assertTrue(otherEmptyFile.exists());
        assertTrue(contentEquals(resultFile, otherEmptyFile));
    }

    @Test(timeout = TIMEOUT)
    public void executeEcho() throws Exception {
        client.connect(HOST_NAME, FIRST_PORT);
        String message = "echo";
        assertEquals(message, client.executeEcho(message).debugString());
    }

    private List<String> listResult(String path) throws Exception {
        return Arrays.stream(client.executeList(path).getFileNames())
                .sorted()
                .collect(Collectors.toList());
    }

    @Test(expected = ClientNotConnectedException.class)
    public void unconnectedClientFailsOnList() throws Exception {
        client.executeList(TEST_DIR_PATH);
    }

    @Test(expected = ClientNotConnectedException.class)
    public void unconnectedClientFailsOnGet() throws Exception {
        File resultFile = File.createTempFile("fail_", RESULT_SUFFIX);
        client.executeGet("test/mandarin", resultFile.getPath());
    }

    @Test(expected = ClientNotConnectedException.class)
    public void unconnectedClientFailsOnEcho() throws Exception {
        client.executeEcho("echo");
    }

    @Test
    public void unconnectedClientDoesntFailsOnExit() throws Exception {
        client.executeExit();
    }
}
