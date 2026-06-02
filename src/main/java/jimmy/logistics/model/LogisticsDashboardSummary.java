package jimmy.logistics.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 运营看板汇总对象 —— 包含今日/历史订单数、收入、状态分布、合同预警等全部看板指标。
 */
public class LogisticsDashboardSummary {

    private long todayOrders;
    private long completedOrders;
    private long waitDispatchOrders;
    private long inTransitOrders;
    private long exceptionOrders;
    private BigDecimal monthIncome;
    private List<Map<String, Object>> statusDistribution;
    private List<Map<String, Object>> recentExceptions;
    /** 30 天内到期合同 */
    private List<Map<String, Object>> expiringContracts;
    /** 上月收入汇总 */
    private BigDecimal lastMonthIncome;
    /** 上月订单数 */
    private long lastMonthOrders;
    /** 上月异常数 */
    private long lastMonthExceptions;

    public long getTodayOrders() {
        return todayOrders;
    }

    public void setTodayOrders(long todayOrders) {
        this.todayOrders = todayOrders;
    }

    public long getCompletedOrders() {
        return completedOrders;
    }

    public void setCompletedOrders(long completedOrders) {
        this.completedOrders = completedOrders;
    }

    public long getWaitDispatchOrders() {
        return waitDispatchOrders;
    }

    public void setWaitDispatchOrders(long waitDispatchOrders) {
        this.waitDispatchOrders = waitDispatchOrders;
    }

    public long getInTransitOrders() {
        return inTransitOrders;
    }

    public void setInTransitOrders(long inTransitOrders) {
        this.inTransitOrders = inTransitOrders;
    }

    public long getExceptionOrders() {
        return exceptionOrders;
    }

    public void setExceptionOrders(long exceptionOrders) {
        this.exceptionOrders = exceptionOrders;
    }

    public BigDecimal getMonthIncome() {
        return monthIncome;
    }

    public void setMonthIncome(BigDecimal monthIncome) {
        this.monthIncome = monthIncome;
    }

    public List<Map<String, Object>> getStatusDistribution() {
        return statusDistribution;
    }

    public void setStatusDistribution(List<Map<String, Object>> statusDistribution) {
        this.statusDistribution = statusDistribution;
    }

    public List<Map<String, Object>> getRecentExceptions() {
        return recentExceptions;
    }

    public void setRecentExceptions(List<Map<String, Object>> recentExceptions) {
        this.recentExceptions = recentExceptions;
    }

    public List<Map<String, Object>> getExpiringContracts() { return expiringContracts; }
    public void setExpiringContracts(List<Map<String, Object>> val) { this.expiringContracts = val; }
    public BigDecimal getLastMonthIncome() { return lastMonthIncome; }
    public void setLastMonthIncome(BigDecimal val) { this.lastMonthIncome = val; }
    public long getLastMonthOrders() { return lastMonthOrders; }
    public void setLastMonthOrders(long val) { this.lastMonthOrders = val; }
    public long getLastMonthExceptions() { return lastMonthExceptions; }
    public void setLastMonthExceptions(long val) { this.lastMonthExceptions = val; }
}
