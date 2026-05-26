package jimmy.logistics.controller;

import jimmy.logistics.model.StructuredLogQueryDTO;
import jimmy.logistics.service.StructuredLogQueryService;
import jimmy.model.ApiResponse;
import jimmy.model.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/system/structured-logs")
public class StructuredLogController {

    private final StructuredLogQueryService structuredLogQueryService;

    public StructuredLogController(StructuredLogQueryService structuredLogQueryService) {
        this.structuredLogQueryService = structuredLogQueryService;
    }

    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> page(StructuredLogQueryDTO query) {
        return ApiResponse.success(structuredLogQueryService.query(query));
    }
}
