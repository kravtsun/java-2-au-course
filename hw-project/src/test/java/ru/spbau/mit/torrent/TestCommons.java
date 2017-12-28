package ru.spbau.mit.torrent;

import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

final class TestCommons {
    static final int TIME_LIMIT = 2000;
    static final int BIND_TIMEOUT = 100;
    static final String TEST_DIR = "src/test/resources";
    static final int DEFAULT_PORT = 8081;
    static final String HOSTNAME = "localhost";
    static final String EMPTY_CONFIG = Paths.get(TEST_DIR, "empty_tracker.config").toString();
    static final AtomicInteger CLIENT_PORT = new AtomicInteger(8082);

    static final File EMPTY_FILE = new File(Paths.get(TEST_DIR, "empty.txt").toString());
    static final File CRLF_FILE = new File(Paths.get(TEST_DIR, "crlf.txt").toString());

    static InetSocketAddress getClientAddress() throws UnknownHostException {
        return new InetSocketAddress(InetAddress.getByName(HOSTNAME), CLIENT_PORT.getAndIncrement());
    }

    static InetSocketAddress getTrackerAddress() throws UnknownHostException {
        return new InetSocketAddress(InetAddress.getByName(HOSTNAME), DEFAULT_PORT);
    }

    private TestCommons() {}
}
