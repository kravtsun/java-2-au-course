package ru.spbau.mit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import static ru.spbau.mit.LightFuture.LightExecutionException;

import java.lang.Thread.State;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class ThreadPoolImplTest {
    private static final Logger LOGGER = Logger.getLogger("root");
    private static final int TEST_TIMEOUT = 1000;
    private int nthreads = 10;
    private ThreadPool threadPool;

    public ThreadPoolImplTest(int nthreads) {
        this.nthreads = nthreads;
    }

    // Sometimes this parameters are used just for repeating the same test.
    @Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {1}, {2}, {4}, {8}, {12}, {16}, {32}
        });
    }

    private static Thread[] getThreads(ThreadPool threadPool) throws IllegalAccessException {
        Field[] fields = threadPool.getClass().getDeclaredFields();
        Field threadsField = Arrays.stream(fields)
                .filter((f) -> f.getName().equals("threads"))
                .findAny()
                .orElseThrow(Error::new);
        threadsField.setAccessible(true);
        return (Thread[]) threadsField.get(threadPool);
    }

    @Before
    public void setUp() throws Exception {
        threadPool = new ThreadPoolImpl(nthreads);
    }

    @After
    public void tearDown() throws Exception {
        threadPool.shutdown();
        threadPool = null;
    }

    @Test(timeout = TEST_TIMEOUT)
    public void addTask() throws Exception {
        LightFuture<Integer> simpleFuture = threadPool.addTask(() -> 1 + 2);
        assertEquals((Integer) 3, simpleFuture.get());
    }

    @Test(timeout = TEST_TIMEOUT)
    public void factorialTest() throws Exception {
        factorialTask(nthreads);
    }

    @Test(timeout = TEST_TIMEOUT)
    public void throwableTest() throws Exception {
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
        final int moreThreads = 2 * nthreads;
        ThreadPool interruptThreadPool = new ThreadPoolImpl(2);
        interruptThreadPool.addTask(() -> {
            try {
                factorialTask(moreThreads);
            } catch (LightExecutionException e) {
                throw new RuntimeException("LightExecutionException");
            }
            return null;
        });

        Thread.sleep(TEST_TIMEOUT / 3);

        LightFuture<Void> interruptFuture = interruptThreadPool.addTask(() -> {
            LOGGER.log(Level.INFO, "interrupt initiated.");
            threadPool.shutdown();
            LOGGER.log(Level.INFO, "interrupt finished.");
            return null;
        });

        interruptFuture.get();
    }

    @Test
    public void exaustiveSingleThreadSubmitTest() throws Exception {
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

        Thread.sleep(TEST_TIMEOUT / 10);

        // threadPool is one thread free, so it should finish all futures by now (one by one).

        assertTrue(Arrays.stream(futures).allMatch(LightFuture::isReady));
        assertEquals(45, Arrays.stream(natural).sum());
    }

    @Test(timeout = TEST_TIMEOUT)
    public void thenAfterTest() throws Exception {
        final int n = 2 * nthreads;

        LightFuture<Integer> future = null;
        for (int i = 0; i < n; ++i) {
            if (future == null) {
                future = threadPool.addTask(() -> 1);
            } else {
                future = future.thenApply((x) -> x + 1);
            }
        }
        assertEquals((Integer) n, future.get());
    }

    @Test(timeout = TEST_TIMEOUT)
    public void threadCountLiveTest() throws Exception {
        final Semaphore semaphore = new Semaphore(1);
        semaphore.acquire();
        for (int i = 0; i < nthreads; i++) {
            threadPool.addTask(() -> {
                try {
                    semaphore.acquire();
                } catch (InterruptedException e) {
                    throw new Error();
                }
                semaphore.release();
                return null;
            });
        }

        Thread[] threads = getThreads(threadPool);
        final long liveThreadsCount = Arrays.stream(threads).filter(Thread::isAlive).count();
        assertEquals(nthreads, liveThreadsCount);
        semaphore.release();
        threadPool.shutdown();
    }

    @Test(timeout = TEST_TIMEOUT)
    public void threadCountRunningTest() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(nthreads);
        for (int i = 0; i < nthreads; i++) {
            threadPool.addTask(() -> {
                countDownLatch.countDown();
                int j = 0;
                while (!Thread.currentThread().isInterrupted()) {
                    j++;
                }
                return null;
            });
        }
        countDownLatch.await();
        Thread[] threads = getThreads(threadPool);
        List<State> threadsStates = Arrays.stream(threads).map(Thread::getState).collect(Collectors.toList());
        final long activeThreadsCount = threadsStates.stream().filter((st) -> st.equals(State.RUNNABLE)).count();
        LOGGER.info(threadsStates.toString());
        LOGGER.info("activeThreadsCount = " + activeThreadsCount);
        assertEquals(nthreads, activeThreadsCount);
        threadPool.shutdown();
    }

    @Test(timeout = TEST_TIMEOUT)
    public void addingToThreadPoolTaskTest() throws Exception {
        LightFuture[] futures = new LightFuture[nthreads];
        Function<Integer, Integer> function = (j) -> 2 * j;
        CyclicBarrier cyclicBarrier = new CyclicBarrier(nthreads);
        for (int i = 0; i < nthreads; ++i) {
            int finalI1 = i;
            LightFuture<Integer> innerFuture = threadPool.addTask(() -> {
                try {
                    cyclicBarrier.await();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return finalI1;
            });
            futures[i] = innerFuture.thenApply(function);
        }
        for (int i = 0; i < nthreads; ++i) {
            assertEquals(function.apply(i), futures[i].get());
        }
    }

    @Test(timeout = TEST_TIMEOUT)
    public void threeTasksBottleneckTest() throws Exception {
        ThreadPool threadPool = new ThreadPoolImpl(2);

        LightFuture<Integer> longParentTask = threadPool.addTask(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new Error();
            }
            return 1;
        });

        LightFuture<Integer> childTask = longParentTask.thenApply((i) -> 2 * i);
        final Integer simpleTaskResult = 42;
        LightFuture<Integer> otherTask = threadPool.addTask(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new Error();
            }
            return simpleTaskResult;
        });
        assertEquals(simpleTaskResult, otherTask.get());
        assertEquals((Integer) 1, longParentTask.get());
        assertEquals((Integer) 2, childTask.get());
    }

    private void factorialTask(int n) throws LightExecutionException {
        LightFuture[] futures = new LightFuture[n];
        for (int i = 1; i <= n; ++i) {
            LightFuture previousFuture = i > 1 ? futures[i - 2] : null;
            FactorialSupplier factorialSupplier = new FactorialSupplier(i, previousFuture);
            futures[i - 1] = threadPool.addTask(factorialSupplier);
        }
        assertEquals(factorial(n), futures[n - 1].get());
    }

    private int factorial(int n) {
        return n <= 1 ? 1 : n * factorial(n - 1);
    }

    private static class FactorialSupplier implements Supplier<Integer> {
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
                try {
                    return n * (Integer) previousFuture.get();
                } catch (LightExecutionException e) {
                    throw new RuntimeException("LightExecutionException");
                }
            }
        }
    }
}