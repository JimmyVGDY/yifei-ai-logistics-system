package jimmy.ai.service;

import jimmy.ai.model.DocumentChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Markdown 文档分块器 —— 按二级标题切割，段落边界对齐，块间重叠。
 * <p>
 * 策略：以 {@code ## } 为自然分隔点，每块不超过 {@value #MAX_CHUNK_CHARS} 字符，
 * 相邻块间保留 {@value #OVERLAP_CHARS} 字符重叠，避免边界信息丢失。
 */
@Slf4j
@Component
public class AiDocumentChunker {

    /** 每块最大字符数（中文约 800 字） */
    private static final int MAX_CHUNK_CHARS = 800;

    /** 块间重叠字符数 */
    private static final int OVERLAP_CHARS = 80;

    /**
     * 将 Markdown 文件按 ## 标题分块。
     * <p>
     * 第一块为"文件头"（# 标题 + 简介段），后续按 ## 切割。
     * 如果某个 section 过长（超过 MAX_CHUNK_CHARS），继续按段落切分。
     *
     * @param filePath Markdown 文件路径
     * @param fileName 文件名（作为元数据 source）
     * @return 文档块列表
     */
    public List<DocumentChunk> chunk(Path filePath, String fileName) {
        try {
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            if (!StringUtils.hasText(content)) {
                return List.of();
            }
            // 提取文档标题（第一个 # 标题）
            String docTitle = extractDocTitle(content);
            // 按 ## 切割为 sections
            List<Section> sections = splitByH2(content, docTitle);
            // 每个 section 按长度分块
            List<DocumentChunk> chunks = new ArrayList<>();
            for (Section section : sections) {
                chunks.addAll(chunkSection(section, fileName, docTitle));
            }
            log.debug("文档分块完成，file={}, sections={}, chunks={}", fileName, sections.size(), chunks.size());
            return chunks;
        } catch (Exception exception) {
            log.warn("文档分块失败，file={}, reason={}", fileName, exception.getMessage());
            return List.of();
        }
    }

    /**
     * 提取文档一级标题（# 开头），作为元数据中的 title。
     */
    private String extractDocTitle(String content) {
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ") && !trimmed.startsWith("## ")) {
                return trimmed.substring(2).trim();
            }
        }
        return "";
    }

    /**
     * 按 ## 二级标题分割文档为 section 列表。
     * <p>
     * 第一个 section 是文件头（# 标题 到 第一个 ## 之间的内容），
     * 后续每个 ## 及其内容为一个 section。
     */
    private List<Section> splitByH2(String content, String docTitle) {
        List<Section> sections = new ArrayList<>();
        String[] lines = content.split("\\R", -1);
        StringBuilder currentHeading = new StringBuilder(docTitle.isEmpty() ? "" : docTitle);
        StringBuilder currentBody = new StringBuilder();
        boolean firstSection = true;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("## ")) {
                // 遇到新的 ##，保存上一个 section
                if (currentBody.length() > 0 || !firstSection) {
                    sections.add(new Section(currentHeading.toString(), currentBody.toString()));
                }
                currentHeading = new StringBuilder(trimmed.substring(3).trim());
                currentBody = new StringBuilder();
                firstSection = false;
            } else {
                if (currentBody.length() > 0) {
                    currentBody.append("\n");
                }
                currentBody.append(line);
            }
        }
        // 最后一个 section
        if (currentBody.length() > 0) {
            sections.add(new Section(currentHeading.toString(), currentBody.toString()));
        }
        return sections;
    }

    /**
     * 将单个 section 按字符数分块，相邻块保留重叠。
     */
    private List<DocumentChunk> chunkSection(Section section, String fileName, String docTitle) {
        List<DocumentChunk> chunks = new ArrayList<>();
        String text = section.body().trim();
        if (!StringUtils.hasText(text)) {
            return chunks;
        }
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + MAX_CHUNK_CHARS, text.length());
            // 尽量在段落边界处断开（向后找最近的空行）
            if (end < text.length()) {
                int breakPoint = text.lastIndexOf("\n\n", end);
                if (breakPoint > start + MAX_CHUNK_CHARS / 2) {
                    end = breakPoint;
                }
            }
            String chunkText = text.substring(start, end).trim();
            if (StringUtils.hasText(chunkText)) {
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("source", fileName);
                metadata.put("title", docTitle);
                metadata.put("section", section.heading());
                metadata.put("startPos", start);
                chunks.add(new DocumentChunk(UUID.randomUUID().toString(), chunkText, metadata));
            }
            // 下一块从 end - overlap 开始
            start = end - OVERLAP_CHARS;
            if (start >= text.length()) {
                break;
            }
            // 确保 start 在段落开头
            int paragraphStart = text.indexOf("\n\n", start);
            if (paragraphStart > start && paragraphStart - start < OVERLAP_CHARS * 2) {
                start = paragraphStart + 2;
            }
        }
        return chunks;
    }

    /**
     * 文档分块后的 section，包含当前章节标题和正文。
     */
    private record Section(String heading, String body) {
    }
}
