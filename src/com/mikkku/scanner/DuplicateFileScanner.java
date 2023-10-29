package com.mikkku.scanner;

import com.mikkku.exception.FileOversizeException;
import com.mikkku.util.FileUtils;
import com.sun.org.apache.xerces.internal.impl.dv.util.HexBin;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class DuplicateFileScanner {

    private static final int PROCESSORS = Runtime.getRuntime().availableProcessors();
    private static final int INIT_CAPACITY = 1 << 8;

    private final File[] files;
    private final MessageDigest[] messageDigests;
    private final CountDownLatch scanLatch;
    private final ThreadPoolExecutor executor;
    private final CountDownLatch executorLatch = new CountDownLatch(1);
    private final AtomicInteger repeatCount = new AtomicInteger();
    private final AtomicInteger oversizeCount = new AtomicInteger();
    private final CopyOnWriteArrayList<String> repeatList = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> oversizeList = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, String> uniqueMap = new ConcurrentHashMap<>(INIT_CAPACITY);

    public DuplicateFileScanner(String algorithm, File... files) {
        this.files = files;
        scanLatch = new CountDownLatch(files.length);
        messageDigests = new MessageDigest[PROCESSORS];
        for (int i = 0; i < messageDigests.length; i++) {
            try {
                messageDigests[i] = MessageDigest.getInstance(algorithm);
            } catch (NoSuchAlgorithmException e) {
                System.err.println("启动失败！");
                System.exit(1);
            }
        }
        ThreadFactory threadFactory = new ThreadFactory() {
            private int id;

            @Override
            public Thread newThread(Runnable r) {
                System.out.println("Worker-" + id + ": 由" + Thread.currentThread().getName() + "线程创建！");
                return new Worker(r, "Worker-" + id, messageDigests[id++]);
            }
        };
        executor = new ThreadPoolExecutor(
                PROCESSORS,
                PROCESSORS,
                0,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(PROCESSORS),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy() {
                    @Override
                    public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
                        System.err.println("拒绝策略执行！");
                        super.rejectedExecution(r, e);
                    }
                }
        ) {
            @Override
            protected void terminated() {
                try {
                    super.terminated();
                } finally {
                    executorLatch.countDown();
                }
            }
        };
    }


    public Object[] scan() {
        for (File file : files) executor.execute(new ScanThread(file));
        try {
            scanLatch.await();
        } catch (InterruptedException e) {
            System.err.println("任务被终止！");
            System.exit(2);
        }
        executor.shutdown();
        try {
            executorLatch.await();
        } catch (InterruptedException e) {
            System.err.println("任务被终止！");
            System.exit(2);
        }
        int uniqueCount = uniqueMap.size();
        int repeatCount = this.repeatCount.get();
        int oversizeCount = this.oversizeCount.get();
        int count = uniqueCount + repeatCount + oversizeCount;
        return new Object[]{uniqueCount, repeatCount, oversizeCount, count, repeatList.clone(), oversizeList.clone()};
    }

    private static class Worker extends Thread {

        private final MessageDigest messageDigest;

        private Worker(Runnable runnable, String name, MessageDigest messageDigest) {
            super(runnable, name);
            this.messageDigest = messageDigest;
        }
    }

    private class ScanThread implements Runnable {

        private final File path;

        public ScanThread(File path) {
            this.path = path;
        }

        @Override
        public void run() {
            try {
                new FileScanner() {

                    @Override
                    protected void operate(File file) {
                        byte[] bytes;
                        //1.生成字节数组
                        try {
                            bytes = FileUtils.fileToBytes(file);
                        } catch (IOException ioException) {
                            ioException.printStackTrace();
                            return;
                        } catch (FileOversizeException fileOversizeException) {
                            oversizeCount.addAndGet(1);
                            oversizeList.add(file + " -- " + file.length());
                            return;
                        }
                        //2.提交任务
                        executor.execute(new WorkThread(bytes, file));
                        System.out.println("阻塞队列长度：" + executor.getQueue().size());
                    }
                }.scanFiles(path);
            } catch (FileNotFoundException e) {
                System.err.println("路径不存在：" + path);
            } finally {
                scanLatch.countDown();
            }
        }
    }

    private class WorkThread implements Runnable {

        private final byte[] bytes;
        private final File file;

        public WorkThread(byte[] bytes, File file) {
            this.bytes = bytes;
            this.file = file;
        }

        @Override
        public void run() {
            //1.生成MD5码
            String encode = HexBin.encode((((Worker) Thread.currentThread()).messageDigest).digest(bytes));
            //2.处理结果
            String oldFile = uniqueMap.put(encode, file.toString());
            if (oldFile != null) {
                repeatCount.addAndGet(1);
                if (!repeatList.contains(encode))
                    repeatList.add(encode);
                if (!repeatList.contains(oldFile))
                    repeatList.add(repeatList.indexOf(encode) + 1, oldFile);
                repeatList.add(repeatList.indexOf(encode) + 1, file.toString());
            }
        }
    }

}
