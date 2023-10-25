package com.mikkku.launcher;


import com.mikkku.scanner.DuplicateFileScanner;

import java.io.File;

public class Launcher {

    public static void main(String[] args) {
        // TODO: 2023/10/24 处理参数args，给多个路径不能是包含关系的路径
        new DuplicateFileScanner(
                new File("C:\\Users\\Administrator\\Desktop\\new world tmp"),
                new File("C:\\Users\\Administrator\\Desktop\\shattered-pixel-dungeon"),
                new File("C:\\Users\\Administrator\\Desktop\\openJDK")
        ).scan();

    }

}
