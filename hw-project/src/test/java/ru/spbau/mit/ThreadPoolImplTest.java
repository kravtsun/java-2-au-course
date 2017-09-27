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
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class ThreadPoolImplTest {
    static final Logger logger = Logger.getLogger("root");
    static final int testTimeout = 1000;
    final int nthreads = 10;
    private ThreadPool threadPool;
//    private volatile Void volatileTrash;

    @Before
    public void setUp() throws Exception {
        threadPool = new ThreadPoolImpl(nthreads);
    }

    @After
    public void tearDown() throws Exception {
        threadPool.shutdown();
        threadPool = null;
    }

    @Test(timeout = testTimeout)
    public void addTask() throws Exception {
        threadPool = new ThreadPoolImpl(1);
        LightFuture<Integer> simpleFuture = threadPool.addTask(() -> 1 + 2);
        assertEquals((Integer) 3, simpleFuture.get());
    }

    @Test(timeout = testTimeout)
    public void factorialTest() throws Exception {
        factorialTask(this.nthreads);
    }

    @Test(timeout = testTimeout)
    public void throwableTest() throws Exception {
        ThreadPool threadPool = new ThreadPoolImpl(this.nthreads);
        // Unchecked only.
        Class[] throwableClasses = {RuntimeException.class, NullPointerException.class};

        BiConsumer<Class, Supplier> checkThrowable = (throwableClass, throwSupplier) -> {
            LightFuture<Void> future = threadPool.addTask(throwSupplier);
            Throwable t = null;
            try {
                future.get();
            }
            catch(Throwable caught) {
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
                RuntimeException t = null;
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

    @Test(timeout = testTimeout)
    public void shutdown() throws Exception {
        // some quantity of threads that can certainly block threadPool.
        final int moreThreads = 2 * this.nthreads;
        ThreadPool interruptThreadPool = new ThreadPoolImpl(2);
        interruptThreadPool.addTask(() -> {
            factorialTask(moreThreads);
            return null;
        });

        synchronized (this) {
            wait(testTimeout / 3);
        }

        LightFuture<Void> interruptFuture = interruptThreadPool.addTask(() -> {
            logger.log(Level.INFO, "interrupt initiated.");
            threadPool.shutdown();
            logger.log(Level.INFO, "interrupt finished.");
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
                wait(testTimeout / 10);
            } catch (InterruptedException e) {
                throw new Error();
            }
        }
        // threadPool is one thread free, so it should finish all futures by now (one by one).

        System.out.println(Arrays.stream(futures).map(LightFuture::isReady).collect(Collectors.toList()));
        assertTrue(Arrays.stream(futures).allMatch(LightFuture::isReady));
        assertEquals(45, Arrays.stream(natural).sum());
    }

    private class FactorialSupplier implements Supplier<Integer> {
        private final int n;
        private final LightFuture<Integer> previousFuture;

        public FactorialSupplier(int n, LightFuture<Integer> previousFuture) {
            this.n = n;
            this.previousFuture = previousFuture;
        }

        @Override
        public Integer get() {
            if (n <= 1) {
                return 1;
            } else {
                return n * previousFuture.get();
            }
        }
    }

    private void factorialTask(int n) {
        LightFuture<Integer>[] futures = new LightFuture[n];
        for (int i = 1; i <= n; ++i) {
            futures[i-1] = threadPool.addTask(new FactorialSupplier(i, i == 1? null : futures[i-2]));
        }
        assertEquals((Integer) factorial(n), futures[n-1].get());
    }

    private int factorial(int n) {
        return n <= 1? 1 : n * factorial(n-1);
    }
}
