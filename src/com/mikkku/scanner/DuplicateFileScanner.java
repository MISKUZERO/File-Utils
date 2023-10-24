package com.mikkku.scanner;

import com.mikkku.exception.FileOversizeException;
import com.mikkku.util.FileUtils;
import com.sun.org.apache.xerces.internal.impl.dv.util.HexBin;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class DuplicateFileScanner {

    public static final int THREAD_COUNT = 11;
    public static final int INIT_CAPACITY = 1 << 8;

    private final ThreadPoolExecutor executor;
    private final CountDownLatch countDownLatch = new CountDownLatch(THREAD_COUNT);
    private final ArrayList<String> repeatList = new ArrayList<>();
    private final ConcurrentHashMap<String, String> uniqueMap = new ConcurrentHashMap<>(INIT_CAPACITY);

    private final AtomicInteger repeatCount = new AtomicInteger();
    private final AtomicInteger failCount = new AtomicInteger();

    private volatile boolean scanOver;
    private final DataUnit[] dataList = new DataUnit[THREAD_COUNT];

    public DuplicateFileScanner() {
        ThreadFactory factory = new ThreadFactory() {
            private int threadId;

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "Thread-" + threadId++ + "");
            }
        };
        executor = new ThreadPoolExecutor(
                THREAD_COUNT,
                THREAD_COUNT,
                0,
                TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                factory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    public void scan(File file) {
        new Thread(() -> {
            long begin = System.currentTimeMillis();
            try {
                new FileScanner<DataUnit>(dataList) {

                    @Override
                    protected void operate(DataUnit[] elements, File file) {
                        byte[] bytes = {};
                        try {
                            bytes = FileUtils.fileToBytes(file);
                        } catch (IOException ioException) {
                            ioException.printStackTrace();
                        } catch (FileOversizeException fileOversizeException) {// TODO: 2023/10/24 大文件处理
                            System.err.println("文件超过2GB：" + file);
                            failCount.addAndGet(1);
                            return;
                        }
                        while (true)
                            for (int i = 0; i < elements.length; i++)
                                if (elements[i] == null) {
                                    elements[i] = new DataUnit(bytes, file.toString());
                                    return;
                                }
                    }
                }.scanFiles(file);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                System.exit(1);
            }
            scanOver = true;
            System.out.println("扫描完成！");
            try {
                countDownLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("全部结束！");
            int unique = uniqueMap.size();
            System.out.println("未重复文件数：" + unique);
            System.out.println("重复文件数：" + repeatCount);
            System.out.println("失败文件数：" + failCount);
            System.out.println("总文件数：" + failCount.addAndGet(repeatCount.addAndGet(unique)));
            long end = System.currentTimeMillis();
            System.out.println("用时：" + (end - begin) + "ms");
//            for (String s : repeatList) {
//                System.out.println(s);
//            }
            System.exit(0);
        }).start();
        for (int i = 0; i < THREAD_COUNT; i++) executor.execute(new Worker(i));
    }

    private static class DataUnit {

        private final byte[] bytes;
        private final String path;

        public DataUnit(byte[] key, String value) {
            this.bytes = key;
            this.path = value;
        }

        public byte[] getBytes() {
            return bytes;
        }

        public String getPath() {
            return path;
        }

    }

    private class Worker extends Thread {

        private final int id;

        public Worker(int id) {
            this.id = id;
        }

        @Override
        public void run() {
            MessageDigest messageDigest;
            try {
                messageDigest = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                System.err.println(currentThread().getName() + " -> Worker-" + id + "：创建失败！");
                return;
            }
            DataUnit dataUnit;
            System.out.println(currentThread().getName() + " -> Worker-" + id + "：开始工作！");
            while ((dataUnit = dataList[id]) != null || !scanOver)
                if (dataUnit != null) {
                    //1.取字节数据
                    dataList[id] = null;
                    byte[] bytes = dataUnit.getBytes();
                    String path = dataUnit.getPath();
                    //2.生成MD5码
                    bytes = messageDigest.digest(bytes);
                    String encode = HexBin.encode(bytes);
                    System.out.println(encode);
                    //3.处理结果
                    String oldPath = uniqueMap.put(encode, path);
                    if (oldPath != null) {
                        repeatCount.addAndGet(1);
                        synchronized (this) {
                            if (!repeatList.contains(encode))
                                repeatList.add(encode);
                            if (!repeatList.contains(oldPath))
                                repeatList.add(repeatList.indexOf(encode) + 1, oldPath);
                            repeatList.add(repeatList.indexOf(encode) + 1, path);
                        }
                    }
                }
            System.out.println(currentThread().getName() + " -> Worker-" + id + "：结束工作！");
            countDownLatch.countDown();
        }
    }
}
