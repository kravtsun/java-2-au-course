package ru.spbau.mit.torrent;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.BindException;

import static ru.spbau.mit.torrent.TestCommons.*;

public class ClientTest {
    private Tracker tracker;

    @Before
    public void setUp() throws Exception {
        while (true) {
            try {
                tracker = new Tracker(getTrackerAddress());
            } catch (BindException ignored) {
                Thread.sleep(BIND_TIMEOUT);
                continue;
            }
            break;
        }
    }

    @Test
    public void clientFailsOnTrackerDisconnected() throws Exception {
        Client client = new Client();
        client.bind(getClientAddress(0));
        client.connectToTracker(tracker.getAddress());
        Thread.sleep(500); // wait for hand-shaking to happen.
        client.close();
        tracker.close();
    }

    @After
    public void tearDown() throws Exception {
        tracker.close();
    }
}
