package ru.spbau.mit.torrent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;
import java.util.List;

public abstract class AbstractTracker {
    /**
     * Хранит мета-информацию о раздаваемых файлах:

     идентификатор
     активные клиенты (недавно был update), у которых есть этот файл целиком или некоторые его части
     Порт сервера: 8081

     Запросы:

     list — список раздаваемых файлов
     upload — публикация нового файла
     sources — список клиентов, владеющих определенным файлов целиком или некоторыми его частями
     update — загрузка клиентом данных о раздаваемых файлах

     В предложенной реализации предполагается, что торрент-трекер один на всё приложение.
     */

    private static final Logger LOGGER = LogManager.getLogger("server");

    /**
     * Формат запроса:
     <1: Byte>
     * Формат ответа:
     * <count: Int> (<id: Int> <name: String> <size: Long>)*,
     count — количество файлов
     id — идентификатор файла
     name — название файла
     size — размер файла
     * @return
     */
    public abstract List<FileProxy> list();

    /**
     * Формат запроса:
     <2: Byte> <name: String> <size: Long>,
     name — название файла
     size — размер файла

     * Формат ответа:
     <id: Int>,
     id — идентификатор файла
     * @param filename
     * @param size
     * @return
     */
    public abstract int upload(String filename, long size) throws Exception;
//        FileProxy file = new FileProxy(filename, size);
//        throw new Exception("Not implemented");
//        return file.getId();
//    }

    /**
     * Формат запроса:
     <3: Byte> <id: Int>,
     id — идентификатор файла

     * Формат ответа:
     <size: Int> (<ip: ByteByteByteByte> <clientPort: Short>)*,
     size — количество клиентов, раздающих файл
     ip — ip клиента,
     clientPort — порт клиента
     * @param fileId
     * @return
     */
    public abstract List<InetSocketAddress> sources(int fileId);

    /**
     * Формат запроса:
     <4: Byte> <clientPort: Short> <count: Int> (<id: Int>)*,
     clientPort — порт клиента,
     count — количество раздаваемых файлов,
     id — идентификатор файла

     * Формат ответа:
     <status: Boolean>,
     status — True, если информация успешно обновлена
     * @param clientPort
     * @param fileIds
     * @return
     */
    // Клиент обязан исполнять данный запрос каждые 5 минут,
    // иначе сервер считает, что клиент ушел с раздачи
    // Нужно ли этот запрос клиенту исполнять каждый раз, когда он получил новую часть какого-то файла?
    // наверно логичнее выполнять его только после того как получена первая (какая-то часть),
    // и далее каждые 5 минут.
    public abstract boolean update(int clientPort, List<Integer> fileIds);
}
