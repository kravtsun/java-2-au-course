package ru.spbau.mit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Function;

public class LightFutureImpl<R> implements LightFuture<R> {
    private final ThreadPoolImpl threadPool;

    // get() method support - should be visible from any thread calling LightFutureImpl.get()
    // ready is set true atomically with result.
    private volatile boolean ready = false;
    private volatile R result;
    private volatile Throwable throwable;

    private Collection<Runnable> taskChildren;

    public <R2> LightFutureImpl(ThreadPoolImpl threadPool, Callable<R> callable, LightFutureImpl<R2> parent) {
        this.threadPool = threadPool;
        this.taskChildren = new ArrayList<>();
        Runnable runnable = getRunnable(callable);
        if (parent == null) {
            threadPool.addRunnable(runnable);
        } else {
            synchronized (parent) {
                if (parent.isReady()) {
                    threadPool.addRunnable(runnable);
                } else {
                    parent.taskChildren.add(runnable);
                }
            }
        }
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public R get() throws LightExecutionException {
        while (true) {
            synchronized (this) {
                if (ready) {
                    return getResult();
                }
                try {
                    wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException();
                }
            }
        }
    }

    @Override
    public <R2> LightFuture<R2> thenApply(Function<? super R, ? extends R2> function) {
        Callable<R2> callable = () -> {
            R arg = this.get();
            return function.apply(arg);
        };
        return new LightFutureImpl<>(threadPool, callable, this);
    }

    private Runnable getRunnable(Callable<R> callable) {
        return () -> {
            try {
                setResult(callable.call());
            } catch (Throwable t) {
                setException(t);
            } finally {
                setReady();
            }
            synchronized (this) {
                notifyAll();
                for (Runnable runnable : taskChildren) {
                    threadPool.addRunnable(runnable);
                }
                taskChildren = null;
            }
        };
    }

    private void setResult(R result) {
        if (this.result != null) {
            throw new Error("LightFuture: invalid state: result is already set.");
        }
        this.result = result;
    }

    private void setReady() {
        if (this.ready) {
            throw new Error("LightFuture: invalid state: ready is already set.");
        }
        this.ready = true;
    }

    private void setException(Throwable throwable) {
        if (this.throwable != null) {
            throw new Error("LightFuture: invalid state: throwable is already set.");
        }
        this.throwable = throwable;
    }

    private R getResult() throws LightExecutionException {
        if (!ready) {
            throw new Error("LightFuture: invalid state: ready is already set.");
        }
        if (throwable != null) {
            Throwable cause = throwable;
            throw new LightExecutionException(cause);
        }
        return result;
    }
}
