package jimmy.logistics.model;

/**
 * 通用 CRUD 操作结果 —— 包含操作 ID、是否删除、新状态。
 */
public record OperationResultVO(
        /** 操作结果ID */
        Long id,
        /** 是否软删除 */
        Boolean deleted,
        /** 操作后状态 */
        String status) {

    public OperationResultVO(Long id) {
        this(id, null, null);
    }

    public static OperationResultVO deleted(Long id) {
        return new OperationResultVO(id, Boolean.TRUE, null);
    }

    public Long getId() {
        return id;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public String getStatus() {
        return status;
    }
}
