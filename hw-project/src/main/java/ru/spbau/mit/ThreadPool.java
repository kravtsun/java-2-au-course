package ru.spbau.mit;

import java.util.function.Function;
import java.util.function.Supplier;

public interface ThreadPool {
    // У каждого потока есть два состояния: ожидание задачи / выполнение задачи

    // Задача — вычисление некоторого значения, вызов get у объекта типа Supplier<R>
    // При добавлении задачи, если в пуле есть ожидающий поток,
    // то он должен приступить к ее исполнению.
    // Иначе задача будет ожидать исполнения пока не освободится какой-нибудь поток

    <R> LightFuture<R> addTask(Supplier<R> task);

    //    Метод shutdown должен завершить работу потоков.
    // Для того, чтобы прервать работу потока рекомендуется пользоваться методом Thread.interrupt()
    void shutdown();
}
