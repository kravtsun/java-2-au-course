package ru.spbau.mit.torrent;

import java.io.IOException;

public interface Configurable {
    void readConfig(String configFilename) throws IOException;

    void writeConfig(String configFilename) throws IOException;
}
