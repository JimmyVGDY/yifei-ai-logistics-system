package jimmy.logistics.controller;

import jimmy.logistics.annotation.OperationLog;
import jimmy.logistics.service.LogisticsV2Service;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/logistics")
public class LogisticsV2Controller {

    private final LogisticsV2Service logisticsV2Service;

    public LogisticsV2Controller(LogisticsV2Service logisticsV2Service) {
        this.logisticsV2Service = logisticsV2Service;
    }

    @OperationLog("上报运输异常")
    @PostMapping("/exceptions/report")
    public Map<String, Object> reportException(@RequestBody Map<String, Object> request) {
        return logisticsV2Service.reportException(request);
    }

    @OperationLog("处理运输异常")
    @PostMapping("/exceptions/{exceptionId}/handle")
    public Map<String, Object> handleException(@PathVariable long exceptionId,
                                               @RequestBody Map<String, Object> request) {
        return logisticsV2Service.handleException(exceptionId, request);
    }

    @OperationLog("生成订单费用")
    @PostMapping("/fees/generate/{orderNo}")
    public Map<String, Object> generateFee(@PathVariable String orderNo) {
        return logisticsV2Service.generateFee(orderNo);
    }

    @OperationLog("标记费用已付款")
    @PostMapping("/fees/{feeId}/pay")
    public Map<String, Object> markFeePaid(@PathVariable long feeId) {
        return logisticsV2Service.markFeePaid(feeId);
    }

    @GetMapping("/statistics/order-trend")
    public List<Map<String, Object>> orderTrend(@RequestParam(defaultValue = "7") int days) {
        return logisticsV2Service.orderTrend(days);
    }

    @GetMapping("/statistics/income-trend")
    public List<Map<String, Object>> incomeTrend(@RequestParam(defaultValue = "6") int months) {
        return logisticsV2Service.incomeTrend(months);
    }

    @OperationLog("上传业务文件")
    @PostMapping(value = "/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadFile(@RequestPart("file") MultipartFile file) throws IOException {
        return logisticsV2Service.uploadFile(file);
    }

    @OperationLog("导出模块 Excel")
    @GetMapping("/excel/export/{module}")
    public ResponseEntity<ByteArrayResource> exportModule(@PathVariable String module,
                                                          @RequestParam(defaultValue = "100") int limit) throws IOException {
        byte[] bytes = logisticsV2Service.exportModule(module, limit);
        String fileName = module + "-export.xlsx";
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }

    @OperationLog("导入客户 Excel")
    @PostMapping(value = "/excel/import/customers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> importCustomers(@RequestPart("file") MultipartFile file) throws IOException {
        return logisticsV2Service.importCustomers(file);
    }
}
