package ru.spbau.mit.ftp;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import ru.spbau.mit.ftp.protocol.EchoResponse;
import ru.spbau.mit.ftp.protocol.GetResponse;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.apache.commons.io.FileUtils.contentEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static ru.spbau.mit.ftp.ClientTest.RESULT_SUFFIX;
import static ru.spbau.mit.ftp.ServerTest.HOST_NAME;
import static ru.spbau.mit.ftp.ServerTest.PORT;

public class BigFileTest {
    private static File bigFile;
    private Server server;
    private Client client;

    @BeforeClass
    public static void beforeClass() throws Exception {
        long bigFileSize = 100_000_000;
        bigFile = createBigFile(bigFileSize);
        assertTrue(bigFile.exists());
    }

    @Before
    public void setUp() throws Exception {
        server = new Server();
        server.start(HOST_NAME, PORT);
        client = new Client();
        assertEquals(EchoResponse.INIT_RESPONSE, client.connect(HOST_NAME, PORT));
    }

    @After
    public void tearDown() throws Exception {
        client.disconnect();
        server.close();
    }

    @Test
    public void singleClientGetTest() throws Exception {
        getBigFileTest(client);
    }

    public static void getBigFileTest(Client client) throws Exception {
        File tmpFile = File.createTempFile("big_", RESULT_SUFFIX);
        GetResponse response = client.executeGet(bigFile.getPath(), tmpFile.getPath());
        assertEquals(tmpFile.getPath(), response.debugString());
        assertTrue(contentEquals(tmpFile, bigFile));
    }

    @Test
    public void severalClientsGetBigFileTest() throws Exception {
        int numClients = 4;
        ExecutorService executor = Executors.newFixedThreadPool(numClients);
        AtomicBoolean isFinished = new AtomicBoolean(false);
        Future[] futures = new Future[numClients];
        CountDownLatch countDownLatch = new CountDownLatch(numClients);
        for (int i = 0; i < numClients; i++) {
            int finalI = i;
            futures[i] = executor.submit(() -> {
                int count = 0;
                try (Client client = new Client()) {

                    assertEquals(EchoResponse.INIT_RESPONSE, client.connect(HOST_NAME, PORT));
                    while (!isFinished.get()) {
                        getBigFileTest(client);
                        Thread.yield();
                        count++;
                    }
                    System.out.println(String.format("Client #%d exited", finalI));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                countDownLatch.countDown();
                return count;
            });
        }
        Thread.sleep(3000);
        isFinished.set(true);
        countDownLatch.await();

        List<Integer> results = Arrays.stream(futures).map((f) -> {
            try {
                return (Integer) f.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());
        assertTrue(results.stream().allMatch((i) -> i > 0));
    }

    private static File createBigFile(long size) throws Exception {
        File bigFile = File.createTempFile("big_", ".in");
        FileChannel bigFileChannel = new FileOutputStream(bigFile).getChannel();
        Random r = new Random();
        int blockSize = 256;
        ByteBuffer buffer = ByteBuffer.allocate(blockSize);
        assertTrue(buffer.hasArray());
        buffer.clear();
        for (int i = 0; i < size / blockSize; ++i) {
            r.nextBytes(buffer.array());
            buffer.position(0);
            buffer.limit(blockSize);
            bigFileChannel.write(buffer);
        }

        return bigFile;
    }
}
