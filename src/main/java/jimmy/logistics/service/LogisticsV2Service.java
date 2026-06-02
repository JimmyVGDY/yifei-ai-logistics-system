package jimmy.logistics.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.logistics.mapper.LogisticsCustomerImportMapper;
import jimmy.logistics.mapper.LogisticsFileMapper;
import jimmy.logistics.config.OperationChangeContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class LogisticsV2Service {

    private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DataFormatter DATA_FORMATTER = new DataFormatter();

    private final LogisticsFileMapper logisticsFileMapper;
    private final LogisticsCustomerImportMapper logisticsCustomerImportMapper;
    private final LogisticsRequirementService logisticsRequirementService;
    private final CompactSnowflakeIdGenerator idGenerator;
    private final Path uploadRoot;

    public LogisticsV2Service(LogisticsFileMapper logisticsFileMapper,
                              LogisticsCustomerImportMapper logisticsCustomerImportMapper,
                              LogisticsRequirementService logisticsRequirementService,
                              CompactSnowflakeIdGenerator idGenerator,
                              @Value("${app.upload.root-directory:uploads}") String uploadRoot) {
        this.logisticsFileMapper = logisticsFileMapper;
        this.logisticsCustomerImportMapper = logisticsCustomerImportMapper;
        this.logisticsRequirementService = logisticsRequirementService;
        this.idGenerator = idGenerator;
        this.uploadRoot = Paths.get(uploadRoot).toAbsolutePath().normalize();
    }

    public Map<String, Object> uploadFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择需要上传的文件");
        }
        Files.createDirectories(uploadRoot);
        String originalName = file.getOriginalFilename() == null ? "unknown" : Paths.get(file.getOriginalFilename()).getFileName().toString();
        String suffix = "";
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex >= 0) {
            suffix = originalName.substring(dotIndex);
        }
        String storedName = FILE_TIME_FORMATTER.format(LocalDateTime.now()) + "-" + UUID.randomUUID().toString().replace("-", "") + suffix;
        Path targetPath = uploadRoot.resolve(storedName).normalize();
        if (!targetPath.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("文件路径不合法");
        }
        file.transferTo(targetPath.toFile());
        String relativePath = uploadRoot.getFileName() + "/" + storedName;
        logisticsFileMapper.insertUploadedFile(idGenerator.nextId(), originalName, storedName, relativePath, file.getSize(), file.getContentType(), currentUser());
        log.info("文件上传完成，originalName={}, size={}", originalName, file.getSize());
        OperationChangeContext.setChangeSummary("文件=" + originalName + ", 大小=" + file.getSize() + "B");
        return record("originalName", originalName, "relativePath", relativePath, "fileSize", file.getSize());
    }

    public byte[] exportModule(String module, int limit) throws IOException {
        List<Map<String, Object>> records = logisticsRequirementService.moduleRecords(module, limit);
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet(module);
        if (!records.isEmpty()) {
            Row header = sheet.createRow(0);
            int columnIndex = 0;
            for (String key : records.get(0).keySet()) {
                header.createCell(columnIndex++).setCellValue(key);
            }
            int rowIndex = 1;
            for (Map<String, Object> record : records) {
                Row row = sheet.createRow(rowIndex++);
                columnIndex = 0;
                for (Object value : record.values()) {
                    Cell cell = row.createCell(columnIndex++);
                    cell.setCellValue(value == null ? "" : String.valueOf(value));
                }
            }
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();
        log.info("模块数据已导出 Excel，module={}, recordSize={}", module, records.size());
        return outputStream.toByteArray();
    }

    public Map<String, Object> importCustomers(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择客户 Excel 文件");
        }
        Workbook workbook = new XSSFWorkbook(file.getInputStream());
        Sheet sheet = workbook.getSheetAt(0);
        int imported = 0;
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || !StringUtils.hasText(cell(row, 0))) {
                continue;
            }
            String customerCode = "CUST-IMP-" + FILE_TIME_FORMATTER.format(LocalDateTime.now()) + "-" + rowIndex;
            logisticsCustomerImportMapper.insertImportedCustomer(
                    idGenerator.nextId(),
                    customerCode,
                    cell(row, 0),
                    text(cell(row, 1), "待维护"),
                    text(cell(row, 2), "13800000000"),
                    text(cell(row, 3), "待维护"),
                    text(cell(row, 4), "待维护"),
                    text(cell(row, 5), "待维护")
            );
            imported++;
        }
        workbook.close();
        log.info("客户 Excel 导入完成，imported={}", imported);
        OperationChangeContext.setChangeSummary("导入客户数=" + imported);
        return record("imported", imported);
    }

    private String text(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private String cell(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) {
            return "";
        }
        return DATA_FORMATTER.formatCellValue(cell).trim();
    }

    private String currentUser() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        return loginId == null ? "admin" : String.valueOf(loginId);
    }

    private Map<String, Object> record(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            result.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return result;
    }
}
