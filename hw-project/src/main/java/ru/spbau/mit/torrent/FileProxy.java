package ru.spbau.mit.torrent;

import java.util.concurrent.atomic.AtomicInteger;

public class FileProxy implements Cloneable {
    static final AtomicInteger FILE_ID = new AtomicInteger(0);
    private final int id;
    private String name;
    private final long size;

    FileProxy(int id, String name, long size) {
        this.id = id;
        this.name = name;
        this.size = size;
    }

    FileProxy(String name, long size) {
        this(FILE_ID.getAndIncrement(), name, size);
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getSize() {
        return size;
    }

    @Override
    public FileProxy clone() {
        return new FileProxy(id, name, size);
    }

    @Override
    public String toString() {
        return id + " \"" + name + "\": (" + size + " bytes)";
    }
}
