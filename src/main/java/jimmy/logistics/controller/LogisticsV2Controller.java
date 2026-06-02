package jimmy.logistics.controller;

import jimmy.logistics.annotation.OperationLog;
import jimmy.logistics.model.CreateCustomerAccountRequest;
import jimmy.logistics.model.ExceptionHandleDTO;
import jimmy.logistics.model.ExceptionReportDTO;
import jimmy.logistics.model.ModuleMutationDTO;
import jimmy.logistics.model.OperationResultVO;
import jimmy.logistics.model.SimpleResultVO;
import jimmy.logistics.model.TrendPointVO;
import jimmy.logistics.service.CustomerAccountService;
import jimmy.logistics.service.LogisticsCrudService;
import jimmy.logistics.service.LogisticsExceptionService;
import jimmy.logistics.service.LogisticsFeeService;
import jimmy.logistics.service.LogisticsStatisticsService;
import jimmy.logistics.service.LogisticsV2Service;
import jimmy.model.ApiResponse;
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
import javax.validation.Valid;

/**
 * 物流业务统一控制器 —— 客户账号、通用 CRUD、异常/费用、统计、文件上传、Excel 导入导出。
 * <p>
 * 所有写操作均标注 {@link OperationLog}，由拦截器自动记入审计日志。
 */
@RestController
@RequestMapping("/logistics")
public class LogisticsV2Controller {

    private final LogisticsV2Service logisticsV2Service;
    private final LogisticsCrudService logisticsCrudService;
    private final CustomerAccountService customerAccountService;
    private final LogisticsExceptionService logisticsExceptionService;
    private final LogisticsFeeService logisticsFeeService;
    private final LogisticsStatisticsService logisticsStatisticsService;

    public LogisticsV2Controller(LogisticsV2Service logisticsV2Service,
                                 LogisticsCrudService logisticsCrudService,
                                 CustomerAccountService customerAccountService,
                                 LogisticsExceptionService logisticsExceptionService,
                                 LogisticsFeeService logisticsFeeService,
                                 LogisticsStatisticsService logisticsStatisticsService) {
        this.logisticsV2Service = logisticsV2Service;
        this.logisticsCrudService = logisticsCrudService;
        this.customerAccountService = customerAccountService;
        this.logisticsExceptionService = logisticsExceptionService;
        this.logisticsFeeService = logisticsFeeService;
        this.logisticsStatisticsService = logisticsStatisticsService;
    }

    @OperationLog("创建客户账号")
    @PostMapping("/customer-accounts")
    public ApiResponse<OperationResultVO> createCustomerAccount(@Valid @RequestBody CreateCustomerAccountRequest request) {
        return ApiResponse.success(customerAccountService.createCustomerAccount(request));
    }

    @OperationLog("新增管理模块记录")
    @PostMapping("/modules/{module}")
    public ApiResponse<OperationResultVO> createModuleRecord(@PathVariable String module,
                                                             @RequestBody ModuleMutationDTO payload) {
        return ApiResponse.success(logisticsCrudService.create(module, payload.getValues()));
    }

    @OperationLog("修改管理模块记录")
    @PostMapping("/modules/{module}/{id}")
    public ApiResponse<OperationResultVO> updateModuleRecord(@PathVariable String module,
                                                             @PathVariable long id,
                                                             @RequestBody ModuleMutationDTO payload) {
        return ApiResponse.success(logisticsCrudService.update(module, id, payload.getValues()));
    }

    @OperationLog("删除管理模块记录")
    @PostMapping("/modules/{module}/{id}/delete")
    public ApiResponse<OperationResultVO> deleteModuleRecord(@PathVariable String module,
                                                             @PathVariable long id) {
        return ApiResponse.success(logisticsCrudService.delete(module, id));
    }

    @OperationLog("上报运输异常")
    @PostMapping("/exceptions/report")
    public ApiResponse<SimpleResultVO> reportException(@Valid @RequestBody ExceptionReportDTO request) {
        return ApiResponse.success(logisticsExceptionService.reportException(request));
    }

    @OperationLog("处理运输异常")
    @PostMapping("/exceptions/{exceptionId}/handle")
    public ApiResponse<SimpleResultVO> handleException(@PathVariable long exceptionId,
                                                       @RequestBody ExceptionHandleDTO request) {
        return ApiResponse.success(logisticsExceptionService.handleException(exceptionId, request));
    }

    @OperationLog("生成订单费用")
    @PostMapping("/fees/generate/{orderNo}")
    public ApiResponse<SimpleResultVO> generateFee(@PathVariable String orderNo) {
        return ApiResponse.success(logisticsFeeService.generateFee(orderNo));
    }

    @OperationLog("标记费用已付款")
    @PostMapping("/fees/{feeId}/pay")
    public ApiResponse<SimpleResultVO> markFeePaid(@PathVariable long feeId) {
        return ApiResponse.success(logisticsFeeService.markFeePaid(feeId));
    }

    @OperationLog("运营看板-查询订单趋势")
    @GetMapping("/statistics/order-trend")
    public ApiResponse<List<TrendPointVO>> orderTrend(@RequestParam(defaultValue = "7") int days) {
        return ApiResponse.success(logisticsStatisticsService.orderTrend(days));
    }

    @OperationLog("运营看板-查询收入趋势")
    @GetMapping("/statistics/income-trend")
    public ApiResponse<List<TrendPointVO>> incomeTrend(@RequestParam(defaultValue = "6") int months) {
        return ApiResponse.success(logisticsStatisticsService.incomeTrend(months));
    }

    @OperationLog("上传业务文件")
    @PostMapping(value = "/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SimpleResultVO> uploadFile(@RequestPart("file") MultipartFile file) throws IOException {
        return ApiResponse.success(SimpleResultVO.from(logisticsV2Service.uploadFile(file)));
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
    public ApiResponse<SimpleResultVO> importCustomers(@RequestPart("file") MultipartFile file) throws IOException {
        return ApiResponse.success(SimpleResultVO.from(logisticsV2Service.importCustomers(file)));
    }
}
