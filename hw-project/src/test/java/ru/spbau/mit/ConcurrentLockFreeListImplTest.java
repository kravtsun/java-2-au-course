package ru.spbau.mit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class ConcurrentLockFreeListImplTest {
    private final int listLength;
    private final LockFreeListImpl<Integer> list;
    public ConcurrentLockFreeListImplTest(int listLength) {
        this.listLength = listLength;
        this.list = new LockFreeListImpl<>();
    }

    @Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {2}, {4}, {8}, {16}, {32}, {50}, {100}, {256}, {1024}, {4096}, {8192}
        });
    }

    @Test(timeout = 30000)
    public void appendRemoveEvenTest() {
        CyclicBarrier startBarrier = new CyclicBarrier(2);
        CountDownLatch finishLatch = new CountDownLatch(2);

        AtomicInteger currentNumber = new AtomicInteger(0);
        Thread adderThread = new Thread(() -> {
            try {
                startBarrier.await();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            for (int i = 0; i < listLength; i++) {
                list.append(2 * i);
                currentNumber.set(2 * i);
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                list.append(2 * i + 1);
            }
            finishLatch.countDown();
        });
        Thread removerThread = new Thread(() -> {
            try {
                startBarrier.await();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            for (int i = 0; i < 2 * listLength; i++) {
                Integer value = currentNumber.get();
                if (value % 2 == 0) {
                    list.remove(value);
                }
            }
            finishLatch.countDown();
        });

        adderThread.start();
        removerThread.start();

        try {
            finishLatch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        for (int i = 0; i < listLength; i++) {
            list.contains(2 * i  + 1);
        }
    }

    @Test(timeout = 30000)
    public void allRemoveTest() throws InterruptedException {
        if (listLength > 10000) {
            return;
        }

        CyclicBarrier startBarrier = new CyclicBarrier(listLength);
        CountDownLatch finishLatch = new CountDownLatch(listLength);

        Thread[] threads = new Thread[listLength];
        Exception[] exceptions = new Exception[listLength];

        for (int i = listLength - 1; i >= 0; i--) {
            list.append(i);
            int finalI = i;
            threads[i] = new Thread(() -> {
                try {
                    startBarrier.await();
                    assertTrue(list.remove(finalI));
                } catch (Exception e) {
                    exceptions[finalI] = e;
                }
                finishLatch.countDown();
            });
            threads[i].start();
        }

        finishLatch.await();

        for (int i = 0; i < listLength; i++) {
            assertNull(exceptions[i]);
            assertFalse(list.contains(i));
        }
        assertTrue(list.isEmpty());
    }
}
