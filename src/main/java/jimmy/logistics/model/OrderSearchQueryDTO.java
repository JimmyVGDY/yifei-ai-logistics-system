package jimmy.logistics.model;

/**
 * ES 订单搜索查询参数 —— 分页 + 关键词模糊搜索。
 */
public class OrderSearchQueryDTO {

    private int page = 1;
    private int pageSize = 20;
    private String keyword;

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
}
