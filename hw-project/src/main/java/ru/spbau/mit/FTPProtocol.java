package ru.spbau.mit;

import java.io.*;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

// TODO remove FTP prefix and move FTPProtocol entities to protocol subpackage.
// TODO abstract FTPProtocol
class FTPProtocol {
    public static class FTPProtocolException extends Exception {
        FTPProtocolException(String message) {
            super(message);
        }
    }

    public interface SentEntity {
        String str();

//        default byte []bytes() {
//            return null;
//        }
    }

    public static abstract class Request implements SentEntity {

        @Override
        public String str() {
            return code() + " " + requestBody();
        }

        public abstract int code();

        public abstract String requestBody();
    }

    public static class SimpleRequest extends Request {
        private final String message;

        SimpleRequest(String message) {
            this.message = message;
        }

        @Override
        public int code() {
            return 0;
        }

        @Override
        public String requestBody() {
            return message;
        }
    }

    public static class ListRequest extends Request {
        private final String path;

        ListRequest(String path) {
            this.path = path;
        }

        @Override
        public int code() {
            return 1;
        }

        @Override
        public String requestBody() {
            return getPath();
        }

        public String getPath() {
            return path;
        }
    }

    public static Request parseRequest(String message) throws FTPProtocolException {
        String codeString = message.substring(0, 1);
        int code = Integer.parseInt(codeString);
        if (message.length() < 2 || message.charAt(1) != ' ') {
            throw new FTPProtocolException("Invalid request format for request: " + message);
        }
        String otherMessage = message.substring(2);
        if (code == 0) {
            return new SimpleRequest(otherMessage);
        } else if (code == 1) {
            return new ListRequest(otherMessage);
        } else {
            throw new FTPProtocolException("invalid request code: " + code);
        }
    }


    public static class SimpleResponse implements SentEntity {
        private final String message;

        SimpleResponse(String message) {
            this.message = message;
        }

        @Override
        public String str() {
            return message;
        }
    }

    public static class ListResponse implements SentEntity {
        private final File[] files;

        ListResponse(File[] files) {
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
}