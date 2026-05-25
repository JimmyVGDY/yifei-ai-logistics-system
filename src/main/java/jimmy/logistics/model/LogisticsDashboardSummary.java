package jimmy.logistics.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class LogisticsDashboardSummary {

    private long todayOrders;
    private long completedOrders;
    private long waitDispatchOrders;
    private long inTransitOrders;
    private long exceptionOrders;
    private BigDecimal monthIncome;
    private List<Map<String, Object>> statusDistribution;
    private List<Map<String, Object>> recentExceptions;

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
}
