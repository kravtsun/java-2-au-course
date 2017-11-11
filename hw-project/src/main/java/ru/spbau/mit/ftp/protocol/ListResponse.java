package ru.spbau.mit.ftp.protocol;

import java.io.File;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ListResponse implements SentEntity {
    private final File[] files;

    public ListResponse(File[] files) {
        this.files = files;
    }

    @Override
    public String str() {
        Function<File, String> filePrinter = (file) -> {
            int isDirectory = file.isDirectory() ? 1 : 0;
            return file.getName() + " " + isDirectory;
        };
        return files.length + " " +
                Arrays.stream(files)
                        .map(filePrinter)
                        .collect(Collectors.joining(" "));
    }
}
