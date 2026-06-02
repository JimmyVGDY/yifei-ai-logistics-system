package jimmy.model;

import java.util.List;

/**
 * 分页结果封装 —— 包含当前页记录列表 + 分页元信息。
 */
public class PageResult<T> {

    /** 当前页记录列表 */
    private List<T> records;
    /** 当前页码（从1开始） */
    private int page;
    /** 每页条数 */
    private int pageSize;
    /** 总记录数 */
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
