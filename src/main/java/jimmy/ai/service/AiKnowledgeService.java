package jimmy.ai.service;

import jimmy.ai.model.AiCitation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * AI 文档知识检索 —— 第一版基于 Markdown 文件做轻量关键词检索。
 */
@Slf4j
@Service
public class AiKnowledgeService {

    private static final int SNIPPET_LENGTH = 420;

    public List<AiCitation> search(String keyword) {
        String normalized = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase(Locale.ROOT) : "";
        List<AiCitation> citations = new ArrayList<>();
        for (Path path : candidateDocuments()) {
            if (citations.size() >= 5) {
                break;
            }
            try {
                if (!Files.exists(path)) {
                    continue;
                }
                String content = Files.readString(path, StandardCharsets.UTF_8);
                String lowerContent = content.toLowerCase(Locale.ROOT);
                int index = StringUtils.hasText(normalized) ? lowerContent.indexOf(normalized) : -1;
                if (index < 0 && !path.getFileName().toString().equalsIgnoreCase("README.md")) {
                    continue;
                }
                citations.add(new AiCitation("DOC", path.getFileName().toString(), path.toString(), snippet(content, Math.max(index, 0))));
            } catch (IOException exception) {
                log.debug("AI 文档检索跳过不可读文件，path={}, reason={}", path, exception.getMessage());
            }
        }
        return citations;
    }

    private List<Path> candidateDocuments() {
        List<Path> paths = new ArrayList<>();
        paths.add(Path.of("README.md"));
        Path docs = Path.of("docs");
        if (Files.isDirectory(docs)) {
            try (var stream = Files.list(docs)) {
                stream.filter(path -> path.getFileName().toString().endsWith(".md"))
                        .sorted()
                        .forEach(paths::add);
            } catch (IOException exception) {
                log.debug("AI 文档目录读取失败，reason={}", exception.getMessage());
            }
        }
        return paths;
    }

    private String snippet(String content, int index) {
        int start = Math.max(0, index - 120);
        int end = Math.min(content.length(), start + SNIPPET_LENGTH);
        return content.substring(start, end).replaceAll("\\s+", " ").trim();
    }
}
