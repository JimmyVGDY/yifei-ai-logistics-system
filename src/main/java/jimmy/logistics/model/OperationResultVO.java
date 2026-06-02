package jimmy.logistics.model;

/**
 * 通用 CRUD 操作结果 —— 包含操作 ID、是否删除、新状态。
 */
public class OperationResultVO {

    private Long id;
    private Boolean deleted;
    private String status;

    public OperationResultVO(Long id) {
        this.id = id;
    }

    public static OperationResultVO deleted(Long id) {
        OperationResultVO result = new OperationResultVO(id);
        result.setDeleted(Boolean.TRUE);
        return result;
    }

    public Long getId() {
        return id;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
