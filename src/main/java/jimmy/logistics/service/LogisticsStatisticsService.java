package jimmy.logistics.service;

import jimmy.logistics.mapper.LogisticsStatisticsMapper;
import jimmy.logistics.model.TrendPointVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class LogisticsStatisticsService {

    private final LogisticsStatisticsMapper logisticsStatisticsMapper;

    public LogisticsStatisticsService(LogisticsStatisticsMapper logisticsStatisticsMapper) {
        this.logisticsStatisticsMapper = logisticsStatisticsMapper;
    }

    public List<TrendPointVO> orderTrend(int days) {
        int safeDays = Math.max(1, Math.min(days, 30));
        return logisticsStatisticsMapper.selectOrderTrend(-safeDays).stream()
                .map(row -> new TrendPointVO(String.valueOf(row.get("statDate")), toBigDecimal(row.get("total"))))
                .collect(Collectors.toList());
    }

    public List<TrendPointVO> incomeTrend(int months) {
        int safeMonths = Math.max(1, Math.min(months, 12));
        return logisticsStatisticsMapper.selectIncomeTrend(-safeMonths).stream()
                .map(row -> new TrendPointVO(String.valueOf(row.get("statMonth")), toBigDecimal(row.get("total"))))
                .collect(Collectors.toList());
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
