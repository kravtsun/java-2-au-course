package ru.spbau.mit.torrent;

import java.util.concurrent.atomic.AtomicInteger;

public class FileProxy {
    private static final AtomicInteger fileId = new AtomicInteger(0);
    private final int id;
    private final String name;
    private final long size;

    FileProxy(String name, long size) {
        id = fileId.getAndIncrement();
        this.name = name;
        this.size = size;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getSize() {
        return size;
    }
}
