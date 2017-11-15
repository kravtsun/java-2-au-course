package ru.spbau.mit.ftp;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import ru.spbau.mit.ftp.AbstractServer.ServerException;
import ru.spbau.mit.ftp.protocol.EchoRequest;
import ru.spbau.mit.ftp.protocol.EchoResponse;
import ru.spbau.mit.ftp.protocol.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class ServerTest {
    public static final String HOST_NAME = "localhost";
    public static final int PORT = 13235;
    public static final int TIMEOUT = 5000;
    private final int nclients;

    private AbstractServer server;

    public ServerTest(int nclients) {
        this.nclients = nclients;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> nclients() {
        return Arrays.asList(new Object[][]{
                {1}, {5}, {10}, {30}
        });
    }

    @Before
    public void setUp() throws Exception {
        server = new Server();
    }

    @After
    public void tearDown() throws Exception {
        server.close();
    }

    @Test(timeout = TIMEOUT)
    public void simpleStartStopTest() throws Exception {
        server.start(HOST_NAME, PORT);
        smallWork(PORT);
        server.stop();
    }

    @Test(timeout = TIMEOUT)
    public void notStoppedServerTest() throws Exception {
        server.start(HOST_NAME, PORT);
        smallWork(PORT);
    }

    @Test(timeout = TIMEOUT)
    public void simpleClientSessionTest() throws Exception {
        server.start(HOST_NAME, PORT);
        simpleSession();
        server.stop();
    }

    @Test(timeout = TIMEOUT)
    public void simpleClientSessionNotStoppedTest() throws Exception {
        server.start(HOST_NAME, PORT);
        simpleSession();
    }

    @Test
    public void rebindServerTest() throws Exception {
        server.start(HOST_NAME, PORT);
        smallWork(PORT);
        server.stop();
        int secondPort = PORT + 1;
        server.start(HOST_NAME, secondPort);
        smallWork(secondPort);
    }

    @Test(expected = ServerException.class)
    public void rebindServerCausesErrorTest() throws Exception {
        server.start(HOST_NAME, PORT);
        server.start(HOST_NAME, PORT + 1);
    }

    @Test(timeout = TIMEOUT, expected = IOException.class)
    public void otherServerSamePortTest() throws Exception {
        server.start(HOST_NAME, PORT);
        try (Server otherServer = new Server()) {
            otherServer.start(HOST_NAME, PORT);
        }
    }

    @Test
    public void otherServerSamePortAfterStopTest() throws Exception {
        server.start(HOST_NAME, PORT);
        smallWork(PORT);
        server.stop();
        try (Server otherServer = new Server()) {
            otherServer.start(HOST_NAME, PORT);
            smallWork(PORT);
            otherServer.stop();
        }
    }

    private void simpleSession() throws Exception {
        try (Client client = new Client()) {
            assertEquals(EchoResponse.INIT_RESPONSE, client.connect(HOST_NAME, PORT));
            assertEquals(new EchoResponse("hello"), client.executeEcho("hello"));
            client.executeEcho(EchoRequest.EXIT_MESSAGE);
        }
    }

    private void smallWork(int port) throws Exception {
        ExecutorService clientPool = Executors.newFixedThreadPool(nclients);
        List<Future> futures = new ArrayList<>();
        for (int i = 0; i < nclients; i++) {
            futures.add(clientPool.submit(() -> {
                try (Client client = new Client()) {
                    assertEquals(EchoResponse.INIT_RESPONSE, client.connect(HOST_NAME, port));
                    Thread.sleep(100);
                    Response response = client.executeEcho(EchoResponse.EXIT_RESPONSE.debugString());
                    assertEquals(EchoResponse.EXIT_RESPONSE, response);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }
        clientPool.shutdown();
        while (!clientPool.isShutdown()) {
            clientPool.awaitTermination(100, TimeUnit.MILLISECONDS);
        }

        // collecting exceptions to throw outside.
        for (Future future : futures) {
            future.get();
        }
    }
}
