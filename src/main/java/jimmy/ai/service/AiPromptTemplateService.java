package jimmy.ai.service;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import jimmy.ai.entity.AiPromptTemplate;
import jimmy.ai.mapper.AiPromptTemplateMapper;
import jimmy.ai.model.PromptRenderResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * AI Prompt 模板渲染服务。
 * <p>
 * 读取顺序：数据库启用模板 -> 代码默认模板。所有变量必须先在模板的 required/optional
 * 变量清单中声明，服务只把声明过的变量传入 Mustache，降低 Prompt 注入和误传内部上下文的风险。
 */
@Slf4j
@Service
public class AiPromptTemplateService {

    private final AiPromptTemplateMapper templateMapper;
    private final DefaultAiPromptTemplates defaultTemplates;
    private final MustacheFactory mustacheFactory = new DefaultMustacheFactory();
    private final boolean dbEnabled;
    private final boolean fallbackEnabled;

    public AiPromptTemplateService(AiPromptTemplateMapper templateMapper,
                                   DefaultAiPromptTemplates defaultTemplates,
                                   @Value("${app.ai.prompt.db-enabled:true}") boolean dbEnabled,
                                   @Value("${app.ai.prompt.fallback-enabled:true}") boolean fallbackEnabled) {
        this.templateMapper = templateMapper;
        this.defaultTemplates = defaultTemplates;
        this.dbEnabled = dbEnabled;
        this.fallbackEnabled = fallbackEnabled;
    }

    /**
     * 渲染指定模板编码。
     *
     * @param templateCode 模板编码
     * @param variables    候选变量，服务会按模板声明过滤
     * @return 渲染后的模板；数据库不可用或模板异常时自动兜底
     */
    public PromptRenderResult render(String templateCode, Map<String, Object> variables) {
        AiPromptTemplate template = loadTemplate(templateCode)
                .orElseGet(() -> fallbackTemplate(templateCode));
        return renderTemplate(template, variables, template.getTemplateVersion() == null || template.getTemplateVersion() == 0);
    }

    private Optional<AiPromptTemplate> loadTemplate(String templateCode) {
        if (!dbEnabled) {
            return Optional.empty();
        }
        try {
            AiPromptTemplate template = templateMapper.findLatestActive(templateCode);
            if (template == null || !StringUtils.hasText(template.getTemplateContent())) {
                return Optional.empty();
            }
            return Optional.of(template);
        } catch (RuntimeException exception) {
            log.debug("AI Prompt 数据库模板读取失败，使用代码兜底，templateCode={}, reason={}",
                    templateCode, exception.getMessage());
            return Optional.empty();
        }
    }

    private AiPromptTemplate fallbackTemplate(String templateCode) {
        if (!fallbackEnabled) {
            throw new IllegalStateException("AI Prompt 模板不存在，且兜底模板已关闭：" + templateCode);
        }
        return defaultTemplates.find(templateCode)
                .orElseThrow(() -> new IllegalArgumentException("AI Prompt 模板不存在：" + templateCode));
    }

    private PromptRenderResult renderTemplate(AiPromptTemplate template, Map<String, Object> variables, boolean fallback) {
        Map<String, Object> filtered = filterVariables(template, variables == null ? Map.of() : variables);
        Set<String> missing = missingRequiredVariables(template, filtered);
        if (!missing.isEmpty()) {
            if (!fallback && fallbackEnabled) {
                log.warn("AI Prompt 数据库模板缺少必填变量，改用代码兜底，templateCode={}, missing={}",
                        template.getTemplateCode(), missing);
                return renderTemplate(fallbackTemplate(template.getTemplateCode()), variables, true);
            }
            log.warn("AI Prompt 兜底模板缺少必填变量，将按空值渲染，templateCode={}, missing={}",
                    template.getTemplateCode(), missing);
            missing.forEach(key -> filtered.put(key, ""));
        }
        try {
            Mustache mustache = mustacheFactory.compile(new StringReader(template.getTemplateContent()), template.getTemplateCode());
            StringWriter writer = new StringWriter();
            mustache.execute(writer, filtered).flush();
            return new PromptRenderResult(
                    template.getTemplateCode(),
                    template.getTemplateVersion() == null ? 0 : template.getTemplateVersion(),
                    writer.toString(),
                    template.getOutputSchema(),
                    template.getModelPurpose(),
                    fallback
            );
        } catch (Exception exception) {
            if (!fallback && fallbackEnabled) {
                log.warn("AI Prompt 数据库模板渲染失败，改用代码兜底，templateCode={}, reason={}",
                        template.getTemplateCode(), exception.getMessage());
                return renderTemplate(fallbackTemplate(template.getTemplateCode()), variables, true);
            }
            throw new IllegalStateException("AI Prompt 模板渲染失败：" + template.getTemplateCode(), exception);
        }
    }

    private Map<String, Object> filterVariables(AiPromptTemplate template, Map<String, Object> variables) {
        Set<String> allowed = new LinkedHashSet<>();
        allowed.addAll(parseVariables(template.getRequiredVariables()));
        allowed.addAll(parseVariables(template.getOptionalVariables()));
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (String key : allowed) {
            if (variables.containsKey(key)) {
                filtered.put(key, variables.get(key));
            }
        }
        return filtered;
    }

    private Set<String> missingRequiredVariables(AiPromptTemplate template, Map<String, Object> filtered) {
        Set<String> missing = new LinkedHashSet<>();
        for (String key : parseVariables(template.getRequiredVariables())) {
            Object value = filtered.get(key);
            if (value == null || (value instanceof CharSequence text && !StringUtils.hasText(text))) {
                missing.add(key);
            }
        }
        return missing;
    }

    private Set<String> parseVariables(String value) {
        if (!StringUtils.hasText(value)) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .forEach(result::add);
        return result;
    }
}
