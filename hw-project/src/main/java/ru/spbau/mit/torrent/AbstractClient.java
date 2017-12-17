package ru.spbau.mit.torrent;

import java.io.Closeable;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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

    public abstract boolean isUpdated();

    public abstract void setUpdated();

    public InetSocketAddress getAddress() {
        return address;
    }

    // TODO abstract a separate Client donor interface.
    public abstract List<Integer> stat(int fileId);

    public abstract void get(int fileId, int part, OutputStream out);
}
