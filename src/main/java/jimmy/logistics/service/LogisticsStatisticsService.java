package jimmy.logistics.service;

import jimmy.logistics.mapper.LogisticsStatisticsMapper;
import jimmy.logistics.model.TrendPointVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class LogisticsStatisticsService {

    private final LogisticsStatisticsMapper logisticsStatisticsMapper;

    public LogisticsStatisticsService(LogisticsStatisticsMapper logisticsStatisticsMapper) {
        this.logisticsStatisticsMapper = logisticsStatisticsMapper;
    }

    public List<TrendPointVO> orderTrend(int days) {
        int safeDays = Math.max(1, Math.min(days, 30));
        List<Map<String, Object>> rows = logisticsStatisticsMapper.selectOrderTrend(-safeDays);
        List<TrendPointVO> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            result.add(new TrendPointVO(String.valueOf(row.get("statDate")), toBigDecimal(row.get("total"))));
        }
        return result;
    }

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
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value));
    }
}
