package com.mikkku.launcher;


import com.mikkku.scanner.DuplicateFileScanner;

import java.io.File;

public class Launcher {

    public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException {
        // TODO: 2023/10/24 处理参数args，给多个路径不能是包含关系的路径
        for (int i = 0; i < 10000; i++) {
            new DuplicateFileScanner("MD5",
//                    new File("C:\\Users\\Administrator\\Desktop\\shattered-pixel-dungeon\\LICENSE.txt"),
//                    new File("C:\\Users\\Administrator\\Desktop\\shattered-pixel-dungeon\\gradlew.bat"),
//                    new File("C:\\Users\\Administrator\\Desktop\\shattered-pixel-dungeon\\README.md"),
                    new File("C:\\Users\\Administrator\\Desktop\\openJDK\\hotspot-69087d08d473\\.hg_archival.txt")
            ).scan();
        }


    }


}
