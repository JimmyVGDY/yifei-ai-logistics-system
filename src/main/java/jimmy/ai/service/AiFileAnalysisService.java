package jimmy.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * AI 文件分析服务 —— 解析上传的文件（Excel/CSV/TXT），将内容注入 AI 模型进行分析。
 * <p>
 * 支持格式：Excel (.xlsx/.xls)、CSV、纯文本。
 * 文件大小上限 10MB。解析后的内容注入 AI prompt，由模型生成分析摘要。
 */
@Slf4j
@Service
public class AiFileAnalysisService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L; // 10MB
    private static final int MAX_PREVIEW_ROWS = 50;
    private static final int MAX_PREVIEW_CHARS = 8000;
    private static final DataFormatter DATA_FORMATTER = new DataFormatter();

    private final AiModelGateway modelGateway;
    private final AiSensitiveDataMasker masker;

    public AiFileAnalysisService(AiModelGateway modelGateway,
                                  AiSensitiveDataMasker masker) {
        this.modelGateway = modelGateway;
        this.masker = masker;
    }

    /**
     * 分析上传的文件并返回 AI 分析结果。
     *
     * @param file         上传的文件
     * @param userQuestion 用户附带的问题
     * @return AI 分析结果，模型不可用时返回文件内容预览
     */
    public String analyze(MultipartFile file, String userQuestion) {
        if (file == null || file.isEmpty()) {
            return "未检测到上传文件，请选择文件后重试。";
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            return "文件大小超过 10MB 限制，请压缩后重试或联系管理员。";
        }

        // 1. 解析文件内容
        String fileName = file.getOriginalFilename();
        String content = parseFile(file, fileName);
        if (!StringUtils.hasText(content)) {
            return "无法解析文件内容，请确认文件格式为 Excel、CSV 或纯文本。";
        }

        // 2. 注入 AI 分析
        if (modelGateway.configured()) {
            try {
                String prompt = buildAnalysisPrompt(fileName, content, userQuestion);
                Optional<String> result = modelGateway.chat(
                        "你是物流管理系统的数据分析助手。请基于上传文件的内容进行分析，用简洁清晰的中文回答。",
                        prompt, "file_analysis");
                if (result.isPresent()) {
                    return result.get();
                }
            } catch (RuntimeException exception) {
                log.debug("AI 文件分析失败，降级文件预览，reason={}", exception.getMessage());
            }
        }

        // 3. AI 不可用 → 返回文件内容预览
        return "【文件预览】" + fileName + "\n\n" + content;
    }

    /**
     * 根据文件扩展名选择解析方式。
     */
    private String parseFile(MultipartFile file, String fileName) {
        String ext = extension(fileName).toLowerCase();
        try {
            if (".xlsx".equals(ext) || ".xls".equals(ext)) {
                return parseExcel(file);
            }
            if (".csv".equals(ext) || ".txt".equals(ext)) {
                return parseText(file);
            }
            // 其他文本文件尝试 UTF-8 读取
            return parseText(file);
        } catch (Exception exception) {
            log.warn("文件解析失败，fileName={}, reason={}", fileName, exception.getMessage());
            return "";
        }
    }

    /**
     * 解析 Excel 文件为 Markdown 表格。
     */
    private String parseExcel(MultipartFile file) throws Exception {
        StringBuilder result = new StringBuilder();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            for (int sheetIdx = 0; sheetIdx < workbook.getNumberOfSheets() && sheetIdx < 3; sheetIdx++) {
                Sheet sheet = workbook.getSheetAt(sheetIdx);
                if (sheet.getPhysicalNumberOfRows() == 0) continue;

                result.append("### ").append(sheet.getSheetName()).append("\n\n");

                // 构建 Markdown 表格
                StringJoiner header = new StringJoiner(" | ", "| ", " |");
                StringJoiner separator = new StringJoiner(" | ", "| ", " |");
                Row headerRow = sheet.getRow(0);
                if (headerRow != null) {
                    for (int col = 0; col < Math.min(headerRow.getLastCellNum(), 20); col++) {
                        header.add(getCellValue(headerRow, col));
                        separator.add("---");
                    }
                }
                result.append(header).append("\n").append(separator).append("\n");

                int rowCount = 0;
                for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum() && rowCount < MAX_PREVIEW_ROWS; rowIdx++) {
                    Row row = sheet.getRow(rowIdx);
                    if (row == null) continue;
                    StringJoiner values = new StringJoiner(" | ", "| ", " |");
                    for (int col = 0; col < Math.min(headerRow == null ? 10 : headerRow.getLastCellNum(), 20); col++) {
                        values.add(getCellValue(row, col).replace("|", "\\|"));
                    }
                    result.append(values).append("\n");
                    rowCount++;
                }
                result.append("\n");
                if (result.length() > MAX_PREVIEW_CHARS) break;
            }
        }
        // 截断过长内容
        if (result.length() > MAX_PREVIEW_CHARS) {
            return result.substring(0, MAX_PREVIEW_CHARS) + "\n\n...（内容已截断，共 " + result.length() + " 字符）";
        }
        return result.toString();
    }

    /**
     * 解析文本文件（CSV/TXT）。
     */
    private String parseText(MultipartFile file) throws Exception {
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null && lineCount < MAX_PREVIEW_ROWS) {
                result.append(line).append("\n");
                lineCount++;
            }
        }
        if (result.length() > MAX_PREVIEW_CHARS) {
            return result.substring(0, MAX_PREVIEW_CHARS) + "\n\n...（内容已截断）";
        }
        return result.toString();
    }

    private String getCellValue(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        return DATA_FORMATTER.formatCellValue(cell).trim();
    }

    private String buildAnalysisPrompt(String fileName, String content, String userQuestion) {
        return "用户上传了文件：" + fileName
                + (StringUtils.hasText(userQuestion) ? "\n用户问题：" + userQuestion : "")
                + "\n\n文件内容预览：\n" + masker.mask(content)
                + "\n\n请基于以上文件内容回答用户问题，或对文件数据进行摘要分析。";
    }

    private String extension(String filename) {
        if (!StringUtils.hasText(filename)) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }
}
