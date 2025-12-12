package org.example.comparator;

import org.example.dto.DiffResult;

import java.io.File;

/**
 * Общий контракт для сравнения файлов.
 */
public interface DataComparator {

    /**
     * Проверяет, поддерживает ли компаратор указанный файл.
     *
     * @param fileName имя файла
     * @return true, если формат поддерживается
     */
    boolean supports(String fileName);

    /**
     * Сравнивает два файла.
     *
     * @param oldFile файл из старой директории
     * @param newFile файл из новой директории
     * @return результат сравнения
     */
    DiffResult compare(File oldFile, File newFile);

    /**
     * Обрабатывает случай, когда один из файлов отсутствует.
     *
     * @param existingFile существующий файл
     * @param isOld        true, если файл из старой директории
     * @return результат сравнения
     */
    DiffResult compareWithMissing(File existingFile, boolean isOld);
}

