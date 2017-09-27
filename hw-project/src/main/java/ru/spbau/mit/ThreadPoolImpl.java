package ru.spbau.mit;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ThreadPoolImpl implements ThreadPool {
    private static final Logger logger = Logger.getLogger("ThreadPool");
    private Thread[] threads;

    // guarded by this monitor (ThreadPoolImpl).
//    private volatile int liveThreadCount;

    private final Object taskMonitor;

    // guarded by taskMonitor
    private final Queue<Runnable> taskQueue;

    ThreadPoolImpl(int n) {
        this.threads = new Thread[n];
        this.taskMonitor = new Object();
        this.taskQueue = new LinkedList<>();

        // Initializing thread.
        for (int i = 0; i < n; i++) {
            this.threads[i] = new Thread(() -> {
                while (true) {
                    Runnable nextTask = null;
                    synchronized (taskMonitor) {
                        try {
                            if (!taskQueue.isEmpty()) {
                                nextTask = taskQueue.remove();
                            }
                        }
                        catch (NoSuchElementException ignored) {
                            // some other thread intercepted task.
                        }
                        if (Thread.currentThread().isInterrupted()) {
                            break;
                        }

                        if (nextTask != null) {
                            nextTask.run();
                        }

                        try {
                            taskMonitor.wait();
                        } catch (InterruptedException e) {
                            // TODO: differentiate InterruptedException from task with
                            // InterruptedException for thread (coming from ThreadPool.shutdown())
                            // Possible solution: ThreadPool.shutdownInitiated field.
                            break;
                        }
                    }
                }
//                synchronized (this) {
//                    System.out.println(this.getClass().getSimpleName());
//                    assert this.getClass() == ThreadPoolImpl.class;
//                    liveThreadCount--;
//                    logger.log(Level.INFO, "setting liveThreadCount := " + liveThreadCount);
//                    if (liveThreadCount == 0) {
//                        this.notifyAll();
//                    }
//                }
            });
        }

        for (int i = 0; i < n; i++) {
            this.threads[i].start();
        }

//        liveThreadCount = threads.length;
        // TODO start daemon thread to notify worker threads if taskQueue is not empty.
    }

    @Override
    public <R> LightFuture<R> addTask(Supplier<R> task) {
        final LightFutureImpl<R> future = new LightFutureImpl<>(this);
        synchronized (taskMonitor) {
            taskQueue.add(() -> {
                try {
                    R result = task.get();
                    future.setResult(result);
                }
                catch (Throwable t) {
                    future.setException(t);
                }
                finally {
                    future.setReady();
                }
                synchronized (future) {
                    future.notifyAll();
                }
            });
            taskMonitor.notify();
        }
        return future;
    }

    @Override
    public void shutdown() {
        logger.info("shutdown start");
        while (Arrays.stream(threads).anyMatch(Thread::isAlive)) {
            for (Thread thread : threads) {
                thread.interrupt();
            }

            // Maybe an overkill?
            synchronized (taskMonitor) {
                taskMonitor.notifyAll();
            }
        }

//        logger.info("waiting threads to finish");
//        while (true) {
//            synchronized (this) {
//                if (liveThreadCount == 0) {
//                    break;
//                }
//                logger.warning("liveThreadCount = " + liveThreadCount);
//
//                try {
//                    wait();
//                } catch (InterruptedException e) {
//                    break;
//                }
//            }
//        }

        logger.info("shutdown finished");
    }

    @Override
    public void finalize() {
        shutdown();
    }

    <R2, R> LightFuture<R2> addDependentTask(LightFutureImpl<R> child, Function<? super R, ? extends R2> function) {
        return addTask(() -> {
            R arg = child.get();
            return function.apply(arg);
        });
    }
}
