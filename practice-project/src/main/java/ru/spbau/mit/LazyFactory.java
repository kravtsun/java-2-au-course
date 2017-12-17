package ru.spbau.mit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class LazyFactory {
    private static abstract class LazyMarked<T> implements Lazy<T> {
        protected volatile boolean isFirstRun;
        protected T result;

        LazyMarked() {
            this.isFirstRun = true;
        }
    }

    // Для однопоточного режима
    public static <T> Lazy<T> createLazySingleThreaded(Supplier<T> supplier) {
        return new LazyMarked<T>() {
            @Override
            public T get() {
                if (isFirstRun) {
                    result = supplier.get();
                    isFirstRun = false;
                }
                return result;
            }
        };
    }

    // Для многопоточного режима; вычисление не должно производиться > 1 раза (см. Singleton)
    public static <T> Lazy<T> createLazyMultiThreaded(Supplier<T> supplier) {
        return new LazyMarked<T>() {
            @Override
            public T get() {
                // double-checked locking.
                if (isFirstRun) {
                    synchronized (this) {
                        if (isFirstRun) {
                            result = supplier.get();
                            isFirstRun = false;
                        }
                    }
                }
                return result;
            }
        };
    }

    // Для многопоточного lock-free режима; вычисление может производиться > 1 раза,
    // но при этом Lazy.get всегда должен возвращать один и тот же объект
    // (см. AtomicReference/AtomicReferenceFieldUpdater)
    public static <T> Lazy<T> createLazyLockFree(Supplier<T> supplier) {
        return new Lazy<T>() {
            final private AtomicReference<T> result = new AtomicReference<>();

            @Override
            public T get() {
                result.compareAndSet(null, supplier.get());
                return result.get();
            }
        };
    }
}
