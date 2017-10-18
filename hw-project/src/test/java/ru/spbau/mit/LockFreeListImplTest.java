package ru.spbau.mit;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class LockFreeListImplTest {
    private LockFreeList<Integer> list;

    @Before
    public void setUp() throws Exception {
        list = new LockFreeListImpl<>();
    }

    @Test
    public void isEmpty() throws Exception {
        assertTrue(list.isEmpty());
        list.append(1);
        assertFalse(list.isEmpty());
        assertTrue(list.remove(1));
        assertTrue(list.isEmpty());
        list.append(1);
        assertFalse(list.isEmpty());
        list.append(1);
        assertFalse(list.isEmpty());
        assertTrue(list.remove(1));
        assertFalse(list.isEmpty());
        assertTrue(list.remove(1));
        assertTrue(list.isEmpty());
    }

    @Test
    public void append() throws Exception {
        List<Integer> oneTwoThree = Arrays.asList(1, 2, 3);
        for (Integer val : oneTwoThree) {
            list.append(val);
        }

        for (Integer val : oneTwoThree) {
            assertTrue(list.contains(val));
        }
    }

    @Test
    public void remove() throws Exception {
        list.append(1);
        list.append(3);
        assertTrue(list.remove(1));
        assertFalse(list.contains(1));
        assertTrue(list.contains(3));
        assertTrue(list.remove(3));
        assertFalse(list.contains(1));
        assertFalse(list.contains(3));
    }

    @Test
    public void contains() throws Exception {
        assertFalse(list.contains(1));
        assertFalse(list.contains(0));
        assertFalse(list.contains(null));
        list.append(1);
        assertTrue(list.contains(1));
        assertFalse(list.contains(0));
        assertFalse(list.contains(null));
    }

    @Test
    public void nullTest() throws Exception {
        assertFalse(list.contains(null));
        list.append(null);
        assertTrue(list.contains(null));
        assertFalse(list.isEmpty());
        assertTrue(list.remove(null));
        assertFalse(list.contains(null));
        assertTrue(list.isEmpty());
    }
}
