package jimmy.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册服务 —— 为当前用户构建 OpenAI function-calling 格式的工具定义列表。
 * <p>
 * 工具定义包含 name、description 和 JSON Schema 格式的 parameters，
 * 供 Python AI 服务在对话编排时使用。
 */
@Slf4j
@Service
public class ToolRegistryService {

    private static final List<String> ALL_MODULES = List.of(
            "orders", "waybills", "customers", "dispatches", "tasks",
            "tracks", "drivers", "vehicles", "exceptions", "fees",
            "users", "roles", "files", "operationLogs"
    );

    /**
     * 为指定用户构建工具注册表。
     *
     * @param userId      用户标识
     * @param permissions 用户权限列表
     * @return 工具定义列表，每条包含 name、description 和 JSON Schema parameters
     */
    public List<Map<String, Object>> buildRegistry(String userId, List<String> permissions) {
        List<Map<String, Object>> tools = new ArrayList<>();

        tools.add(buildQueryBusinessModule());
        tools.add(buildGlobalFuzzySearch());
        tools.add(buildJoinedBusinessQuery());
        tools.add(buildQueryDashboard());
        tools.add(buildQueryLogAnalysis());
        tools.add(buildExecuteReadonlySql());
        tools.add(buildContinueCursor());

        log.debug("为 userId={} 构建工具注册表，共 {} 个工具", maskUserId(userId), tools.size());
        return tools;
    }

    // ---- 工具定义构建方法 ----

    private Map<String, Object> buildQueryBusinessModule() {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", "query_business_module");
        tool.put("description", "查询单个物流业务模块的数据。用户说的业务名就是 module (物流轨迹=tracks 费用=fees 订单=orders 运单=waybills 调度=dispatches 任务=tasks 异常=exceptions 司机=drivers 车辆=vehicles 客户=customers)。用户说的具体查询条件(人名/单号/车牌等)才是 keyword；用户只是要看某模块的整体数据时 keyword 留空。用户说的时间范围(今天/本周/这个月/上个月等)要计算成 startTime/endTime 传入，格式 yyyy-MM-dd HH:mm:ss。");

        Map<String, Object> properties = new LinkedHashMap<>();
        addStringProp(properties, "module", "模块编码，必须从以下精确取值: orders / waybills / customers / dispatches / tasks / tracks / drivers / vehicles / exceptions / fees / users / roles / files / operationLogs");
        addStringProp(properties, "keyword", "具体搜索条件(人名/单号/车牌/地址等)。严禁编造关键词：没有就传空字符串，不能截取模块名拼凑");
        addStringProp(properties, "startTime", "开始时间 yyyy-MM-dd HH:mm:ss, 用户说这个月就传本月1日 00:00:00");
        addStringProp(properties, "endTime", "结束时间 yyyy-MM-dd HH:mm:ss, 用户说这个月就传今天 23:59:59");

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("module"));  // keyword 可选，无 keyword 时查模块全部

        tool.put("parameters", parameters);
        return tool;
    }

    private Map<String, Object> buildGlobalFuzzySearch() {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", "global_fuzzy_search");
        tool.put("description", "全场景模糊搜索，在当前账号可访问的所有业务模块中按关键词查找。适用于用户不确定具体模块或需要跨模块搜索的场景。按业务相关度排序输出结果。");

        Map<String, Object> properties = new LinkedHashMap<>();
        addStringProp(properties, "keyword", "搜索关键词，如客户名称、订单号、运单号、司机姓名、车牌号、地址、状态等");
        addStringProp(properties, "startTime", "查询开始时间，格式 yyyy-MM-dd HH:mm:ss");
        addStringProp(properties, "endTime", "查询结束时间，格式 yyyy-MM-dd HH:mm:ss");

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("keyword"));

        tool.put("parameters", parameters);
        return tool;
    }

    private Map<String, Object> buildJoinedBusinessQuery() {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", "joined_business_query");
        tool.put("description", "跨模块业务联合查询，用于查看客户全貌、订单完整链路、司机/任务关联链、异常影响范围等。一次查询自动关联多个相关模块。场景可选：customer（客户全貌）、order（订单生命周期）、driver（司机与任务链）、vehicle（车辆调度链）、exception（异常影响）、business（综合业务）。");

        Map<String, Object> properties = new LinkedHashMap<>();
        addStringProp(properties, "scene", "联合查询场景：customer（客户全貌）、order（订单生命周期）、driver（司机与任务链）、vehicle（车辆调度链）、exception（异常影响范围）、business（综合业务查询）");
        addStringProp(properties, "keyword", "搜索关键词，如客户名称、订单号、运单号、司机姓名、车牌号等");
        addStringProp(properties, "startTime", "查询开始时间，格式 yyyy-MM-dd HH:mm:ss");
        addStringProp(properties, "endTime", "查询结束时间，格式 yyyy-MM-dd HH:mm:ss");

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("scene", "keyword"));

        tool.put("parameters", parameters);
        return tool;
    }

    private Map<String, Object> buildQueryDashboard() {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", "query_dashboard");
        tool.put("description", "查询运营看板/汇总数据，包括今日待处理订单数、近期异常统计、收入概览等。无需参数，基于当前账号权限自动返回可见数据。");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of());

        tool.put("parameters", parameters);
        return tool;
    }

    private Map<String, Object> buildQueryLogAnalysis() {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", "query_log_analysis");
        tool.put("description", "分析系统操作日志，支持按 traceId、operationId、loginSessionId、userId 等链路标识定位问题。用于排查接口错误、性能瓶颈和异常链路。");

        Map<String, Object> properties = new LinkedHashMap<>();
        addStringProp(properties, "traceId", "分布式链路追踪 ID，用于关联同一请求链路上的所有操作日志");
        addStringProp(properties, "operationId", "操作 ID，用于定位单次操作日志");
        addStringProp(properties, "loginSessionId", "登录会话 ID，用于追踪同一登录会话内的所有操作");
        addStringProp(properties, "userId", "用户 ID，用于查看指定用户的操作日志");
        addStringProp(properties, "startTime", "查询开始时间，格式 yyyy-MM-dd HH:mm:ss");
        addStringProp(properties, "endTime", "查询结束时间，格式 yyyy-MM-dd HH:mm:ss");

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("startTime", "endTime"));

        tool.put("parameters", parameters);
        return tool;
    }

    private Map<String, Object> buildExecuteReadonlySql() {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", "execute_readonly_sql");
        tool.put("description", "执行一条经过安全校验的 SELECT 只读 SQL 查询，用于复杂统计、多表关联等无法通过标准模块查询满足的场景。SQL 必须只包含 SELECT 语句，禁止写操作。系统会自动校验表名白名单、字段权限和语句安全性。");

        Map<String, Object> properties = new LinkedHashMap<>();
        addStringProp(properties, "sql", "经过校验的 SELECT 只读 SQL 语句。仅支持单条 SELECT，禁止 INSERT/UPDATE/DELETE/DROP 等写操作，表名和字段必须在系统白名单内");

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("sql"));

        tool.put("parameters", parameters);
        return tool;
    }

    private Map<String, Object> buildContinueCursor() {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", "continue_cursor");
        tool.put("description", "继续翻页查看上一轮查询的剩余结果。当上一轮查询返回了 cursorId 且提示还有更多数据时，使用此工具获取后续数据页。");

        Map<String, Object> properties = new LinkedHashMap<>();
        addStringProp(properties, "cursorId", "上一轮工具查询结果中返回的 cursorId，用于定位继续查询的上下文");

        Map<String, Object> offsetProp = new LinkedHashMap<>();
        offsetProp.put("type", "integer");
        offsetProp.put("description", "起始偏移量，即已返回的记录总数。例如已返回 20 条则 offset=20，继续获取第 21 条起的数据");
        properties.put("offset", offsetProp);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("cursorId", "offset"));

        tool.put("parameters", parameters);
        return tool;
    }

    private void addStringProp(Map<String, Object> properties, String name, String description) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", "string");
        prop.put("description", description);
        properties.put(name, prop);
    }

    private String maskUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return "anonymous";
        }
        return userId.length() > 8 ? userId.substring(0, 4) + "****" : userId;
    }
}
