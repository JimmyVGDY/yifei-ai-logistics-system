package jimmy.model;

import java.util.List;

/**
 * 分页结果封装 —— 包含当前页记录列表 + 分页元信息。
 */
public record PageResult<T>(
        /** 当前页记录列表 */
        List<T> records,
        /** 当前页码（从1开始） */
        int page,
        /** 每页条数 */
        int pageSize,
        /** 总记录数 */
        long total) {

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
