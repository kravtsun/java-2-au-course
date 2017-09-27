package ru.spbau.mit;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class ThreadPoolImpl implements ThreadPool {
    private static final Logger logger = Logger.getLogger("ThreadPool");
    private Thread[] threads;

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
                        // TODO: differentiate InterruptedException from task with
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
        final Runnable runnable = () -> {
            try {
                R result = task.get();
                future.setResult(result);
            } catch (Throwable t) {
                future.setException(t);
            } finally {
                future.setReady();
            }
            synchronized (future) {
                future.notifyAll();
            }
        };

        synchronized (taskQueue) {
            final boolean added = taskQueue.add(runnable);
            if (!added) {
                throw new Error("addTask: taskQueue inconsistent state:");
            }
            taskQueue.notify();
        }
        return future;
    }

    @Override
    public void shutdown() {
        logger.info("shutdown start");
        while (Arrays.stream(threads).anyMatch(Thread::isAlive)) {
            logger.info("liveThreadCount: " + Arrays.stream(threads).filter(Thread::isAlive).count());

            for (Thread thread : threads) {
                thread.interrupt();
            }

            synchronized (taskQueue) {
                taskQueue.notifyAll();
            }
        }
        logger.info("shutdown finished");
    }

    <R2, R> LightFuture<R2> addDependentTask(LightFutureImpl<R> child, Function<? super R, ? extends R2> function) {
        return addTask(() -> {
            R arg = child.get();
            return function.apply(arg);
        });
    }
}
