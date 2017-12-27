package ru.spbau.mit.torrent;

import java.io.IOException;

public interface Configurable {
    void readConfig(String configFilename) throws IOException, NIOException;

    void writeConfig(String configFilename) throws IOException, NIOException;
}
