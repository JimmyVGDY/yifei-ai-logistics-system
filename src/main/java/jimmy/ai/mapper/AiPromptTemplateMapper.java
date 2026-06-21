package jimmy.ai.mapper;

import jimmy.ai.entity.AiPromptTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * AI Prompt 模板 Mapper。
 * <p>
 * 只读取当前启用的最新版本模板；模板缺失时由服务层回退到代码默认模板。
 */
@Mapper
public interface AiPromptTemplateMapper {

    /**
     * 按模板编码读取启用状态下的最新版本。
     */
    AiPromptTemplate findLatestActive(@Param("templateCode") String templateCode);
}
