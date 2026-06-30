package jimmy.logistics.service;

import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.common.util.FieldEncryptor;
import jimmy.logistics.mapper.LogisticsCrudMapper;
import jimmy.logistics.model.CrudFieldValue;
import jimmy.logistics.util.ColumnExistenceChecker;
import jimmy.logistics.util.CrudBusinessUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LogisticsCrudServiceTest {

    @Test
    void shouldPassExpectedVersionWhenUpdatingVersionedRecord() {
        LogisticsCrudMapper mapper = mock(LogisticsCrudMapper.class);
        ColumnExistenceChecker columnChecker = mock(ColumnExistenceChecker.class);
        CrudBusinessUtils utils = new CrudBusinessUtils(mapper, mock(CompactSnowflakeIdGenerator.class));
        ChangeAuditService changeAudit = mock(ChangeAuditService.class);
        CrudConfigRegistry registry = mock(CrudConfigRegistry.class);
        UserAccountService userAccountService = mock(UserAccountService.class);
        CrudConfig config = new CrudConfig("logistics_order", "created_at", "updated_at", "status");

        when(registry.requireConfig("orders")).thenReturn(config);
        when(columnChecker.hasColumn(eq("logistics_order"), any())).thenReturn(true);
        when(mapper.selectRecordById("logistics_order", 100L)).thenReturn(Map.of("id", 100L, "version", 7));
        when(mapper.updateRecord(eq("logistics_order"), eq(100L), anyList(), eq(true), eq(7))).thenReturn(1);

        LogisticsCrudService service = new LogisticsCrudService(
                mapper,
                changeAudit,
                columnChecker,
                utils,
                new FieldEncryptor(false, ""),
                mock(CompactSnowflakeIdGenerator.class),
                registry,
                userAccountService
        );

        service.update("orders", 100L, Map.of("status", "WAIT_DISPATCH"));

        verify(mapper).updateRecord(eq("logistics_order"), eq(100L), anyList(), eq(true), eq(7));
    }
}
