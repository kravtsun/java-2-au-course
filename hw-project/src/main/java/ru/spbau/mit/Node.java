package ru.spbau.mit;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

class Node<T> {
    private final T value;
    private final AtomicReference<Node<T>> next;
    private final AtomicBoolean isRemoved;

    Node(T value, Node<T> next) {
        this.value = value;
        this.next = new AtomicReference<>(next);
        this.isRemoved = new AtomicBoolean(false);
    }

    boolean isEndNode() {
        return value == null && next.get() == null;
    }

    // Will return false if isRemoved == true or node isEndNode.
    boolean equalsByValue(T value) {
        if (isRemoved.get() || isEndNode()) {
            return false;
        }
        if (value == null) {
            return this.value == null;
        }
        return value.equals(this.value);
    }

    Node<T> getNext() {
        return next.get();
    }

    boolean compareAndSetNext(Node<T> oldNext, Node<T> newNext) {
        return next.compareAndSet(oldNext, newNext);
    }

    boolean isRemoved() {
        return this.isRemoved.get();
    }

    // logical removal.
    boolean setRemoved() {
        return this.isRemoved.compareAndSet(false, true);
    }
}
