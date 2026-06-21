package jimmy.ai.entity;

import java.time.LocalDateTime;

/**
 * AI Prompt 模板实体。
 * <p>
 * Prompt 在系统里属于可版本管理的业务资产，数据库模板用于运行期调整，
 * 代码默认模板用于数据库未迁移或模板缺失时兜底，避免影响现有 AI 功能。
 */
public class AiPromptTemplate {

    private Long id;
    private String templateCode;
    private String templateName;
    private Integer templateVersion;
    private String templateType;
    private String templateContent;
    private String requiredVariables;
    private String optionalVariables;
    private String outputSchema;
    private String modelPurpose;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }

    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }

    public Integer getTemplateVersion() { return templateVersion; }
    public void setTemplateVersion(Integer templateVersion) { this.templateVersion = templateVersion; }

    public String getTemplateType() { return templateType; }
    public void setTemplateType(String templateType) { this.templateType = templateType; }

    public String getTemplateContent() { return templateContent; }
    public void setTemplateContent(String templateContent) { this.templateContent = templateContent; }

    public String getRequiredVariables() { return requiredVariables; }
    public void setRequiredVariables(String requiredVariables) { this.requiredVariables = requiredVariables; }

    public String getOptionalVariables() { return optionalVariables; }
    public void setOptionalVariables(String optionalVariables) { this.optionalVariables = optionalVariables; }

    public String getOutputSchema() { return outputSchema; }
    public void setOutputSchema(String outputSchema) { this.outputSchema = outputSchema; }

    public String getModelPurpose() { return modelPurpose; }
    public void setModelPurpose(String modelPurpose) { this.modelPurpose = modelPurpose; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
