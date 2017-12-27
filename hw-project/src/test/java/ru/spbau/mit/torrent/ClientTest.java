package ru.spbau.mit.torrent;

import org.junit.After;
import org.junit.Before;

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

    @After
    public void tearDown() throws Exception {
        tracker.close();
    }
}
