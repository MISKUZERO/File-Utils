package com.mikkku.scanner;

import com.sun.org.apache.xerces.internal.impl.dv.util.HexBin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class DuplicateFileScanner {

    private static final int PROCESSORS = Runtime.getRuntime().availableProcessors();
    private static final int INIT_CAPACITY = 1 << 4;

    private final String algorithm;
    private final File[] files;
    private final CountDownLatch latch;
    private final ThreadPoolExecutor executor;
    private final AtomicInteger repeatCount = new AtomicInteger();
    private final AtomicInteger failCount = new AtomicInteger();
    private final ConcurrentHashMap<String, String> hashMap = new ConcurrentHashMap<>(INIT_CAPACITY << 4);
    private final ConcurrentHashMap<String, String> sizeMap = new ConcurrentHashMap<>(INIT_CAPACITY);
    private final ConcurrentHashMap<String, String> antiHashMap = new ConcurrentHashMap<>(INIT_CAPACITY);
    private final ConcurrentHashMap<String, String> antiSizeMap = new ConcurrentHashMap<>(INIT_CAPACITY);

    public DuplicateFileScanner(String algorithm, File... files) throws IOException {
        this.algorithm = algorithm;
        this.files = files;
        final int[] counts = {0};
        FileScanner fileScanner = new FileScanner() {
            @Override
            protected void operate(File file) {
                counts[0]++;
            }
        };
        for (File file : files) fileScanner.scanFiles(file);
        latch = new CountDownLatch(counts[0]);
        executor = new ThreadPoolExecutor(PROCESSORS, PROCESSORS, 0, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(PROCESSORS), Thread::new, new ThreadPoolExecutor.CallerRunsPolicy());
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
                        } else
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
            File file = this.file;
            MessageDigest hashDigest = null;
            try {
                hashDigest = MessageDigest.getInstance(algorithm);
            } catch (NoSuchAlgorithmException e) {
                System.err.println("算法不存在！");
                System.exit(4);
            }
            //1.读取文件并生成字节数组
            try {
                try {
                    byte[] cache = new byte[8 * 1024];
                    try (FileInputStream fileInputStream = new FileInputStream(file)) {
                        int len;
                        while ((len = fileInputStream.read(cache)) != -1)
                            hashDigest.update(cache, 0, len);
                    }
                } catch (Error error) {
                    System.err.println("内存空间不足！");
                    System.exit(5);
                }
            } catch (Exception e) {
                failCount.addAndGet(1);
                e.printStackTrace();
                return;
            }
            //2.提交给编码线程处理
            executor.execute(new DigestThread(hashDigest, file));
        }
    }

    private class DigestThread implements Runnable {

        private final MessageDigest digest;
        private final File file;

        public DigestThread(MessageDigest digest, File file) {
            this.digest = digest;
            this.file = file;
        }

        @Override
        public void run() {
            try {
                String hash = HexBin.encode(digest.digest()), oldFile = hashMap.put(hash, file.toString());
                if (oldFile != null) {
                    repeatCount.addAndGet(1);
                    antiHashMap.put(hash + "\\" + oldFile, "");
                    antiHashMap.put(hash + "\\" + file, "");
                }
            } finally {
                latch.countDown();
            }
        }
    }

}
