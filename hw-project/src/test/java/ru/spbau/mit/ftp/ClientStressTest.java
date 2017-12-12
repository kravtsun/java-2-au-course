package ru.spbau.mit.ftp;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import ru.spbau.mit.ftp.protocol.EchoResponse;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static ru.spbau.mit.ftp.ServerTest.HOST_NAME;
import static ru.spbau.mit.ftp.ServerTest.PORT;
import static ru.spbau.mit.ftp.ServerTest.TIMEOUT;

@RunWith(Parameterized.class)
public class ClientStressTest {
    private final int nclients;
    private Server server;

    public ClientStressTest(int nclients) {
        this.nclients = nclients;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> nclients() {
        return Arrays.asList(new Object[][]{
                {1}, {5}, {10}, {30}, {100}
        });
    }

    @Before
    public void setUp() throws Exception {
        server = new Server();
        server.start(HOST_NAME, PORT);
    }

    @After
    public void tearDown() throws Exception {
        if (server != null) {
            server.stop();
        }
    }

    @Test(timeout = TIMEOUT)
    public void clientEndsWhenServerClosed() throws Exception {
        try (Client client = new Client()) {
            assertEquals(EchoResponse.INIT_RESPONSE, client.connect(HOST_NAME, PORT));
            Future<Long> future = Executors.newFixedThreadPool(1).submit(() -> {
                String message = "message";
                long count = 0;
                try {
                    while (true) {
                        assertEquals(new EchoResponse(message), client.executeEcho(message));
                        count++;
                    }
                } catch (Exception ignored) {
                    return count;
                }
            });

            // waiting for client to spin.
            Thread.sleep(100);
            server.stop();
            assertTrue(future.get() > 1);
        }
    }

    @Test(timeout = TIMEOUT)
    public void severalClientsTest() throws Exception {
        ExecutorService clientPool = Executors.newFixedThreadPool(nclients);
        AtomicBoolean finished = new AtomicBoolean(false);
        CountDownLatch countDownLatch = new CountDownLatch(nclients);
        int[] count = new int[nclients];
        for (int i = 0; i < nclients; ++i) {
            int finalI = i;
            clientPool.submit(() -> {
                try (Client client = new Client()) {
                    assertEquals(EchoResponse.INIT_RESPONSE, client.connect(HOST_NAME, PORT));
                    while (!finished.get()) {
                        String message = "hello#" + count[finalI];
                        assertEquals(new EchoResponse(message), client.executeEcho(message));
                        count[finalI]++;
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                countDownLatch.countDown();
            });
        }
        Thread.sleep(200);
        finished.set(true);
        countDownLatch.await();
        assertTrue(Arrays
                .stream(count)
                .allMatch((i) -> i > 0));
    }
}
