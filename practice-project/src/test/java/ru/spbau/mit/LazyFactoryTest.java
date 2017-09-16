package ru.spbau.mit;

import org.junit.Test;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.Assert.*;

public class LazyFactoryTest {
    private static final int THREADS_COUNT = 2_000;

    private abstract class RunCounter implements Runnable {
        private int runCount = 0;

        @Override
        public void run() {
            synchronized (this) {
                runCount++;
            }
        }

        int getRunCount() {
            synchronized (this) {
                return runCount;
            }
        }
    }

    private class IntegerRunCounter extends RunCounter implements Supplier<Integer> {
        @Override
        public Integer get() {
            run();
            return super.getRunCount();
        }
    }

    private class VoidRunCounter extends RunCounter implements Supplier<Integer> {

        @Override
        public Integer get() {
            run();
            return null;
        }
    }

    @Test
    public void createLazySingleThreadedInteger() throws Exception {
        IntegerRunCounter integerRunCounter = new IntegerRunCounter();
        Lazy<Integer> integerLazy = LazyFactory.createLazySingleThreaded(integerRunCounter);
        assertEquals(0, integerRunCounter.getRunCount());
        assertEquals((Integer) 1, integerLazy.get());
        assertEquals((Integer) 1, integerLazy.get());
    }

    @Test
    public void createLazySingleThreadedVoid() throws Exception {
        VoidRunCounter voidRunCounter = new VoidRunCounter();
        Lazy<Integer> voidLazy = LazyFactory.createLazySingleThreaded(voidRunCounter);
        assertEquals(0, voidRunCounter.getRunCount());
        assertNull(voidLazy.get());
        assertEquals(1, voidRunCounter.getRunCount());
        assertNull(voidLazy.get());
        assertEquals(1, voidRunCounter.getRunCount());
    }

    @Test
    public void createLazyMultiThreaded() throws Exception {
        IntegerRunCounter integerRunCounter = new IntegerRunCounter();
        Lazy<Integer> integerLazy = LazyFactory.createLazyMultiThreaded(integerRunCounter);
        multithreadedTest(() -> assertEquals((Integer)1, integerLazy.get()));
    }

    @Test
    public void createLazyLockFree() throws Exception {
//        IntegerRunCounter integerRunCounter = new IntegerRunCounter();
        final int someBigInt = 1235456789;
        Supplier<Integer> supplier = () -> new Integer(someBigInt);
        Lazy<Integer> bigIntLazy = LazyFactory.createLazyLockFree(supplier);
        AtomicReference<Integer> atomicInteger = new AtomicReference<>();
        multithreadedTest(() -> {
            Integer returnedInteger = bigIntLazy.get();
            assertEquals((Integer)someBigInt, returnedInteger);
            if (!atomicInteger.compareAndSet(null, returnedInteger)) {
                assertSame(atomicInteger.get(), returnedInteger);
            }
        });
    }

    // Создать много потоков
    // внутри создать барьер по количеству потоков (чтобы стартовали в одно время).
    private static void multithreadedTest(Runnable runnable) {
        final CyclicBarrier barrier = new CyclicBarrier(THREADS_COUNT);
        Thread[] threads = new Thread[THREADS_COUNT];
        final Throwable[] lastThrowable = {null};
        for (int i = 0; i < THREADS_COUNT; i++) {
            threads[i] = new Thread(() -> {
                try {
                    barrier.await();
                }
                catch (InterruptedException | BrokenBarrierException e) {
                    fail();
                }
                runnable.run();
            }
            );
            threads[i].setUncaughtExceptionHandler((t, e) -> {
                synchronized (lastThrowable) {
                    lastThrowable[0] = e;
                }
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        try {
            for (Thread thread : threads) {
                thread.join();
            }
        }
        catch (InterruptedException e) {
            fail("Failed to join thread");
        }
        if (lastThrowable[0] != null) {
            fail(lastThrowable[0].toString());
        }
    }
}