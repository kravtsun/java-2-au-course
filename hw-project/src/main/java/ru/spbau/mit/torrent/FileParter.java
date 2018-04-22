package ru.spbau.mit.torrent;

import java.io.File;
import java.util.List;

public interface FileParter {
    // backdoors for ClientSession.
    FileProxy getFile(int fileId);

    File fileFromProxy(FileProxy fileProxy);

    List<Integer> getFileParts(int fileId);
}
