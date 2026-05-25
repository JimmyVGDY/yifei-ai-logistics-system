package jimmy.logistics.service;

import jimmy.logistics.model.TrendPointVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class LogisticsStatisticsService {

    private final JdbcTemplate jdbcTemplate;

    public LogisticsStatisticsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TrendPointVO> orderTrend(int days) {
        int safeDays = Math.max(1, Math.min(days, 30));
        return jdbcTemplate.query(
                "select date(created_at) stat_date, count(1) total from logistics_order " +
                        "where created_at >= timestampadd(day, ?, current_timestamp) group by date(created_at) order by stat_date",
                (rs, rowNum) -> new TrendPointVO(String.valueOf(rs.getObject("stat_date")), BigDecimal.valueOf(rs.getLong("total"))),
                -safeDays
        );
    }

    public List<TrendPointVO> incomeTrend(int months) {
        int safeMonths = Math.max(1, Math.min(months, 12));
        return jdbcTemplate.query(
                "select date_format(update_time, '%Y-%m') stat_month, coalesce(sum(actual_fee), 0) total from logistics_fee " +
                        "where payment_status = 'PAID' and update_time >= timestampadd(month, ?, current_timestamp) group by date_format(update_time, '%Y-%m') order by stat_month",
                (rs, rowNum) -> new TrendPointVO(rs.getString("stat_month"), rs.getBigDecimal("total")),
                -safeMonths
        );
    }
}
