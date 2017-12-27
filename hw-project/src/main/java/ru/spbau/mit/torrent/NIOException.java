package ru.spbau.mit.torrent;

class NIOException extends Exception {
    NIOException(String message) {
        super(message);
    }

    NIOException(Throwable cause) {
        super(cause);
    }

    public NIOException(String message, Throwable cause) {
        super(message, cause);
    }
}
