package jimmy.logistics.model;

import java.math.BigDecimal;

/**
 * 趋势图数据点 —— 用于运营看板的订单趋势和收入趋势图表。
 * <p>
 * {@code name} 为横轴标签（如日期/月份），{@code total} 为纵轴数值。
 * </p>
 */
public record TrendPointVO(
        /** 横轴标签（日期 yyyy-MM-dd 或月份 yyyy-MM） */
        String name,
        /** 纵轴数值（订单数或收入金额） */
        BigDecimal total) {

    public String getName() { return name; }

    public BigDecimal getTotal() { return total; }
}
