package com.mikkku.util;

import com.mikkku.exception.FileOversizeException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class FileUtils {

    public static byte[] fileToBytes(File file) throws IOException, FileOversizeException, InterruptedException {
        long size = file.length();
        if (size > Integer.MAX_VALUE)
            throw new FileOversizeException("Cannot process file larger than 2GB");
        try {
            byte[] data = new byte[(int) size];
            byte[] cache = new byte[8 * 1024];
            int len, offset = 0;
            try (FileInputStream fileInputStream = new FileInputStream(file)) {
                while ((len = fileInputStream.read(cache)) != -1) {
                    System.arraycopy(cache, 0, data, offset, len);
                    offset += len;
                }
            }
            return data;
        } catch (Error error) {
            Thread.sleep(size / 1000000);
            return fileToBytes(file);
        }
    }

}
