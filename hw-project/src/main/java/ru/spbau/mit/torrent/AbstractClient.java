package ru.spbau.mit.torrent;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public abstract class AbstractClient implements Closeable {
    private final InetSocketAddress address;

    /**
     *
     - Клиент при подключении отправляет на трекер список раздаваемых им файлов.

     - При скачивании файла клиент получает у трекера информацию о клиентах,
     раздающих файл (сидах), и далее общается с ними напрямую.

     - У отдельного сида можно узнать о том, какие полные части у него есть,
     а также скачать их.

     - После скачивания отдельных блоков некоторого файла клиент становится сидом.

     Torrent-client:
     - Порт клиента указывается при запуске и передается на трекер в рамках запроса update

     - Каждый файл раздается по частям, размер части — константа на всё приложение

     - Клиент хранит и раздает эти самые части

     Запросы:
     - stat — доступные для раздачи части определенного файла
     - get — скачивание части определенного файла

     Этот класс используется для хранения информации на трекере.
     */

    public AbstractClient(InetSocketAddress address) {
        this.address = address;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    // TODO for all those commands from tracker we should implement the same methods in client.
    // doing all with execute prefix for decrease mess-up.
    public abstract List<FileProxy> executeList() throws IOException;

    public abstract List<InetSocketAddress> executeSources(int fileId) throws IOException;

    // Client-specific requests.
    public abstract int executeUpload(String filename) throws IOException;

    public abstract List<Integer> executeStat(AsynchronousSocketChannel in, int fileId) throws IOException;

    public abstract void executeGet(AsynchronousSocketChannel in, int fileId, int part) throws IOException;

    public abstract void proceedGet(AsynchronousSocketChannel out, int fileId, int partId) throws IOException, ExecutionException, InterruptedException;

    public abstract void proceedStat(AsynchronousSocketChannel out, int fileId) throws IOException;

    public abstract boolean executeUpdate() throws IOException;
}
