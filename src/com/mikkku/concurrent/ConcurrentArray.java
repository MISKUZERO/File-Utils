package com.mikkku.concurrent;

import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

@Deprecated
public class ConcurrentArray<T> {

    private final ReentrantLock lock = new ReentrantLock();
    private final Object[] array;
    private final Random random = new Random();
    private final int CAPACITY;
    private int count;

    public ConcurrentArray(int capacity) {
        if (capacity <= 0)
            throw new IllegalArgumentException("The capacity must is positive integer!");
        array = new Object[capacity];
        CAPACITY = capacity;
    }


    public int size() {
        return count;
    }

    public void input(T element) {
        int index = random.nextInt(CAPACITY);
        while (true) {
            if (array[index] == null) {
                lock.lock();
                try {
                    if (array[index] == null) {
                        array[index] = element;
                        count++;
                        return;
                    }
                } finally {
                    lock.unlock();
                }
            }
            if (index == CAPACITY - 1) {
                index = 0;
            } else
                index++;
        }
    }


    @SuppressWarnings("unchecked")
    public T output() {
        if (count == 0)
            return null;
        T element;
        for (int i = 0; i < array.length; i++)
            if (array[i] != null) {
                lock.lock();
                try {
                    if (array[i] != null) {
                        element = (T) array[i];
                        array[i] = null;
                        count--;
                        return element;
                    }
                } finally {
                    lock.unlock();
                }
            }
        return null;
    }

}
