package ru.spbau.mit.torrent;

public class ClientException extends RuntimeException {
    ClientException(String message) {
        super(message);
    }
}
