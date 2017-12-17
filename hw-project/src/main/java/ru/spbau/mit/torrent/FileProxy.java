package ru.spbau.mit.torrent;

import java.util.concurrent.atomic.AtomicInteger;

public class FileProxy {
    private static final AtomicInteger fileId = new AtomicInteger(0);
    private final int id;
    private final String name;
    private final long size;

    FileProxy(int id, String name, long size) {
        this.id = id;
        this.name = name;
        this.size = size;
    }

    FileProxy(String name, long size) {
        this(fileId.getAndIncrement(), name, size);
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
