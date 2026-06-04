package jimmy.logistics.service;

import jimmy.logistics.mapper.LogisticsStatisticsMapper;
import jimmy.logistics.model.TrendPointVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 物流统计服务 —— 订单趋势和收入趋势的聚合查询。
 */
@Service
public class LogisticsStatisticsService {

    private final LogisticsStatisticsMapper logisticsStatisticsMapper;

    public LogisticsStatisticsService(LogisticsStatisticsMapper logisticsStatisticsMapper) {
        this.logisticsStatisticsMapper = logisticsStatisticsMapper;
    }

    /**
     * 查询订单数量趋势（天数上限 30）
     */
    public List<TrendPointVO> orderTrend(int days) {
        int safeDays = Math.max(1, Math.min(days, 30));
        List<Map<String, Object>> rows = logisticsStatisticsMapper.selectOrderTrend(-safeDays);
        List<TrendPointVO> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            result.add(new TrendPointVO(String.valueOf(row.get("statDate")), toBigDecimal(row.get("total"))));
        }
        return result;
    }

    /**
     * 查询收入金额趋势（月数上限 12）
     */
    public List<TrendPointVO> incomeTrend(int months) {
        int safeMonths = Math.max(1, Math.min(months, 12));
        List<Map<String, Object>> rows = logisticsStatisticsMapper.selectIncomeTrend(-safeMonths);
        List<TrendPointVO> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            result.add(new TrendPointVO(String.valueOf(row.get("statMonth")), toBigDecimal(row.get("total"))));
        }
        return result;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value));
    }
}
