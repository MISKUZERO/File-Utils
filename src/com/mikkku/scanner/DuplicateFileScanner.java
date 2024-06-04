package com.mikkku.scanner;

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
    private static final int INIT_CAPACITY = 1 << 4;

    private final File[] files;
    private final MessageDigest[] messageDigests;
    private final CountDownLatch latch;
    private final ThreadPoolExecutor executor;
    private final AtomicInteger repeatCount = new AtomicInteger();
    private final AtomicInteger failCount = new AtomicInteger();
    private final ConcurrentHashMap<String, String> hashMap = new ConcurrentHashMap<>(INIT_CAPACITY << 4);
    private final ConcurrentHashMap<String, String> sizeMap = new ConcurrentHashMap<>(INIT_CAPACITY);
    private final ConcurrentHashMap<String, String> antiHashMap = new ConcurrentHashMap<>(INIT_CAPACITY);
    private final ConcurrentHashMap<String, String> antiSizeMap = new ConcurrentHashMap<>(INIT_CAPACITY);

    public DuplicateFileScanner(String algorithm, File... files) throws IOException {
        this.files = files;
        final int[] counts = {0};
        FileScanner fileScanner = new FileScanner() {
            @Override
            protected void operate(File file) {
                counts[0]++;
            }
        };
        for (File file : files) fileScanner.scanFiles(file);
        System.out.println("=======> " + counts[0]);
        latch = new CountDownLatch(counts[0]);
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
        );
    }


    public Object[] scan() {
        for (File file : files) executor.execute(new ScanThread(file));
        try {
            latch.await();
        } catch (InterruptedException e) {
            System.err.println("任务被终止！");
            System.exit(2);
        }
        executor.shutdown();
        try {
            if (executor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS)) {
                int uniqueCount = hashMap.size() + sizeMap.size();
                int repeatCount = this.repeatCount.get();
                int failCount = this.failCount.get();
                int fileCount = uniqueCount + repeatCount + failCount;
                return new Object[]{uniqueCount, repeatCount, failCount, fileCount, antiHashMap, antiSizeMap};
            }
        } catch (InterruptedException e) {
            System.err.println("扫描被中断！");
            System.exit(3);
        }
        return null;
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
                        //1.预处理：超过2GB的文件改为判断文件大小
                        long size = file.length();
                        if (size > Integer.MAX_VALUE) {
                            String oldFile = sizeMap.put(size + "", file.toString());
                            if (oldFile != null) {
                                repeatCount.addAndGet(1);
                                antiSizeMap.put(size + "\\" + oldFile, "");
                                antiSizeMap.put(size + "\\" + file, "");
                            }
                            latch.countDown();
                            return;
                        }
                        //2.提交给IO线程处理
                        executor.execute(new IOThread(file));
                    }
                }.scanFiles(path);
            } catch (FileNotFoundException e) {
                System.err.println("路径不存在：" + path);
            }
        }
    }

    private class IOThread implements Runnable {

        private final File file;

        public IOThread(File file) {
            this.file = file;
        }

        @Override
        public void run() {
            //1.读取文件并生成字节数组
            byte[] bytes;
            try {
                bytes = FileUtils.fileToBytes(file);
            } catch (Exception e) {
                failCount.addAndGet(1);
                e.printStackTrace();
                return;
            }
            //2.提交给编码线程处理
            executor.execute(new DigestThread(bytes, file));
        }
    }

    private class DigestThread implements Runnable {

        private final byte[] bytes;
        private final File file;

        public DigestThread(byte[] bytes, File file) {
            this.bytes = bytes;
            this.file = file;
        }

        @Override
        public void run() {
            try {
                //1.生成hash码
                String encode = HexBin.encode((((Worker) Thread.currentThread()).messageDigest).digest(bytes));
                //2.加入hashMap（用于判断hash码是否相同）
                String oldFile = hashMap.put(encode, file.toString());
                if (oldFile != null) {
                    repeatCount.addAndGet(1);
                    antiHashMap.put(encode + "\\" + oldFile, "");
                    antiHashMap.put(encode + "\\" + file, "");
                }
            } finally {
                latch.countDown();
            }
        }
    }

}
