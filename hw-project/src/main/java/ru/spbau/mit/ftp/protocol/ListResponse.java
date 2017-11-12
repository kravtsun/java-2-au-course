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
    private String[] fileNames;

    public ListResponse() {}

    public ListResponse(File[] files) {
        final Function<File, String> filePrinter = (file) -> {
            int isDirectory = file.isDirectory() ? 1 : 0;
            return file.getName() + " " + isDirectory;
        };
        if (files != null) {
            fileNames = new String[files.length];
            for (int i = 0; i < files.length; i++) {
                fileNames[i] = filePrinter.apply(files[i]);
            }
        } else {
            fileNames = new String[0];
        }
        setInitialized();
    }

    @Override
    public void write(WritableByteChannel out) throws IOException {
        checkForNonEmptyness();
        writeInt(out, fileNames.length);
        for (String filename : fileNames) {
            writeString(out, filename);
        }
    }

    @Override
    public void read(ReadableByteChannel in) throws IOException {
        checkForEmptyness();
        int count = readInt(in);
        fileNames = new String[count];
        for (int i = 0; i < count; ++i) {
            fileNames[i] = readString(in);
        }
    }

    @Override
    public String debugString() {
        if (fileNames == null) {
            return null;
        }
        return fileNames.length + "\n" + Arrays.stream(fileNames).collect(Collectors.joining("\n"));
    }

    public String[] getFileNames() {
        return fileNames;
    }
}
