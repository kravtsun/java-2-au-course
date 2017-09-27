package ru.spbau.mit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static ru.spbau.mit.LightFuture.LightExecutionException;

import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.*;

public class ThreadPoolImplTest {
    private static final Logger LOGGER = Logger.getLogger("root");
    private static final int TEST_TIMEOUT = 1000;
    private static final int NTHREADS = 10;
    private ThreadPool threadPool;

    @Before
    public void setUp() throws Exception {
        threadPool = new ThreadPoolImpl(NTHREADS);
    }

    @After
    public void tearDown() throws Exception {
        threadPool.shutdown();
        threadPool = null;
    }

    @Test(timeout = TEST_TIMEOUT)
    public void addTask() throws Exception {
        threadPool = new ThreadPoolImpl(1);
        LightFuture<Integer> simpleFuture = threadPool.addTask(() -> 1 + 2);
        assertEquals((Integer) 3, simpleFuture.get());
    }

    @Test(timeout = TEST_TIMEOUT)
    public void factorialTest() throws Exception {
        factorialTask(this.NTHREADS);
    }

    @Test(timeout = TEST_TIMEOUT)
    public void throwableTest() throws Exception {
        ThreadPool threadPool = new ThreadPoolImpl(this.NTHREADS);
        // Unchecked only.
        Class[] throwableClasses = {RuntimeException.class, NullPointerException.class};

        BiConsumer<Class, Supplier<Void>> checkThrowable =
                (Class throwableClass, Supplier<Void> throwSupplier) -> {
            LightFuture<Void> future = threadPool.addTask(throwSupplier);
            Throwable t = null;
            try {
                future.get();
            } catch (Throwable caught) {
                t = caught;
            }
            assertNotNull(t);
            assertEquals(t.getClass(), LightExecutionException.class);
            LightExecutionException lightExecutionException = (LightExecutionException) t;
            Throwable cause = lightExecutionException.getCause();
            assertEquals(throwableClass, cause.getClass());
        };

        for (Class throwableClass : throwableClasses) {
            Supplier<Void> throwSupplier = () -> {
                RuntimeException t;
                try {
                    t = (RuntimeException) throwableClass.newInstance();
                } catch (Throwable reflectionError) {
                    throw new Error("Reflection error while testing: " + reflectionError);
                }

                throw t;
            };
            checkThrowable.accept(throwableClass, throwSupplier);
        }
    }

    @Test(timeout = TEST_TIMEOUT)
    public void shutdown() throws Exception {
        // some quantity of threads that can certainly block threadPool.
        final int moreThreads = 2 * this.NTHREADS;
        ThreadPool interruptThreadPool = new ThreadPoolImpl(2);
        interruptThreadPool.addTask(() -> {
            factorialTask(moreThreads);
            return null;
        });

        synchronized (this) {
            wait(TEST_TIMEOUT / 3);
        }

        LightFuture<Void> interruptFuture = interruptThreadPool.addTask(() -> {
            LOGGER.log(Level.INFO, "interrupt initiated.");
            threadPool.shutdown();
            LOGGER.log(Level.INFO, "interrupt finished.");
            return null;
        });

        interruptFuture.get();
    }

    @Test
    public void exaustiveSingleThreadSubmitTest() {
        threadPool = new ThreadPoolImpl(1);
        final int ntasks = 10;
        final int[] natural = new int[ntasks];
        LightFuture[] futures = new LightFuture[ntasks];
        for (int i = 0; i < ntasks; ++i) {
            int finalI = i;
            futures[i] = threadPool.addTask(() -> {
                natural[finalI] = finalI;
                return null;
            });
        }

        synchronized (this) {
            try {
                wait(TEST_TIMEOUT / 10);
            } catch (InterruptedException e) {
                throw new Error();
            }
        }
        // threadPool is one thread free, so it should finish all futures by now (one by one).

        assertTrue(Arrays.stream(futures).allMatch(LightFuture::isReady));
        assertEquals(45, Arrays.stream(natural).sum());
    }

    private class FactorialSupplier implements Supplier<Integer> {
        private final int n;
        private final LightFuture previousFuture;

        FactorialSupplier(int n, LightFuture previousFuture) {
            this.n = n;
            this.previousFuture = previousFuture;
        }

        @Override
        public Integer get() {
            if (n <= 1) {
                return 1;
            } else {
                return n * (Integer) previousFuture.get();
            }
        }
    }

    private void factorialTask(int n) {
        LightFuture[] futures = new LightFuture[n];
        for (int i = 1; i <= n; ++i) {
            LightFuture previousFuture = i > 1 ? futures[i - 2] : null;
            FactorialSupplier factorialSupplier = new FactorialSupplier(i, previousFuture);
            futures[i - 1] = threadPool.addTask(factorialSupplier);
        }
        assertEquals((Integer) factorial(n), futures[n - 1].get());
    }

    private int factorial(int n) {
        return n <= 1 ? 1 : n * factorial(n - 1);
    }
}

// TODO test for LightFuture.thenApply.
