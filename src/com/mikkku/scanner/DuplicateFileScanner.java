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

    private volatile boolean scanOver;

    private final int scanThreadCount;
    private final int workThreadCount;
    private final File[] files;
    private final DataUnit[] dataList;
    private final CountDownLatch scanLatch;
    private final CountDownLatch workLatch;
    private final MessageDigest[] messageDigests;
    private final AtomicInteger failCount = new AtomicInteger();
    private final AtomicInteger repeatCount = new AtomicInteger();
    private final CopyOnWriteArrayList<String> repeatList = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, String> uniqueMap = new ConcurrentHashMap<>(INIT_CAPACITY);

    public DuplicateFileScanner(String algorithm, File... files) {// TODO: 2023/10/26 用线程池来优化性能
        this.files = files;
        scanThreadCount = files.length;
        workThreadCount = PROCESSORS - scanThreadCount;
        dataList = new DataUnit[scanThreadCount + workThreadCount];
        scanLatch = new CountDownLatch(scanThreadCount);
        workLatch = new CountDownLatch(workThreadCount);
        messageDigests = new MessageDigest[workThreadCount + scanThreadCount];
        for (int i = 0; i < messageDigests.length; i++) {
            try {
                messageDigests[i] = MessageDigest.getInstance(algorithm);
            } catch (NoSuchAlgorithmException e) {
                System.err.println("启动失败！");
                System.exit(1);
            }
        }
    }


    public void scan() {
        long begin = System.currentTimeMillis();
        for (int i = 0; i < scanThreadCount; i++) new ScanThread(i, files[i]).start();
        for (int i = scanThreadCount; i < scanThreadCount + workThreadCount; i++) new WorkThread(i).start();
        try {
            scanLatch.await();
        } catch (InterruptedException e) {
            System.err.println("任务被终止！");
            System.exit(2);
        }
        scanOver = true;
//        System.out.println("扫描完成！");
        try {
            workLatch.await();
        } catch (InterruptedException e) {
            System.err.println("任务被终止！");
            System.exit(2);
        }
//        System.out.println("全部结束！");
        int unique = uniqueMap.size();
//        System.out.println("未重复文件数：" + unique);
//        System.out.println("重复文件数：" + repeatCount);
//        System.out.println("失败文件数：" + failCount);
        System.out.println("总文件数：" + failCount.addAndGet(repeatCount.addAndGet(unique)));
        long end = System.currentTimeMillis();
//        System.out.println("用时：" + (end - begin) + "ms");
        // TODO: 2023/10/25 写到bat文件（用mklink创建快捷方式），也要写到文本文件
//        for (String s : repeatList) {
//            System.out.println(s);
//        }
    }

    public static class DataUnit {

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

    private class ScanThread extends Thread {

        private final int id;

        private final File path;

        public ScanThread(int id, File path) {
            this.id = id;
            this.path = path;
        }

        @Override
        public void run() {
//            System.out.println(currentThread().getName() + " -> Scanner-" + id + "：开始工作！");
            try {
                new FileScanner<DataUnit>(dataList) {

                    @Override
                    protected void operate(DataUnit[] dataUnits, File file) {
                        byte[] bytes;
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
                        for (int i = scanThreadCount; i < dataUnits.length; i++) // TODO: 2023/10/25  添加索引优化
                            if (dataUnits[i] == null)
                                synchronized (this) {
                                    if (dataUnits[i] == null) {
                                        dataUnits[i] = new DataUnit(bytes, file.toString());
                                        return;
                                    }
                                }
                        //未添加数据，自行处理
                        final MessageDigest messageDigest = messageDigests[id];
                        bytes = messageDigest.digest(bytes);
                        String encode = HexBin.encode(bytes);
//                        System.out.println(encode);
                        //3.处理结果
                        String oldPath = uniqueMap.put(encode, file.toString());
                        if (oldPath != null) {
                            repeatCount.addAndGet(1);
                            if (!repeatList.contains(encode))
                                repeatList.add(encode);
                            if (!repeatList.contains(oldPath))
                                repeatList.add(repeatList.indexOf(encode) + 1, oldPath);
                            repeatList.add(repeatList.indexOf(encode) + 1, file.toString());
                        }
                    }
                }.scanFiles(path);
            } catch (FileNotFoundException e) {
                System.err.println("路径不存在：" + path);
            } finally {
//                System.out.println(currentThread().getName() + " -> Scanner-" + id + "：结束工作！");
                scanLatch.countDown();
            }
        }
    }

    private class WorkThread extends Thread {

        private final int id;

        public WorkThread(int id) {
            this.id = id;
        }

        @Override
        public void run() {
//            System.out.println(currentThread().getName() + " -> Worker-" + id + "：开始工作！");
            try {
                final MessageDigest messageDigest = messageDigests[id];
                DataUnit dataUnit;
                while ((dataUnit = dataList[id]) != null || !scanOver)
                    if (dataUnit != null) {
                        //1.取字节数据
                        dataList[id] = null;
                        byte[] bytes = dataUnit.getBytes();
                        //2.生成MD5码
                        bytes = messageDigest.digest(bytes);
                        String encode = HexBin.encode(bytes);
//                        System.out.println(encode);
                        //3.处理结果
                        String oldPath = uniqueMap.put(encode, dataUnit.getPath());
                        if (oldPath != null) {
                            repeatCount.addAndGet(1);
                            if (!repeatList.contains(encode))
                                repeatList.add(encode);
                            if (!repeatList.contains(oldPath))
                                repeatList.add(repeatList.indexOf(encode) + 1, oldPath);
                            repeatList.add(repeatList.indexOf(encode) + 1, dataUnit.getPath());
                        }
                    }
            } finally {
//                System.out.println(currentThread().getName() + " -> Worker-" + id + "：结束工作！");
                workLatch.countDown();
            }
        }
    }

}
