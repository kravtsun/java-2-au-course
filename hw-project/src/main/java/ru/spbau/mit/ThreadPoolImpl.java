package ru.spbau.mit;

import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class ThreadPoolImpl implements ThreadPool {
    private static final Logger LOGGER = Logger.getLogger("ThreadPool");
    private final Thread[] threads;
    private final Queue<Runnable> taskQueue;

    public ThreadPoolImpl(int n) {
        threads = new Thread[n];
        taskQueue = new LinkedList<>();
        final Runnable threadTask = () -> {
            while (!Thread.currentThread().isInterrupted()) {
                Runnable nextTask = null;
                synchronized (taskQueue) {
                    if (!taskQueue.isEmpty()) {
                        nextTask = taskQueue.remove();
                    }
                }

                if (nextTask != null) {
                    nextTask.run();
                }

                synchronized (taskQueue) {
                    try {
                        if (taskQueue.isEmpty()) {
                            taskQueue.wait();
                        }
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        };

        // Initializing threads.
        for (int i = 0; i < n; i++) {
            threads[i] = new Thread(threadTask);
        }

        // Starting threads.
        for (int i = 0; i < n; i++) {
            threads[i].start();
        }
        // TODO start daemon thread to notify worker threads if taskQueue is not empty.
    }

    @Override
    public <R> LightFuture<R> addTask(Supplier<R> task) {
        return new LightFutureImpl<>(this, task::get, null);
    }

    @Override
    public void shutdown() {
        LOGGER.info("shutdown start");
        while (Arrays.stream(threads).anyMatch(Thread::isAlive)) {
            LOGGER.info("liveThreadCount: " + Arrays.stream(threads).filter(Thread::isAlive).count());

            for (Thread thread : threads) {
                thread.interrupt();
            }
        }
        LOGGER.info("shutdown finished");
    }

    void addRunnable(Runnable runnable) {
        synchronized (taskQueue) {
            taskQueue.add(runnable);
            taskQueue.notify();
        }
    }
}
