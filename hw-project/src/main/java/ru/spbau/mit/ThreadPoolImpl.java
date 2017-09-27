package ru.spbau.mit;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class ThreadPoolImpl implements ThreadPool {
    private static final Logger LOGGER = Logger.getLogger("ThreadPool");
    private final Thread[] threads;

    private final Queue<Runnable> taskQueue;

    ThreadPoolImpl(int n) {
        this.threads = new Thread[n];
        this.taskQueue = new LinkedList<>();
        final Runnable threadTask = () -> {
            while (true) {
                Runnable nextTask = null;
                synchronized (taskQueue) {
                    try {
                        if (!taskQueue.isEmpty()) {
                            nextTask = taskQueue.remove();
                        }
                    } catch (NoSuchElementException ignored) {
                        // some other thread intercepted task.
                    }
                }

                if (Thread.currentThread().isInterrupted()) {
                    break;
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
                        // TODO differentiate InterruptedException from task with
                        // InterruptedException for thread (coming from ThreadPool.shutdown())
                        // Possible solution: ThreadPool.shutdownInitiated field.
                        break;
                    }
                }
            }
        };

        // Initializing threads.
        for (int i = 0; i < n; i++) {
            this.threads[i] = new Thread(threadTask);
        }

        // Starting threads.
        for (int i = 0; i < n; i++) {
            this.threads[i].start();
        }
        // TODO start daemon thread to notify worker threads if taskQueue is not empty.
    }

    @Override
    public <R> LightFuture<R> addTask(Supplier<R> task) {
        final LightFutureImpl<R> future = new LightFutureImpl<>(this);
        Callable<R> callable = task::get;
        addRunnable(future.getRunnable(callable));
        return future;
    }

    @Override
    public void shutdown() {
        LOGGER.info("shutdown start");
        while (Arrays.stream(threads).anyMatch(Thread::isAlive)) {
            LOGGER.info("liveThreadCount: " + Arrays.stream(threads).filter(Thread::isAlive).count());

            for (Thread thread : threads) {
                thread.interrupt();
            }

            synchronized (taskQueue) {
                taskQueue.notifyAll();
            }
        }
        LOGGER.info("shutdown finished");
    }

    private <R> void addRunnable(Runnable runnable) {
        synchronized (taskQueue) {
            final boolean added = taskQueue.add(runnable);
            if (!added) {
                throw new Error("addTask: taskQueue inconsistent state:");
            }
            taskQueue.notify();
        }
    }

    <R, R2> LightFuture<R2> addDependentTask(LightFutureImpl<R> child,
                                            Function<? super R, ? extends R2> function) {
        final LightFutureImpl<R2> future = new LightFutureImpl<>(this);
        Runnable runnable = future.getRunnable(() -> {
            R arg = child.get();
            R2 result = function.apply(arg);
            return result;
        });
        addRunnable(runnable);
        return future;
    }
}
