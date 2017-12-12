package ru.spbau.mit.ftp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.spbau.mit.ftp.protocol.*;

import java.io.File;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerSession implements Runnable {
    private static final Logger LOGGER = LogManager.getLogger("session");
    private static final AtomicInteger SESSIONS_COUNT = new AtomicInteger(0);
    private final SocketChannel socketChannel;
    private final int sessionId;

    ServerSession(SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
        this.sessionId = SESSIONS_COUNT.incrementAndGet();
    }

    @Override
    public void run() {
        try {
            EchoResponse.INIT_RESPONSE.write(socketChannel);
            LOGGER.info(logMessage("starting IO loop"));
            while (true) {
                Request request = Request.parse(socketChannel);
                Response response = dealRequest(request);
                response.write(socketChannel);
                LOGGER.debug(logMessage("sent: " + response + ": " + response.debugString()));
                if (response.equals(EchoResponse.EXIT_RESPONSE)) {
                    break;
                }
            }
            LOGGER.info(logMessage("closing socket"));
        } catch (Exception e) {
            LOGGER.error(logMessage(e.toString()));
        } finally {
            try {
                if (socketChannel.isOpen()) {
                    EchoResponse.EXIT_RESPONSE.write(socketChannel);
                    socketChannel.close();
                }
            } catch (IOException e) {
                LOGGER.error(logMessage(e.toString()));
            }
        }
        LOGGER.info(logMessage("exiting..."));
    }

    private Response dealRequest(Request request) throws AbstractServer.ServerException {
        LOGGER.info(logMessage("received: " + request + ": " + request.debugString()));
        Response response;
        if (request instanceof EchoRequest) {
            String receivedMessage = ((EchoRequest) request).getMessage();
            if (receivedMessage.equals(EchoRequest.EXIT_MESSAGE)) {
                response = EchoResponse.EXIT_RESPONSE;
            } else {
                response = new EchoResponse(receivedMessage);
            }
        } else if (request instanceof ListRequest) {
            String path = ((ListRequest) request).getPath();
            File[] files = new File(path).listFiles();
            response = new ListResponse(files);
        } else if (request instanceof GetRequest) {
            String path = ((GetRequest) request).getPath();
            response = GetResponse.serverGetResponse(path);
        } else {
            throw new AbstractServer.ServerException("Unknown request: " + request);
        }
        return response;
    }

    private String logMessage(String message) {
        return String.format("#%d: %s", sessionId, message);
    }
}
