package com.mikkku.launcher;


import com.mikkku.scanner.DuplicateFileScanner;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.List;

public class Launcher {

    public static void main(String[] args) {
        // TODO: 2023/10/24 处理参数args，给多个路径不能是包含关系的路径
        long begin = System.currentTimeMillis();
        Object[] res = new DuplicateFileScanner(
                "MD5",
                new File("C:\\Users\\Administrator\\Desktop\\shattered-pixel-dungeon"),
                new File("C:\\Users\\Administrator\\Desktop\\openJDK")
        ).scan();
        long end = System.currentTimeMillis();
        System.out.println("未重复文件：" + res[0]);
        System.out.println("重复文件：" + res[1]);
        System.out.println("大文件：" + res[2]);
        System.out.println("总计：" + res[3]);
        System.out.println("用时：" + (end - begin) + "ms");
        @SuppressWarnings("unchecked")
        List<String> repeatList = (List<String>) res[4];
        @SuppressWarnings("unchecked")
        List<String> oversizeList = (List<String>) res[5];
        // TODO: 2023/10/25 写到bat文件（用mklink创建快捷方式），也要写到文本文件
        String currentPath = System.getProperty("user.dir") + "\\";
        if (!repeatList.isEmpty()) {
            try (PrintStream repeatListPS = new PrintStream(currentPath + "hash_collision.txt")) {
                for (String s : repeatList) repeatListPS.println(s);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        if (!oversizeList.isEmpty()) {
            try (PrintStream overSizeListPS = new PrintStream(currentPath + "oversize_file.txt")) {
                for (String s : oversizeList) overSizeListPS.println(s);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }
}
