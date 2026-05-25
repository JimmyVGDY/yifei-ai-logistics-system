package jimmy.logistics.model;

import java.math.BigDecimal;

public class TrendPointVO {

    private String name;
    private BigDecimal total;

    public TrendPointVO(String name, BigDecimal total) {
        this.name = name;
        this.total = total;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getTotal() {
        return total;
    }
}
