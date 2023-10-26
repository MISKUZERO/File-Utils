package com.mikkku.scanner;

import java.io.File;
import java.io.FileNotFoundException;

public abstract class FileScanner implements Scanner<File> {

    protected abstract void operate(File file);

    @Override
    public void scan(File file) throws FileNotFoundException {
        if (!file.exists())
            throw new FileNotFoundException("The file or directory abstract pathname is not exists");
        recursionScanFilesAndDirs(file.getAbsoluteFile());
    }

    public void scanFiles(File file) throws FileNotFoundException {
        if (!file.exists())
            throw new FileNotFoundException("The file or directory abstract pathname is not exists");
        recursionScanFiles(file.getAbsoluteFile());
    }

    private void recursionScanFiles(File file) {
        if (file.isDirectory()) {
            File[] tmpFiles = file.listFiles();
            if (tmpFiles == null) return;
            for (File tmpFile : tmpFiles) recursionScanFiles(tmpFile);
        } else
            operate(file);
    }

    private void recursionScanFilesAndDirs(File file) {
        if (file.isDirectory()) {
            File[] tmpFiles = file.listFiles();
            if (tmpFiles == null) {
                operate(file);
                return;
            }
            for (File tmpFile : tmpFiles) recursionScanFilesAndDirs(tmpFile);
        }
        operate(file);
    }

}
