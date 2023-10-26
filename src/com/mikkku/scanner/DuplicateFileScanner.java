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

    private final int scanThreadCount;
    private final File[] files;
    private final MessageDigest[] messageDigests;
    private final CountDownLatch scanLatch;
    private final ThreadPoolExecutor executor;
    private final AtomicInteger failCount = new AtomicInteger();
    private final AtomicInteger repeatCount = new AtomicInteger();
    private final CopyOnWriteArrayList<String> repeatList = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, String> uniqueMap = new ConcurrentHashMap<>(INIT_CAPACITY);

    public DuplicateFileScanner(String algorithm, File... files) {// TODO: 2023/10/26 用线程池来优化性能
        this.files = files;
        scanThreadCount = files.length;
        scanLatch = new CountDownLatch(scanThreadCount);
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
                System.out.println("Worker-" + id + ": " + Thread.currentThread().getName() + "：线程创建！");
                return new Worker(r, messageDigests[id++]);//TODO 需要原子类？
            }
        };
        executor = new ThreadPoolExecutor(
                PROCESSORS,
                PROCESSORS,
                0,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        ) {
            @Override
            protected void beforeExecute(Thread t, Runnable r) {
                if (r.getClass() == WorkThread.class)
                    ((WorkThread) r).messageDigest = ((Worker) t).messageDigest;
            }
        };
    }


    public void scan() {
        for (int i = 0; i < scanThreadCount; i++) {
            executor.execute(new ScanThread(files[i]));
        }
        long begin = System.currentTimeMillis();
        try {
            scanLatch.await();
        } catch (InterruptedException e) {
            System.err.println("任务被终止！");
            System.exit(2);
        }
        System.out.println("扫描完成！");
        System.out.println("全部结束！");
        int unique = uniqueMap.size();
        System.out.println("未重复文件数：" + unique);
        System.out.println("重复文件数：" + repeatCount);
        System.out.println("失败文件数：" + failCount);
        System.out.println("总文件数：" + failCount.addAndGet(repeatCount.addAndGet(unique)));
        long end = System.currentTimeMillis();
        System.out.println("用时：" + (end - begin) + "ms");
        // TODO: 2023/10/25 写到bat文件（用mklink创建快捷方式），也要写到文本文件
        for (String s : repeatList) {
            System.out.println(s);
        }
        System.exit(0);
    }

    private static class Worker extends Thread {

        private final MessageDigest messageDigest;

        private Worker(Runnable runnable, MessageDigest messageDigest) {
            super(runnable);
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
            System.out.println("Scan Thread: " + Thread.currentThread().getName() + "：开始工作！");
            try {
                new FileScanner() {

                    @Override
                    protected void operate(File file) {
                        byte[] bytes;
                        //生成字节数组
                        try {
                            bytes = FileUtils.fileToBytes(file);
                        } catch (IOException ioException) {
                            ioException.printStackTrace();
                            return;
                        } catch (FileOversizeException fileOversizeException) {// TODO: 2023/10/24 大文件处理
                            System.err.println("不能处理超过2GB的文件：" + file);
                            failCount.addAndGet(1);
                            return;
                        }
                        executor.execute(new WorkThread(bytes, file));
                    }
                }.scanFiles(path);
            } catch (FileNotFoundException e) {
                System.err.println("路径不存在：" + path);
            } finally {
                System.out.println("Scan Thread: " + Thread.currentThread().getName() + "：结束工作！");
                scanLatch.countDown();
            }
        }
    }

    private class WorkThread implements Runnable {

        private final byte[] bytes;
        private final File file;
        private MessageDigest messageDigest;

        public WorkThread(byte[] bytes, File file) {
            this.bytes = bytes;
            this.file = file;
        }

        @Override
        public void run() {
            System.out.println("Work Thread: " + Thread.currentThread().getName() + "：开始工作！");
            if (!repeatList.contains(messageDigest.hashCode() + ""))
                repeatList.add(0, messageDigest.hashCode() + "");
            //1.生成MD5码
            String encode = HexBin.encode(messageDigest.digest(bytes));
            System.out.println(encode);
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
            System.out.println("Work Thread: " + Thread.currentThread().getName() + "：结束工作！");
        }
    }

}
