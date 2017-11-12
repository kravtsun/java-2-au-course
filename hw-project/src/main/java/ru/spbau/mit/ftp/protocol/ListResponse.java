package ru.spbau.mit.ftp.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
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
    public void write(DataOutputStream out) throws IOException {
        checkForNonEmptyness();
        out.writeInt(fileNames.size());
        for (String filename : fileNames) {
            out.writeUTF(filename);
        }
    }

    @Override
    public void read(DataInputStream in) throws IOException {
        checkForEmptyness();
        int count = in.readInt();
        fileNames = new ArrayList<String>();
        for (int i = 0; i < count; ++i) {
            fileNames.add(in.readUTF());
        }
    }

    @Override
    public String debugString() {
        if (fileNames == null) {
            return null;
        }
        return fileNames.stream().collect(Collectors.joining("\n"));
    }
}
