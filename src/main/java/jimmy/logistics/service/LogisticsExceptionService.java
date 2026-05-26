package jimmy.logistics.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.logistics.mapper.LogisticsExceptionMapper;
import jimmy.logistics.model.ExceptionHandleDTO;
import jimmy.logistics.model.ExceptionReportDTO;
import jimmy.logistics.model.SimpleResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class LogisticsExceptionService {

    private static final List<String> HANDLE_TARGET_STATUSES = Arrays.asList("PROCESSING", "CLOSED");

    private final LogisticsExceptionMapper logisticsExceptionMapper;
    private final CompactSnowflakeIdGenerator idGenerator;

    public LogisticsExceptionService(LogisticsExceptionMapper logisticsExceptionMapper,
                                     CompactSnowflakeIdGenerator idGenerator) {
        this.logisticsExceptionMapper = logisticsExceptionMapper;
        this.idGenerator = idGenerator;
    }

    public SimpleResultVO reportException(ExceptionReportDTO request) {
        Long orderId = findOrderId(request.getOrderNo());
        Long taskId = findTaskId(orderId);
        String reportUser = currentUser();
        Long exceptionId = idGenerator.nextId();
        logisticsExceptionMapper.insertException(exceptionId, orderId, taskId, request.getExceptionType(), request.getExceptionDesc(), reportUser);
        logisticsExceptionMapper.updateOrderStatusException(orderId);
        log.info("运输异常已上报，orderNo={}, exceptionType={}, reportUser={}",
                request.getOrderNo(), request.getExceptionType(), reportUser);
        return new SimpleResultVO().add("exceptionId", exceptionId).add("status", "WAIT_HANDLE");
    }

    public SimpleResultVO handleException(long exceptionId, ExceptionHandleDTO request) {
        String status = request == null || !StringUtils.hasText(request.getExceptionStatus())
                ? "CLOSED"
                : request.getExceptionStatus().trim().toUpperCase();
        if (!HANDLE_TARGET_STATUSES.contains(status)) {
            throw new IllegalArgumentException("异常处理状态不合法");
        }
        String currentStatus = findExceptionStatus(exceptionId);
        validateStatusFlow(currentStatus, status);
        String handleUser = currentUser();
        int updated = logisticsExceptionMapper.updateExceptionStatus(exceptionId, status, handleUser);
        if (updated == 0) {
            throw new IllegalArgumentException("异常记录不存在");
        }
        log.info("运输异常已处理，exceptionId={}, status={}, handleUser={}", exceptionId, status, handleUser);
        return new SimpleResultVO().add("exceptionId", exceptionId).add("status", status);
    }

    private String findExceptionStatus(long exceptionId) {
        String status = logisticsExceptionMapper.findExceptionStatus(exceptionId);
        if (status == null) {
            throw new IllegalArgumentException("异常记录不存在");
        }
        return status;
    }

    private void validateStatusFlow(String currentStatus, String targetStatus) {
        if ("CLOSED".equals(currentStatus)) {
            throw new IllegalArgumentException("异常已处理，不能重复操作");
        }
        if ("PROCESSING".equals(targetStatus) && !"WAIT_HANDLE".equals(currentStatus)) {
            throw new IllegalArgumentException("只有待处理异常才能开始处理");
        }
        if ("CLOSED".equals(targetStatus) && !Arrays.asList("WAIT_HANDLE", "PROCESSING").contains(currentStatus)) {
            throw new IllegalArgumentException("当前异常状态不能标记为已处理");
        }
    }

    private Long findOrderId(String orderNo) {
        Long id = logisticsExceptionMapper.findOrderIdByOrderNo(orderNo);
        if (id == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        return id;
    }

    private Long findTaskId(Long orderId) {
        return logisticsExceptionMapper.findLatestTaskIdByOrderId(orderId);
    }

    private String currentUser() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        return loginId == null ? "admin" : String.valueOf(loginId);
    }
}
