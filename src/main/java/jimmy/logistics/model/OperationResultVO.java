package jimmy.logistics.model;

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
