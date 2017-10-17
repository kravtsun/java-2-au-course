package ru.spbau.mit;

// java.util.concurrent.Callable alternative.
@FunctionalInterface
interface Callable<R> {
    R call() throws Exception;
}
