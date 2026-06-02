package jimmy.logistics.model;

/**
 * 模块列表查询参数 —— 分页 + 关键词 + 时间范围筛选。
 */
public class ModuleQueryDTO {

    /** 页码（从1开始） */
    private int page = 1;
    /** 每页条数 */
    private int pageSize = 20;
    /** 关键词搜索 */
    private String keyword;
    /** 开始时间（yyyy-MM-dd HH:mm:ss） */
    private String startTime;
    /** 结束时间（yyyy-MM-dd HH:mm:ss） */
    private String endTime;
    /** 使用场景筛选 */
    private String usage;

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getUsage() {
        return usage;
    }

    public void setUsage(String usage) {
        this.usage = usage;
    }
}
