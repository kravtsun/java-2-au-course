package ru.spbau.mit;

import java.util.function.Function;

public class LightFutureImpl<R> implements LightFuture<R> {
    private final ThreadPoolImpl threadPool;

    // get() method support - should be visible from any thread calling LightFutureImpl.get()
    // ready is set true atomically with result.
    private volatile boolean ready = false;
    private volatile R result;
    private volatile Throwable throwable;

    LightFutureImpl(ThreadPoolImpl threadPool) {
        this.threadPool = threadPool;
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public R get() {
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
        return threadPool.addDependentTask(this, function);
    }

    void setResult(R result) {
        if (this.result != null) {
            throw new Error("LightFuture: invalid state: result is already set.");
        }
        this.result = result;
    }

    void setReady() {
        if (this.ready) {
            throw new Error("LightFuture: invalid state: ready is already set.");
        }
        this.ready = true;
    }

    void setException(Throwable throwable) {
        if (this.throwable != null) {
            throw new Error("LightFuture: invalid state: throwable is already set.");
        }
        this.throwable = throwable;
    }

    private R getResult() {
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

// TODO try to get rid of synchronized.
