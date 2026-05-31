package jimmy.logistics.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模块记录容器 —— 前端通用列表的每一行数据。
 * <p>
 * 继承 {@link LinkedHashMap} 保留字段插入顺序，前端按此顺序渲染表格列。
 * 与 {@link ModuleMutationDTO} 配对使用：前端发送 JSON Object，后端解析为 Map 处理。
 * </p>
 */
public class ModuleRecordVO extends LinkedHashMap<String, Object> {

    public ModuleRecordVO(Map<String, Object> values) {
        super(values);
    }
}
