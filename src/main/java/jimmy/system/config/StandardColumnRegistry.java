package jimmy.system.config;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 标准列注册表 —— 委托给 {@link ModuleManifest} 统一模块清单。
 * <p>
 * 新增模块时只需在 {@link ModuleManifest} 中添加，本类自动同步。
 */
@Component
public class StandardColumnRegistry {

    public record ColumnDef(String fieldName, String label, boolean sensitive) {}

    public List<ColumnDef> columns(String module) {
        ModuleManifest.ModuleEntry entry = ModuleManifest.get(module);
        if (entry == null) return List.of();
        return entry.columns().stream()
                .map(c -> new ColumnDef(c.fieldName(), c.label(), c.sensitive()))
                .toList();
    }

    public Set<String> moduleNames() {
        return ModuleManifest.moduleCodes();
    }
}
