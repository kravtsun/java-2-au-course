package ru.spbau.mit.torrent;

import java.util.List;

public interface FileParter {
    // backdoors for ClientSession.
    FileProxy getFile(int fileId);

    List<Integer> getFileParts(int fileId);
}
