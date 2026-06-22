package jimmy.ai.service;

import jimmy.ai.model.AiExecutionMode;
import jimmy.ai.model.AiExecutionPlan;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 意图规划器。
 * <p>
 * 第一版先采用确定性规则生成执行计划，避免模型误判直接影响后端工具调用。后续接入
 * Spring AI Tool Calling 的结构化规划时，也必须把模型计划交给这里做二次校验和兜底。
 */
@Service
public class AiIntentPlanner {

    public AiExecutionPlan plan(String message) {
        String text = message == null ? "" : message.trim();
        if (!StringUtils.hasText(text)) {
            return AiExecutionPlan.general("空问题，走普通问答兜底");
        }
        if (containsAny(text, "traceId", "operationId", "loginSessionId", "报错", "异常日志", "日志排查", "链路")) {
            return new AiExecutionPlan(AiExecutionMode.LOG_ANALYSIS, List.of("操作日志"), extractKeyword(text), "命中日志排障关键词");
        }
        if (containsAny(text, "统计", "汇总", "排名", "占比", "趋势", "按月", "按天", "关联查询")) {
            return new AiExecutionPlan(AiExecutionMode.READONLY_SQL, List.of("临时统计查询"), extractKeyword(text), "命中统计或关联分析关键词");
        }
        if (containsAny(text, "全貌", "完整链路", "相关的所有", "所有物流信息", "订单和费用", "运单和订单", "任务和轨迹", "异常影响")) {
            return new AiExecutionPlan(AiExecutionMode.JOINED_QUERY, inferModules(text), extractKeyword(text), "命中业务联合查询关键词");
        }
        List<String> modules = inferModules(text);
        if (modules.size() == 1) {
            return new AiExecutionPlan(AiExecutionMode.MODULE_QUERY, modules, extractKeyword(text), "命中明确业务模块");
        }
        if (!modules.isEmpty() || looksLikeKeywordOnly(text)) {
            return new AiExecutionPlan(AiExecutionMode.GLOBAL_SEARCH, modules, extractKeyword(text), "问题较模糊，优先全场景模糊搜索");
        }
        return new AiExecutionPlan(AiExecutionMode.RAG_SEARCH, List.of("系统文档"), extractKeyword(text), "未命中业务查询，优先检索系统文档");
    }

    private List<String> inferModules(String text) {
        List<String> modules = new ArrayList<>();
        addIf(modules, text, "订单管理", "订单", "运单管理", "下单");
        addIf(modules, text, "运单中心", "运单", "运单号", "物流单");
        addIf(modules, text, "调度管理", "调度", "派车");
        addIf(modules, text, "运输任务", "运输任务", "配送任务", "任务");
        addIf(modules, text, "客户管理", "客户", "联系人", "手机号");
        addIf(modules, text, "司机管理", "司机", "驾驶员");
        addIf(modules, text, "车辆管理", "车辆", "车牌");
        addIf(modules, text, "异常管理", "异常", "待处理", "有问题");
        addIf(modules, text, "费用结算", "费用", "收款", "付款");
        addIf(modules, text, "物流轨迹", "轨迹", "位置", "路线");
        /*
         * “运输任务里的异常任务”是一个限定在运输任务模块内的查询，
         * 不能因为出现“异常”两个字就同时扩展到异常管理。
         */
        if (modules.contains("运输任务") && !text.contains("异常管理")) {
            modules.remove("异常管理");
        }
        return modules.stream().distinct().toList();
    }

    private void addIf(List<String> modules, String text, String module, String... words) {
        if (containsAny(text, words)) {
            modules.add(module);
        }
    }

    private boolean looksLikeKeywordOnly(String text) {
        return text.length() >= 2 && text.length() <= 20 && !text.matches(".*[？?。！!，,；;：:].*");
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private String extractKeyword(String text) {
        return text.length() > 80 ? text.substring(0, 80) : text;
    }
}
