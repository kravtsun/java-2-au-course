package ru.spbau.mit;
/**
 *   Структура должна предоставлять гарантию lock-free.
 * Для понятия "lock-free" мы будем использовать классическое определение:
 * структура данных называется lock-free,
 * если при работе с ней можно в любое время остановить произвольный поток,
 * и при остальные потоки продолжат прогрессировать.
 *   Для примера: реализация с помощью ключевого слова synchronized не является lock-free,
 * поскольку если мы остановим поток, захвативший блокировку,
 * то остальные потоки не смогут продолжать работу со списком.
 * @param <T>
 */

public interface LockFreeList<T> {
    boolean isEmpty();

    /**
     * Appends value to the end of list
     */
    void append(T value);

    /**
     *
     * @param value
     * @return true if value was present.
     */
    boolean remove(T value);

    /**
     * Does one-pass (returns on first match)
     * and answers the question:
     * "Have one met this value while this pass lasted?"
     * @param value to search for.
     * @return true
     */
    boolean contains(T value);
}