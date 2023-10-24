package com.mikkku.scanner;

import java.io.File;
import java.io.FileNotFoundException;

public abstract class FileScanner<E> implements Scanner<File> {

    protected E[] elements;

    protected abstract void operate(E[] elements, File file);

    public FileScanner(E[] elements) {
        this.elements = elements;
    }

    @Override
    public void scan(File file) throws FileNotFoundException {
        if (!file.exists())
            throw new FileNotFoundException("The file or directory abstract pathname is not exists");
        recursionScanFilesAndDirs(elements, file.getAbsoluteFile());
    }

    public void scanFiles(File file) throws FileNotFoundException {
        if (!file.exists())
            throw new FileNotFoundException("The file or directory abstract pathname is not exists");
        recursionScanFiles(elements, file.getAbsoluteFile());
    }

    private void recursionScanFiles(E[] elements, File file) {
        if (file.isDirectory()) {
            File[] tmpFiles = file.listFiles();
            if (tmpFiles == null) return;
            for (File tmpFile : tmpFiles) recursionScanFiles(elements, tmpFile);
        } else
            operate(elements, file);
    }

    private void recursionScanFilesAndDirs(E[] elements, File file) {
        if (file.isDirectory()) {
            File[] tmpFiles = file.listFiles();
            if (tmpFiles == null) {
                operate(elements, file);
                return;
            }
            for (File tmpFile : tmpFiles) recursionScanFilesAndDirs(elements, tmpFile);
        }
        operate(elements, file);
    }

}
