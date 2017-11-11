package ru.spbau.mit.ftp.protocol;

public class Protocol {
    public static class FTPProtocolException extends Exception {
        FTPProtocolException(String message) {
            super(message);
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
}