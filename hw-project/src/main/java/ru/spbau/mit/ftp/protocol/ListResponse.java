package ru.spbau.mit.ftp.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ListResponse extends Response {
    private List<String> fileNames;

    public ListResponse() {}

    public ListResponse(File[] files) {
        final Function<File, String> filePrinter = (file) -> {
            int isDirectory = file.isDirectory() ? 1 : 0;
            return file.getName() + " " + isDirectory;
        };
        fileNames = Arrays.stream(files)
                .map(filePrinter)
                .collect(Collectors.toList());
        setInitialized();
    }

    @Override
    public void write(WritableByteChannel out) throws IOException {
        checkForNonEmptyness();
        writeInt(out, fileNames.size());
        for (String filename : fileNames) {
            writeString(out, filename);
        }
    }

    @Override
    public void read(ReadableByteChannel in) throws IOException {
        checkForEmptyness();
        int count = readInt(in);
        fileNames = new ArrayList<String>();
        for (int i = 0; i < count; ++i) {
            fileNames.add(readString(in));
        }
    }

    @Override
    public String debugString() {
        if (fileNames == null) {
            return null;
        }
        return fileNames.size() + "\n" + fileNames.stream().collect(Collectors.joining("\n"));
    }
}
