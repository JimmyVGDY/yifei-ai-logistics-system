package jimmy.logistics.service;

import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.logistics.entity.LogisticsOrder;
import jimmy.logistics.mapper.LogisticsOrderMapper;
import jimmy.logistics.model.CreateLogisticsOrderRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LogisticsOrderServiceTest {

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void shouldRunCacheSearchAndMqOnlyAfterCommit() {
        LogisticsOrderMapper mapper = mock(LogisticsOrderMapper.class);
        CompactSnowflakeIdGenerator idGenerator = mock(CompactSnowflakeIdGenerator.class);
        LogisticsOrderCacheService cacheService = mock(LogisticsOrderCacheService.class);
        LogisticsOrderSearchService searchService = mock(LogisticsOrderSearchService.class);
        LogisticsOrderMessageService messageService = mock(LogisticsOrderMessageService.class);
        when(idGenerator.nextId()).thenReturn(260630120000000L, 260630120000001L);

        LogisticsOrderService service = new LogisticsOrderService(
                mapper, idGenerator, cacheService, searchService, messageService);
        CreateLogisticsOrderRequest request = new CreateLogisticsOrderRequest();
        request.setCustomerName("测试客户");
        request.setSenderAddress("上海");
        request.setReceiverAddress("北京");
        request.setCargoName("设备");

        TransactionSynchronizationManager.initSynchronization();
        service.create(request);

        verify(mapper).insert(any(LogisticsOrder.class));
        verify(cacheService, never()).rememberOrderNo(any());
        verify(searchService, never()).saveSearchDocument(any());
        verify(messageService, never()).publishOrderCreated(any());

        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        verify(cacheService).rememberOrderNo(any());
        verify(cacheService).cacheOrder(any());
        verify(searchService).saveSearchDocument(any());
        verify(messageService).publishOrderCreated(any());
    }
}
