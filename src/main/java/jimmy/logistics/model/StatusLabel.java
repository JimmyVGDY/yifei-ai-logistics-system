package jimmy.logistics.model;

import java.util.HashMap;
import java.util.Map;

public final class StatusLabel {

    private static final Map<String, String> LABELS = buildLabels();

    private StatusLabel() {
    }

    public static String label(String status) {
        if (status == null || status.trim().isEmpty()) {
            return "";
        }
        String label = LABELS.get(status);
        return label == null ? "未知状态(" + status + ")" : label;
    }

    private static Map<String, String> buildLabels() {
        Map<String, String> map = new HashMap<>();
        map.put("ACTIVE", "启用");
        map.put("ENABLED", "启用");
        map.put("DISABLED", "停用");
        map.put("INACTIVE", "停用");
        map.put("AVAILABLE", "空闲");
        map.put("IDLE", "空闲");
        map.put("RESTING", "休息中");
        map.put("ON_ROUTE", "运输中");
        map.put("MAINTENANCE", "维修中");
        map.put("PAUSED", "暂停");
        map.put("CREATED", "已创建");
        map.put("WAIT_DISPATCH", "待调度");
        map.put("DISPATCHED", "已调度");
        map.put("ASSIGNED", "已分配");
        map.put("PROCESSING", "处理中");
        map.put("PICKED_UP", "已揽收");
        map.put("IN_TRANSIT", "运输中");
        map.put("TRANSPORTING", "运输中");
        map.put("ARRIVED", "已到达");
        map.put("DELIVERING", "派送中");
        map.put("DELIVERED", "已送达");
        map.put("SIGNED", "已签收");
        map.put("COMPLETED", "已完成");
        map.put("FINISHED", "已完成");
        map.put("CANCELLED", "已取消");
        map.put("CANCELED", "已取消");
        map.put("EXCEPTION", "异常");
        map.put("WAIT_HANDLE", "待处理");
        map.put("HANDLING", "处理中");
        map.put("CLOSED", "已关闭");
        map.put("PAID", "已付款");
        map.put("UNPAID", "未付款");
        map.put("PART_PAID", "部分付款");
        map.put("REFUNDED", "已退款");
        map.put("SUCCESS", "成功");
        map.put("FAILED", "失败");
        map.put("BLOCKED", "已限流");
        map.put("QUERY_FALLBACK", "查询降级");
        map.put("1", "启用");
        map.put("0", "停用");
        return map;
    }
}
