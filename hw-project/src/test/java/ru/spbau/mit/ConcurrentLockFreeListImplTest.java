package ru.spbau.mit;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;

@RunWith(Parameterized.class)
public class ConcurrentLockFreeListImplTest {
    @Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {2}, {4}, {8}, {16}, {32}, {50}, {100}, {256}, {1024}, {4098}, {16392},
                {65568}, {262272}, {1049088}
        });
    }

    private final int listLength;
    private final LockFreeListImpl<Integer> list;

    public ConcurrentLockFreeListImplTest(int listLength) {
        this.listLength = listLength;
        this.list = new LockFreeListImpl<>();
//        this.list = new SynchronizedList<>();
    }

    @Test
    public void AppendRemoveEvenTest() {
        CyclicBarrier startBarrier = new CyclicBarrier(2);
        CountDownLatch finishLatch = new CountDownLatch(2);

        Thread adderThread = new Thread(() -> {
            try {
                startBarrier.await();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            for (int i = 0; i < listLength; i++) {
                list.append(2 * i);
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
                Integer value = list.iterator().next();
                if (value != null && value % 2 == 0) {
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

        int[] elementsCounter = new int[2 * listLength];

        for (Integer value : list) {
            elementsCounter[value]++;
        }

        for (int i = 0; i < listLength; i++) {
            Assert.assertNotEquals(0, elementsCounter[2 * i + 1]);
        }
    }
}