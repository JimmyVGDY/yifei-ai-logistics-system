package jimmy.logistics.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.common.id.CompactSnowflakeIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
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

    private final JdbcTemplate jdbcTemplate;
    private final LogisticsRequirementService logisticsRequirementService;
    private final CompactSnowflakeIdGenerator idGenerator;
    private final Path uploadRoot;

    public LogisticsV2Service(JdbcTemplate jdbcTemplate,
                              LogisticsRequirementService logisticsRequirementService,
                              CompactSnowflakeIdGenerator idGenerator,
                              @Value("${app.upload.root-directory:uploads}") String uploadRoot) {
        this.jdbcTemplate = jdbcTemplate;
        this.logisticsRequirementService = logisticsRequirementService;
        this.idGenerator = idGenerator;
        this.uploadRoot = Paths.get(uploadRoot).toAbsolutePath().normalize();
    }

    public Map<String, Object> reportException(Map<String, Object> request) {
        String orderNo = requiredText(request, "orderNo");
        String exceptionType = requiredText(request, "exceptionType");
        String exceptionDesc = requiredText(request, "exceptionDesc");
        Long orderId = findOrderId(orderNo);
        Long taskId = findTaskId(orderId);
        String reportUser = currentUser();
        Long exceptionId = idGenerator.nextId();
        jdbcTemplate.update(
                "insert into logistics_exception (id, order_id, task_id, exception_type, exception_desc, exception_status, report_user, report_time, handle_user, handle_time) " +
                        "values (?, ?, ?, ?, ?, 'WAIT_HANDLE', ?, current_timestamp, null, null)",
                exceptionId, orderId, taskId, exceptionType, exceptionDesc, reportUser
        );
        jdbcTemplate.update("update logistics_order set status = 'EXCEPTION', updated_at = current_timestamp where id = ?", orderId);
        log.info("运输异常已上报，orderNo={}, exceptionType={}, reportUser={}", orderNo, exceptionType, reportUser);
        return record("exceptionId", exceptionId, "status", "WAIT_HANDLE");
    }

    public Map<String, Object> handleException(long exceptionId, Map<String, Object> request) {
        String status = text(request, "exceptionStatus", "CLOSED");
        String handleUser = currentUser();
        int updated = jdbcTemplate.update(
                "update logistics_exception set exception_status = ?, handle_user = ?, handle_time = current_timestamp where id = ?",
                status, handleUser, exceptionId
        );
        if (updated == 0) {
            throw new IllegalArgumentException("异常记录不存在");
        }
        log.info("运输异常已处理，exceptionId={}, status={}, handleUser={}", exceptionId, status, handleUser);
        return record("exceptionId", exceptionId, "status", status);
    }

    public Map<String, Object> generateFee(String orderNo) {
        Map<String, Object> order = jdbcTemplate.queryForMap(
                "select id, cargo_weight from logistics_order where order_no = ?",
                orderNo
        );
        Long orderId = ((Number) order.get("id")).longValue();
        BigDecimal cargoWeight = new BigDecimal(String.valueOf(order.get("cargo_weight")));
        BigDecimal baseFee = new BigDecimal("120.00");
        BigDecimal weightFee = cargoWeight.multiply(new BigDecimal("2.50")).setScale(2, BigDecimal.ROUND_HALF_UP);
        BigDecimal distanceFee = new BigDecimal("80.00");
        BigDecimal additionalFee = new BigDecimal("0.00");
        BigDecimal discountFee = new BigDecimal("0.00");
        BigDecimal payableFee = baseFee.add(weightFee).add(distanceFee).add(additionalFee).subtract(discountFee);

        Integer exists = jdbcTemplate.queryForObject("select count(1) from logistics_fee where order_id = ?", Integer.class, orderId);
        if (exists != null && exists > 0) {
            jdbcTemplate.update(
                    "update logistics_fee set base_fee = ?, weight_fee = ?, distance_fee = ?, additional_fee = ?, discount_fee = ?, payable_fee = ?, update_time = current_timestamp where order_id = ?",
                    baseFee, weightFee, distanceFee, additionalFee, discountFee, payableFee, orderId
            );
        } else {
            Long feeId = idGenerator.nextId();
            jdbcTemplate.update(
                    "insert into logistics_fee (id, order_id, base_fee, weight_fee, distance_fee, additional_fee, discount_fee, payable_fee, actual_fee, payment_status, create_time, update_time) " +
                            "values (?, ?, ?, ?, ?, ?, ?, ?, 0, 'UNPAID', current_timestamp, current_timestamp)",
                    feeId, orderId, baseFee, weightFee, distanceFee, additionalFee, discountFee, payableFee
            );
        }
        log.info("订单费用已生成，orderNo={}, payableFee={}", orderNo, payableFee);
        return record("orderNo", orderNo, "payableFee", payableFee, "paymentStatus", "UNPAID");
    }

    public Map<String, Object> markFeePaid(long feeId) {
        int updated = jdbcTemplate.update(
                "update logistics_fee set actual_fee = payable_fee, payment_status = 'PAID', update_time = current_timestamp where id = ?",
                feeId
        );
        if (updated == 0) {
            throw new IllegalArgumentException("费用记录不存在");
        }
        log.info("费用记录已标记付款，feeId={}", feeId);
        return record("feeId", feeId, "paymentStatus", "PAID");
    }

    public List<Map<String, Object>> orderTrend(int days) {
        int safeDays = Math.max(1, Math.min(days, 30));
        return jdbcTemplate.queryForList(
                "select date(created_at) stat_date, count(1) total from logistics_order " +
                        "where created_at >= timestampadd(day, ?, current_timestamp) group by date(created_at) order by stat_date",
                -safeDays
        );
    }

    public List<Map<String, Object>> incomeTrend(int months) {
        int safeMonths = Math.max(1, Math.min(months, 12));
        return jdbcTemplate.queryForList(
                "select date_format(update_time, '%Y-%m') stat_month, coalesce(sum(actual_fee), 0) total from logistics_fee " +
                        "where payment_status = 'PAID' and update_time >= timestampadd(month, ?, current_timestamp) group by date_format(update_time, '%Y-%m') order by stat_month",
                -safeMonths
        );
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
        jdbcTemplate.update(
                "insert into sys_uploaded_file (id, original_name, stored_name, relative_path, file_size, content_type, upload_user, upload_time) values (?, ?, ?, ?, ?, ?, ?, current_timestamp)",
                idGenerator.nextId(), originalName, storedName, relativePath, file.getSize(), file.getContentType(), currentUser()
        );
        log.info("文件上传完成，originalName={}, size={}", originalName, file.getSize());
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
            jdbcTemplate.update(
                    "insert into logistics_customer (id, customer_code, customer_name, contact_name, contact_phone, province, city, address, status, created_at, updated_at) " +
                            "values (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', current_timestamp, current_timestamp)",
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
        return record("imported", imported);
    }

    private Long findOrderId(String orderNo) {
        List<Long> ids = jdbcTemplate.queryForList("select id from logistics_order where order_no = ?", Long.class, orderNo);
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("订单不存在");
        }
        return ids.get(0);
    }

    private Long findTaskId(Long orderId) {
        List<Long> ids = jdbcTemplate.queryForList("select id from logistics_task where order_id = ? order by id desc limit 1", Long.class, orderId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private String requiredText(Map<String, Object> request, String key) {
        String value = text(request, key, null);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(key + "不能为空");
        }
        return value;
    }

    private String text(Map<String, Object> request, String key, String defaultValue) {
        Object value = request == null ? null : request.get(key);
        return value == null || !StringUtils.hasText(String.valueOf(value)) ? defaultValue : String.valueOf(value).trim();
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
