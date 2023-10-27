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
    private final AtomicInteger failCount = new AtomicInteger();
    private final AtomicInteger repeatCount = new AtomicInteger();
    private final CopyOnWriteArrayList<String> repeatList = new CopyOnWriteArrayList<>();
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
        );
    }


    public void scan() {
        long begin = System.currentTimeMillis();
        for (File file : files) executor.execute(new ScanThread(file));
        try {
            scanLatch.await();
        } catch (InterruptedException e) {
            System.err.println("任务被终止！");
            System.exit(2);
        }
        executor.shutdown();
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
                        try {
                            //生成字节数组
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
        }
    }

}
