package ru.spbau.mit;

import java.util.concurrent.atomic.AtomicReference;

public class LockFreeListImpl<T> implements LockFreeList<T> {
    private final AtomicReference<Node<T>> tail;

    public LockFreeListImpl() {
        Node<T> endNode = new Node<>(null, null);
        tail = new AtomicReference<>(endNode);
    }

    @Override
    public boolean isEmpty() {
        while (true) {
            Node<T> currentNode = tail.get();
            if (currentNode.isEndNode()) {
                return true;
            } else if (currentNode.isRemoved()) {
                tail.compareAndSet(currentNode, currentNode.getNext());
            } else {
                return false;
            }
        }
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
        retry: while (true) {
            Node<T> currentNode = tail.get();
            // tries to remove node at tail.
            while (currentNode.equalsByValue(value)) {
                if (currentNode.setRemoved()) {
                    // optimization: physical removal.
                    tail.compareAndSet(currentNode, currentNode.getNext());
                    return true;
                }
                continue retry;
            }

            while (!currentNode.isEndNode()) {
                final Node<T> nextNode = currentNode.getNext();
                if (nextNode.equalsByValue(value)) {
                    if (nextNode.setRemoved()) {
                        // optimization: physical removal.
                        currentNode.compareAndSetNext(nextNode, nextNode.getNext());
                        return true;
                    }
                    continue retry;
                }
                if (nextNode.isRemoved()) {
                    currentNode.compareAndSetNext(nextNode, nextNode.getNext());
                } else {
                    currentNode = nextNode;
                }
            }
            return false;
        }
    }

    @Override
    public boolean contains(T value) {
        Node<T> currentNode = tail.get();
        if (currentNode.equalsByValue(value)) {
            return true;
        }

        while (!currentNode.isEndNode()) {
            Node<T> nextNode = currentNode.getNext();
            if (nextNode.equalsByValue(value)) {
                return true;
            }
            if (nextNode.isRemoved()) {
                currentNode.compareAndSetNext(nextNode, nextNode.getNext());
            } else {
                currentNode = nextNode;
            }
        }
        return false;
    }
}
