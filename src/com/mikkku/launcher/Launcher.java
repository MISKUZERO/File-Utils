package com.mikkku.launcher;


import com.mikkku.scanner.DuplicateFileScanner;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.FileAlreadyExistsException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;

public class Launcher {

    public static void main(String[] args) throws IOException {
        // TODO: 2023/11/5 模拟数据，真实数据需要通过命令行传参
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String prefix = System.getProperty("user.dir") + "\\link";
        File linkPath = new File(prefix + formatter.format(LocalDateTime.now()));
        while (!linkPath.mkdir())
            linkPath = new File(prefix + formatter.format(LocalDateTime.now()));
        File[] files = {
                new File("E://我的收藏")
        };
        long begin = System.currentTimeMillis();
        Object[] res = new DuplicateFileScanner("MD5", files).scan();
        long end = System.currentTimeMillis();
        System.out.println("未重复：" + res[0]);
        System.out.println("重复：" + res[1]);
        System.out.println("失败：" + res[2]);
        System.out.println("总计：" + res[3]);
        System.out.println("用时：" + (end - begin) + "ms");
        @SuppressWarnings("unchecked")
        Map<String, String> antiHashMap = (Map<String, String>) res[4];
        @SuppressWarnings("unchecked")
        Map<String, String> antiSizeMap = (Map<String, String>) res[5];
        //写入到vbs脚本（创建快捷方式）
        if (!antiHashMap.isEmpty()) {
            File hashDir = new File(linkPath + "\\hash");
            if (!hashDir.mkdir())
                throw new FileAlreadyExistsException("The directory \"hash\" is already exists");
            writeVBS(antiHashMap, hashDir);
        }
        if (!antiSizeMap.isEmpty()) {
            File sizeDir = new File(linkPath + "\\size");
            if (!sizeDir.mkdir())
                throw new FileAlreadyExistsException("The directory \"size\" is already exists");
            writeVBS(antiSizeMap, sizeDir);
        }
    }

    private static void writeVBS(Map<String, String> map, File dir) {
        try (PrintStream printStream = new PrintStream(dir + "\\make_link.vbs")) {
            int num = 1;
            Set<String> set = map.keySet();
            printStream.println("Set wss=CreateObject(\"WScript.Shell\") ");
            for (String str : set) {
                String[] props = str.split("\\\\", 0);
                printStream.println("Set s=wss.CreateShortcut(\"" +
                        props[0] +
                        "_" + num + "_" +
                        props[props.length - 1] +
                        ".lnk\")");
                printStream.println("s.TargetPath=\"" +
                        str.substring(props[0].length() + 1) +
                        "\"");
                printStream.println("s.Save");
                num++;
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}
