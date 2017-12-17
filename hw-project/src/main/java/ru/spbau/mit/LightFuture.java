package ru.spbau.mit;

import java.util.function.Function;

//    Задачи, принятные к исполнению, представлены в виде объектов интерфейса LightFuture
public interface LightFuture<R> {
    class LightExecutionException extends Exception {
        LightExecutionException(Throwable cause) {
            super(cause);
        }
    }

    // возвращает true, если задача выполнена.
    boolean isReady();

    // Метод get возвращает результат выполнения задачи:
    // - В случае, если соответствующий задаче supplier завершился с исключением,
    // этот метод должен завершиться с исключением LightExecutionException
    // - Если результат еще не вычислен, метод ожидает его и возвращает полученное значение
    R get() throws LightExecutionException;

    //    Метод thenApply — принимает объект типа Function,
    // который может быть применен к результату данной задачи X
    // и возвращает новую задачу Y, принятую к исполнению
    //    Новая задача будет исполнена не ранее, чем завершится исходная
    //    В качестве аргумента объекту Function будет передан результат исходной задачи,
    // и все Y должны исполняться на общих основаниях (т.е. должны разделяться между потоками пула)
    //    Метод thenApply может быть вызван несколько раз

//    java.util.concurrent.ThreadPoolExecutor
//    ThreadPoolExecutor threadPoolExecutor = Executors.newCachedThreadPool();

    <R2> LightFuture<R2> thenApply(Function<? super R, ? extends R2> function);
}
