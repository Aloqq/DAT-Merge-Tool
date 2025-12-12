package org.example.comparator;

import org.example.dto.DiffResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Класс для рекурсивного обхода директорий и сравнения файлов.
 */
public class FileComparator {
    private static final Logger logger = LoggerFactory.getLogger(FileComparator.class);
    private final List<DataComparator> comparators;

    public FileComparator(List<DataComparator> comparators) {
        this.comparators = comparators;
    }

    /**
     * Сравнивает две директории и возвращает карту результатов для каждого файла.
     *
     * @param oldDir путь к старой директории
     * @param newDir путь к новой директории
     * @return карта относительных путей файлов к результатам сравнения
     */
    public Map<String, DiffResult> compareDirectories(String oldDir, String newDir) {
        Map<String, DiffResult> results = new HashMap<>();

        try {
            Path oldPath = Paths.get(oldDir);
            Path newPath = Paths.get(newDir);

            if (!Files.exists(oldPath) && !Files.exists(newPath)) {
                logger.error("Обе директории не существуют: {} и {}", oldDir, newDir);
                return results;
            }

            // Собираем все файлы из обеих директорий
            Set<String> allFiles = new HashSet<>();
            if (Files.exists(oldPath)) {
                collectSupportedFiles(oldPath, oldPath, allFiles);
            }
            if (Files.exists(newPath)) {
                collectSupportedFiles(newPath, newPath, allFiles);
            }

            // Сравниваем каждый файл
            for (String relativePath : allFiles) {
                File oldFile = oldPath.resolve(relativePath).toFile();
                File newFile = newPath.resolve(relativePath).toFile();

                DataComparator comparator = resolveComparator(oldFile, newFile);
                if (comparator == null) {
                    logger.warn("Нет подходящего компаратора для файла: {}", relativePath);
                    continue;
                }

                DiffResult result;

                if (!oldFile.exists() && newFile.exists()) {
                    // Файл добавлен
                    result = comparator.compareWithMissing(newFile, false);
                } else if (oldFile.exists() && !newFile.exists()) {
                    // Файл удален
                    result = comparator.compareWithMissing(oldFile, true);
                } else if (oldFile.exists() && newFile.exists()) {
                    // Оба файла существуют - сравниваем
                    result = comparator.compare(oldFile, newFile);
                } else {
                    // Оба файла не существуют (не должно произойти)
                    continue;
                }

                results.put(relativePath, result);
                logger.info("Обработан файл: {}", relativePath);
            }

        } catch (Exception e) {
            logger.error("Ошибка при сравнении директорий: {}", e.getMessage(), e);
        }

        return results;
    }

    /**
     * Рекурсивно собирает все JSON-файлы из директории.
     *
     * @param rootPath корневой путь (для вычисления относительных путей)
     * @param currentPath текущий путь для обхода
     * @param files множество для сохранения относительных путей
     */
    private void collectSupportedFiles(Path rootPath, Path currentPath, Set<String> files) {
        try {
            if (!Files.exists(currentPath)) {
                return;
            }

            if (Files.isDirectory(currentPath)) {
                Files.list(currentPath).forEach(path -> {
                    collectSupportedFiles(rootPath, path, files);
                });
            } else if (isSupportedFile(currentPath.getFileName().toString())) {
                String relativePath = rootPath.relativize(currentPath).toString();
                files.add(relativePath);
            }
        } catch (Exception e) {
            logger.warn("Ошибка при обходе пути {}: {}", currentPath, e.getMessage());
        }
    }

    private boolean isSupportedFile(String fileName) {
        return comparators.stream().anyMatch(c -> c.supports(fileName));
    }

    private DataComparator resolveComparator(File oldFile, File newFile) {
        String fileName = null;
        if (oldFile != null && oldFile.exists()) {
            fileName = oldFile.getName();
        } else if (newFile != null && newFile.exists()) {
            fileName = newFile.getName();
        }

        if (fileName == null) {
            return null;
        }

        for (DataComparator comparator : comparators) {
            if (comparator.supports(fileName)) {
                return comparator;
            }
        }
        return null;
    }
}

