package ru.spbau.mit.torrent;

import java.io.IOException;

public interface Configurable {
    // save configuration file in order to be used for writing configuration on close().
    void setConfig(String configFilename);

    // should be called on readConfig, in order to drop previous configuration state.
    void initEmpty();

    // reads state from configFilename and calls setConfig on success.
    void readConfig(String configFilename) throws IOException, NIOException;

    // saves state to configFilename and calls setConfig on success.
    void writeConfig(String configFilename) throws IOException, NIOException;
}
