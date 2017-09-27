package ru.spbau.mit;

// java.util.concurrent.Callable alternative.
@FunctionalInterface
public interface Callable<R> {
    R call() throws Exception;
}
