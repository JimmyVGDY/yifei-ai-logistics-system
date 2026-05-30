package jimmy.logistics.service;

import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.logistics.entity.LogisticsOrder;
import jimmy.logistics.mapper.LogisticsTrackMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LogisticsTrackInitializeService {

    private static final String ORDER_CREATED_DESC = "订单创建后自动初始化轨迹";
    /** 订单创建时运单尚未生成，waybillId 暂存为 0，后续调度时补充。 */
    private static final long INITIAL_WAYBILL_ID = 0L;

    private final LogisticsTrackMapper logisticsTrackMapper;
    private final CompactSnowflakeIdGenerator idGenerator;

    public LogisticsTrackInitializeService(LogisticsTrackMapper logisticsTrackMapper,
                                           CompactSnowflakeIdGenerator idGenerator) {
        this.logisticsTrackMapper = logisticsTrackMapper;
        this.idGenerator = idGenerator;
    }

    public void initializeCreatedTrack(LogisticsOrder order) {
        if (logisticsTrackMapper.countByOrderIdAndOperationDesc(order.getId(), ORDER_CREATED_DESC) > 0) {
            return;
        }
        logisticsTrackMapper.insertTrack(
                idGenerator.nextId(),
                order.getId(),
                INITIAL_WAYBILL_ID,
                order.getStatus(),
                "订单中心",
                "系统",
                ORDER_CREATED_DESC
        );
        log.info("订单创建轨迹已初始化，orderNo={}", order.getOrderNo());
    }
}
