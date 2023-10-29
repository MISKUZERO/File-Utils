package com.mikkku.test;


import com.mikkku.concurrent.ConcurrentArray;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

public class ConcurrentContainerTest {

    static final int CAPACITY = 1;
    static final int THREADS = 12;
    static final int OFFSET = 0;
    static final int NUMS = 10000;

    volatile boolean adding = true;

    static final ReentrantLock lock = new ReentrantLock();

    static List<Integer> vector = new Vector<>(CAPACITY);
    static List<Integer> lockList = new ArrayList<>(CAPACITY);
    static List<Integer> synchronizeList = new ArrayList<>(CAPACITY);
    static List<Integer> copyOnWriteArrayList = new CopyOnWriteArrayList<>();
    static Map<Integer, Integer> concurrentHashMap = new ConcurrentHashMap<>(CAPACITY);
    static ConcurrentArray<Integer> concurrentArray = new ConcurrentArray<>(NUMS * THREADS * 2);
    static BlockingQueue<Integer> arrayBlockingQueue = new ArrayBlockingQueue<>(Integer.MAX_VALUE / 2);
    static BlockingQueue<Integer> linkedBlockingQueue = new LinkedBlockingQueue<>();


    static Object getRes(String property) throws NoSuchFieldException, IllegalAccessException {
        Class<ConcurrentContainerTest> concurrentCollTestClass = ConcurrentContainerTest.class;
        char firstChar = Character.toLowerCase(property.charAt(0));
        String substring = property.substring(1);
        Field declaredField = concurrentCollTestClass.getDeclaredField(firstChar + substring);
        return declaredField.get(null);
    }

    void setVector() {
        for (int i = 0; i < NUMS; i++) {
            vector.add(i);
        }
    }

    void getVector() {
        while (adding || vector.size() != 0) {
            try {
                vector.remove(0);
            } catch (Exception ignore) {

            }
        }
    }

    void setLockList() {
        for (int i = 0; i < NUMS; i++) {
            try {
                lock.lock();
                lockList.add(i);
            } finally {
                lock.unlock();
            }
        }
    }

    void getLockList() {
        while (adding || lockList.size() != 0) {
            try {
                lock.lock();
                if (lockList.size() != 0)
                    lockList.remove(0);
            } finally {
                lock.unlock();
            }
        }
    }

    void setSynchronizeList() {
        for (int i = 0; i < NUMS; i++) {
            synchronized (ConcurrentContainerTest.class) {
                synchronizeList.add(i);
            }
        }
    }

    void getSynchronizeList() {
        while (adding || synchronizeList.size() != 0) {
            synchronized (this) {
                if (synchronizeList.size() != 0)
                    synchronizeList.remove(0);
            }
        }
    }

    void setCopyOnWriteArrayList() {
        for (int i = 0; i < NUMS; i++) {
            copyOnWriteArrayList.add(i);
        }
    }

    void getCopyOnWriteArrayList() {
        while (adding || copyOnWriteArrayList.size() != 0) {
            lock.lock();
            try {
                if (copyOnWriteArrayList.size() != 0)
                    copyOnWriteArrayList.remove(0);
            } finally {
                lock.unlock();
            }

        }
    }

    void setConcurrentHashMap() {
        int hash = Thread.currentThread().hashCode();
        for (int i = 0; i < NUMS; i++) {
            concurrentHashMap.put(hash + i, 0);
        }
    }

    void setConcurrentArray() throws InterruptedException {
        for (int i = 0; i < NUMS; i++) {
            concurrentArray.input(i);
        }
    }

    void getConcurrentArray() {
        while (adding || concurrentArray.size() != 0) {
            concurrentArray.output();
        }
    }

    void setArrayBlockingQueue() {
        for (int i = 0; i < NUMS; i++) {
            arrayBlockingQueue.add(i);
        }
    }

    void getArrayBlockingQueue() {
        while (adding || arrayBlockingQueue.size() != 0) {
            arrayBlockingQueue.poll();
        }
    }

    void setLinkedBlockingQueue() {
        for (int i = 0; i < NUMS; i++) {
            linkedBlockingQueue.add(i);
        }
    }

    void getLinkedBlockingQueue() {
        while (adding || linkedBlockingQueue.size() != 0) {
            linkedBlockingQueue.poll();
        }
    }

    public static void main(String[] args) {

        ConcurrentContainerTest test = new ConcurrentContainerTest();
        CountDownLatch addLatch = new CountDownLatch(THREADS / 2 - OFFSET);
        CountDownLatch removeLatch = new CountDownLatch(THREADS / 2 + OFFSET);

        long l = System.currentTimeMillis();
        for (int i = 0; i < THREADS / 2 - OFFSET; i++) {
            new Thread(() -> {
                try {
                    test.setConcurrentArray();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    addLatch.countDown();
                }
            }).start();
        }
        for (int i = 0; i < THREADS / 2 + OFFSET; i++) {
            new Thread(() -> {
                try {
                    test.getConcurrentArray();
                } finally {
                    removeLatch.countDown();
                }
            }).start();
        }
        try {
            addLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        test.adding = false;
        try {
            removeLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        long l1 = System.currentTimeMillis();
        System.out.println("time: " + (l1 - l) + "ms");

        //only add                  Time(ms)    THREADS = 12 NUMS = 1000000

        //Vector                    2700
        //LockList*                 2200
        //SynchronizeList           2300
        //CopyOnWriteArrayList      2700                     10000
        //ConcurrentHashMap         4000
        //ArrayBlockingQueue        5300
        //LinkedBlockingQueue       13000
        //ConcurrentArray           6000

        //ArrayBlockingQueue        70          10           100000
        //ArrayBlockingQueue        2800        50           10000
        //ArrayBlockingQueue        4600        100          10000

        //LinkedBlockingQueue       90          10           100000
        //LinkedBlockingQueue       2800        50           10000
        //LinkedBlockingQueue       2800        100          10000


        //add and remove            Time(ms)    THREADS NUMS = 50000
        //Vector                    2200        3|9
        //Vector                    4500        6|6
        //Vector                    9900        9|3
        //LockList                  75          3|9
        //LockList                  150         6|6
        //LockList                  660         9|3
        //SynchronizeList           2000        3|9
        //SynchronizeList           4200        6|6
        //SynchronizeList           9500        9|3
        //CopyOnWriteArrayList      2700        3|9
        //CopyOnWriteArrayList      3400        6|6
        //CopyOnWriteArrayList      68000       9|3
        //ArrayBlockingQueue        80          3|9
        //ArrayBlockingQueue        70          6|6
        //ArrayBlockingQueue        80          9|3
        //LinkedBlockingQueue       90          3|9
        //LinkedBlockingQueue       100         6|6
        //LinkedBlockingQueue       120         9|3

        //LockList                  2800        3|9     1000000
        //LockList                  6000        6|6     1000000
        //LockList                  16000       9|3     1000000
        //LockList                  30000       25|25   1000000

        //ArrayBlockingQueue        2800        3|9     1000000
        //ArrayBlockingQueue        2800        6|6     1000000
        //ArrayBlockingQueue        4600        9|3     1000000
        //ArrayBlockingQueue        7100        25|25   1000000

        //LinkedBlockingQueue       3000        3|9     1000000
        //LinkedBlockingQueue       3400        6|6     1000000
        //LinkedBlockingQueue       3800        9|3     1000000
        //LinkedBlockingQueue       6000        25|25   1000000

    }


}
