package ru.spbau.mit;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;

public class LockFreeListImpl<T> implements LockFreeList<T>, Iterable<T> {
    private final AtomicReference<Node<T>> tail;

    public LockFreeListImpl() {
        Node<T> endNode = new Node<>(null, null);
        tail = new AtomicReference<>(endNode);
    }

    @Override
    public boolean isEmpty() {
        return tail.get().isEndNode();
    }

    @Override
    public void append(T value) {
        while (true) {
            Node<T> currentTail = tail.get();
            Node<T> newNode = new Node<>(value, currentTail);
            assert !newNode.isEndNode();
            if (tail.compareAndSet(currentTail, newNode)) {
                return;
            }
        }
    }

    @Override
    public boolean remove(T value) {
        Node<T> currentNode = tail.get();

        // tries to remove node at tail.
        while (!currentNode.isEndNode() && currentNode.equalsByValue(value)) {
            if (tail.compareAndSet(currentNode, currentNode.getNext())) {
                return true;
            }
            currentNode = tail.get();
        }

        while (!currentNode.isEndNode()) {
            final Node<T> nextNode = currentNode.getNext();
            if (nextNode.isEndNode()) {
                break;
            }
            if (nextNode.equalsByValue(value)) {
                boolean isSuccessRemoval = currentNode.CompareAndSetNext(nextNode, nextNode.getNext());
                if (isSuccessRemoval) {
                    return true;
                }
            }
            currentNode = nextNode;
        }
        return false;
    }

    @Override
    public boolean contains(T value) {
        return findByValue(value) != null;
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            private Node<T> currentNode = tail.get();

            @Override
            public boolean hasNext() {
                return !currentNode.isEndNode();
            }

            @Override
            public T next() {
                T currentValue = currentNode.value;
                currentNode = currentNode.getNext();
                return currentValue;
            }
        };
    }

    private static class Node<T> {
        private final T value;
        private final AtomicReference<Node<T>> next;

        Node(T value, Node<T> next) {
            this.value = value;
            this.next = new AtomicReference<>(next);
        }

        boolean isEndNode() {
            return value == null && next.get() == null;
        }

        boolean equalsByValue(T value) {
            if (value == null) {
                return this.value == null;
            }
            return this.value.equals(value);
        }

        Node<T> getNext() {
            return next.get();
        }

        boolean CompareAndSetNext(Node<T> oldNext, Node<T> newNext) {
            return next.compareAndSet(oldNext, newNext);
        }
    }

    private Node findByValue(T value) {
        Node<T> fromNode = tail.get();
        while (!fromNode.isEndNode() && !fromNode.equalsByValue(value)) {
            fromNode = fromNode.getNext();
        }
        return fromNode.isEndNode() ? null : fromNode;
    }
}
