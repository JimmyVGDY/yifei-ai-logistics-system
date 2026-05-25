package jimmy.model;

import java.util.List;

public class PageResult<T> {

    private List<T> records;
    private int page;
    private int pageSize;
    private long total;

    public PageResult(List<T> records, int page, int pageSize, long total) {
        this.records = records;
        this.page = page;
        this.pageSize = pageSize;
        this.total = total;
    }

    public List<T> getRecords() {
        return records;
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public long getTotal() {
        return total;
    }
}
