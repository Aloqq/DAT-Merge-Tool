package org.example.difftool.controller;

import org.example.difftool.model.*;
import org.example.difftool.service.DatParser;
import org.example.difftool.service.DiffService;
import org.example.difftool.service.ExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@CrossOrigin(origins = "*")
public class DatController {

    private static final Logger logger = LoggerFactory.getLogger(DatController.class);

    private final DatParser datParser;
    private final DiffService diffService;
    private final ExportService exportService;

    public DatController(DatParser datParser,
                         DiffService diffService,
                         ExportService exportService) {
        this.datParser = datParser;
        this.diffService = diffService;
        this.exportService = exportService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadResponse upload(@RequestParam("old") MultipartFile oldFile,
                                 @RequestParam("new") MultipartFile newFile) throws IOException {
        logger.info("=== Начало обработки upload ===");
        logger.info("OLD файл: name={}, size={}", oldFile != null ? oldFile.getOriginalFilename() : "null", 
                    oldFile != null ? oldFile.getSize() : 0);
        logger.info("NEW файл: name={}, size={}", newFile != null ? newFile.getOriginalFilename() : "null",
                    newFile != null ? newFile.getSize() : 0);

        if (oldFile == null || newFile == null || oldFile.isEmpty() || newFile.isEmpty()) {
            logger.error("Один или оба файла пусты или null");
            throw new ResponseStatusException(BAD_REQUEST, "Необходимо загрузить оба файла OLD и NEW");
        }

        logger.info("Читаю содержимое файлов...");
        String oldContent = new String(oldFile.getBytes(), StandardCharsets.UTF_8);
        String newContent = new String(newFile.getBytes(), StandardCharsets.UTF_8);
        logger.info("OLD размер контента: {} символов", oldContent.length());
        logger.info("NEW размер контента: {} символов", newContent.length());

        logger.info("Парсинг OLD файла...");
        DatParser.ParseResult oldResult = datParser.parse(oldContent);
        logger.info("OLD формат: {}, записей: {}", oldResult.getFormat(), oldResult.getRecords().size());

        logger.info("Парсинг NEW файла...");
        DatParser.ParseResult newResult = datParser.parse(newContent);
        logger.info("NEW формат: {}, записей: {}", newResult.getFormat(), newResult.getRecords().size());

        if (oldResult.getFormat() != newResult.getFormat()) {
            logger.error("Форматы не совпадают: OLD={}, NEW={}", oldResult.getFormat(), newResult.getFormat());
            throw new ResponseStatusException(BAD_REQUEST, "Форматы файлов не совпадают");
        }

        logger.info("Построение diff...");
        List<DatRecord> diff = diffService.buildDiff(oldResult.getRecords(), newResult.getRecords());
        logger.info("Diff построен: {} записей", diff.size());

        UploadResponse response = new UploadResponse(newResult.getFormat().name().toLowerCase(), diff);
        logger.info("Формирую ответ: format={}, records={}", response.getFormat(), response.getRecords().size());
        
        // Подсчет примерного размера ответа
        int estimatedSize = response.getRecords().size() * 500; // примерная оценка
        logger.info("Примерный размер ответа: ~{} KB", estimatedSize / 1024);
        
        logger.info("=== Конец обработки upload ===");
        logger.info("Возвращаю ответ...");
        return response;
    }

    @PostMapping(value = "/export", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> export(@RequestBody ExportRequest request) {
        if (request.getRecords() == null || request.getRecords().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Нет данных для экспорта");
        }
        DatFormat format = resolveFormat(request.getFormat());
        byte[] payload = exportService.export(request.getRecords(), format);

        String fileName = format == DatFormat.BLOCK ? "merged_item_name.txt" : "merged.txt";

        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_PLAIN)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
            .body(payload);
    }

    private DatFormat resolveFormat(String format) {
        if (!StringUtils.hasText(format)) {
            return DatFormat.LINE;
        }
        return switch (format.toLowerCase()) {
            case "block" -> DatFormat.BLOCK;
            case "config" -> DatFormat.CONFIG;
            default -> DatFormat.LINE;
        };
    }
}

