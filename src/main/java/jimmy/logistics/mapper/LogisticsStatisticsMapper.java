package jimmy.logistics.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 统计分析数据访问 —— 运营看板图表数据（订单趋势、收入趋势）。
 */
@Mapper
public interface LogisticsStatisticsMapper {

    /** 查询最近 N 天每日订单数趋势，负偏移表示历史天数 */
    List<Map<String, Object>> selectOrderTrend(@Param("daysOffset") int daysOffset);

    /** 查询最近 N 个月每月收入趋势，负偏移表示历史月数 */
    List<Map<String, Object>> selectIncomeTrend(@Param("monthsOffset") int monthsOffset);
}
