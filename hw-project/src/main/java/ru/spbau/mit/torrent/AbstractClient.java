package ru.spbau.mit.torrent;

import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.List;

public interface AbstractClient {

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

    List<FileProxy> executeList() throws Exception;

    List<InetSocketAddress> executeSources(int fileId) throws Exception;

    // Client-specific requests.
    int executeUpload(String filename) throws Exception;

    List<Integer> executeStat(AsynchronousSocketChannel in, int fileId) throws Exception;

    void executeGet(AsynchronousSocketChannel in, int fileId, int part) throws Exception;

    boolean executeUpdate() throws Exception;
}
