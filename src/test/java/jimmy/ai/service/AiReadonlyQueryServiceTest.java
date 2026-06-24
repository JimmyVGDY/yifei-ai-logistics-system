package jimmy.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.ai.model.AiGeneratedSqlQueryResult;
import jimmy.ai.model.AiQueryIntent;
import jimmy.ai.model.AiReadonlyQueryResult;
import jimmy.logistics.model.ModuleQueryDTO;
import jimmy.logistics.model.ModuleRecordVO;
import jimmy.logistics.service.LogisticsRequirementService;
import jimmy.common.model.PageResult;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.ArgumentCaptor;

class AiReadonlyQueryServiceTest {

    @Test
    void shouldReturnFriendlyMessageAndSkipQueryWhenPermissionDenied() {
        AiQueryIntentParser parser = mock(AiQueryIntentParser.class);
        AiGeneratedSqlQueryService sqlQueryService = mock(AiGeneratedSqlQueryService.class);
        LogisticsRequirementService requirementService = mock(LogisticsRequirementService.class);
        AiReadonlyQueryService service = new AiReadonlyQueryService(
                parser,
                sqlQueryService,
                requirementService,
                new AiQuerySummaryService(),
                new AiSensitiveDataMasker(),
                null,
                new AiToolCallContext(8),
                null,
                new jimmy.ai.service.PermissionEvaluator(),
                null
        );
        when(sqlQueryService.query(anyString())).thenReturn(AiGeneratedSqlQueryResult.skipped());
        when(parser.parse("查未收款费用")).thenReturn(new AiQueryIntent(
                "fees", "费用结算", "fee:query", null, null, null, false, false, true
        ));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.hasPermission("fee:query")).thenReturn(false);

            AiReadonlyQueryResult result = service.query("查未收款费用");

            assertThat(result.executed()).isTrue();
            assertThat(result.answerContext()).isEqualTo("当前账号权限不足，无法查询该类数据。如有需要，请联系系统管理员。");
            assertThat(result.answerContext()).doesNotContain("fee:query", "fees");
            assertThat(result.toolCalls()).hasSize(1);
            assertThat(result.toolCalls().getFirst().result()).doesNotContain("fee:query", "fees");
            verifyNoInteractions(requirementService);
        }
    }

    @Test
    void shouldRejectWriteRequestWithoutQueryingDatabase() {
        AiQueryIntentParser parser = mock(AiQueryIntentParser.class);
        AiGeneratedSqlQueryService sqlQueryService = mock(AiGeneratedSqlQueryService.class);
        LogisticsRequirementService requirementService = mock(LogisticsRequirementService.class);
        AiReadonlyQueryService service = new AiReadonlyQueryService(
                parser,
                sqlQueryService,
                requirementService,
                new AiQuerySummaryService(),
                new AiSensitiveDataMasker(),
                null,
                new AiToolCallContext(8),
                null,
                new jimmy.ai.service.PermissionEvaluator(),
                null
        );
        when(sqlQueryService.query(anyString())).thenReturn(AiGeneratedSqlQueryResult.skipped());
        when(parser.parse("删除订单")).thenReturn(AiQueryIntent.forbiddenWriteIntent());

        AiReadonlyQueryResult result = service.query("删除订单");

        assertThat(result.executed()).isTrue();
        assertThat(result.answerContext()).contains("仅支持只读查询");
        verifyNoInteractions(requirementService);
    }

    @Test
    void shouldInheritPreviousModuleWhenCurrentQuestionOnlyContainsFilter() {
        AiQueryIntentParser parser = new AiQueryIntentParser();
        AiGeneratedSqlQueryService sqlQueryService = mock(AiGeneratedSqlQueryService.class);
        LogisticsRequirementService requirementService = mock(LogisticsRequirementService.class);
        AiReadonlyQueryService service = new AiReadonlyQueryService(
                parser,
                sqlQueryService,
                requirementService,
                new AiQuerySummaryService(),
                new AiSensitiveDataMasker(),
                null,
                new AiToolCallContext(8),
                null,
                new jimmy.ai.service.PermissionEvaluator(),
                null
        );
        when(sqlQueryService.query(anyString())).thenReturn(AiGeneratedSqlQueryResult.skipped());
        when(requirementService.modulePage(anyString(), any(ModuleQueryDTO.class)))
                .thenReturn(new PageResult<>(List.of(new ModuleRecordVO(java.util.Map.of("异常状态", "待处理"))), 1, 10, 1));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.hasPermission("exception:query")).thenReturn(true);

            AiReadonlyQueryResult result = service.query("只要待处理的", "查一下异常管理");

            assertThat(result.executed()).isTrue();
            assertThat(result.toolCalls().getFirst().target()).isEqualTo("异常管理");
            assertThat(result.toolCalls().getFirst().result()).contains("待处理");
            verify(requirementService).modulePage(org.mockito.ArgumentMatchers.eq("exceptions"), any(ModuleQueryDTO.class));
        }
    }

    @Test
    void shouldFallbackToGlobalSearchWhenCustomerKeywordHasNoResult() {
        AiQueryIntentParser parser = new AiQueryIntentParser();
        AiGeneratedSqlQueryService sqlQueryService = mock(AiGeneratedSqlQueryService.class);
        LogisticsRequirementService requirementService = mock(LogisticsRequirementService.class);
        AiReadonlyQueryService service = new AiReadonlyQueryService(
                parser,
                sqlQueryService,
                requirementService,
                new AiQuerySummaryService(),
                new AiSensitiveDataMasker(),
                null,
                new AiToolCallContext(8),
                null,
                new jimmy.ai.service.PermissionEvaluator(),
                null
        );
        when(sqlQueryService.query(anyString())).thenReturn(AiGeneratedSqlQueryResult.skipped());
        when(requirementService.modulePage(anyString(), any(ModuleQueryDTO.class))).thenAnswer(invocation -> {
            String module = invocation.getArgument(0);
            if ("orders".equals(module)) {
                return new PageResult<>(List.of(new ModuleRecordVO(java.util.Map.of(
                        "order_no", "LO-TEST-001",
                        "customer_name", "陈菲"
                ))), 1, 5, 1);
            }
            return new PageResult<>(List.of(), 1, 5, 0);
        });

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.hasPermission(anyString())).thenReturn(true);

            AiReadonlyQueryResult result = service.query("陈菲");

            assertThat(result.executed()).isTrue();
            assertThat(result.answerContext()).contains("全场景模糊搜索", "运单管理", "陈菲");
            assertThat(result.toolCalls()).anySatisfy(toolCall -> {
                assertThat(toolCall.toolName()).isEqualTo("全场景模糊搜索");
                assertThat(toolCall.target()).isEqualTo("运单管理");
            });
            verify(requirementService).modulePage(eq("customers"), any(ModuleQueryDTO.class));
            verify(requirementService).modulePage(eq("orders"), any(ModuleQueryDTO.class));
        }
    }

    @Test
    void shouldReusePreviousKeywordWhenUserRequestsGlobalSearch() {
        AiQueryIntentParser parser = new AiQueryIntentParser();
        AiGeneratedSqlQueryService sqlQueryService = mock(AiGeneratedSqlQueryService.class);
        LogisticsRequirementService requirementService = mock(LogisticsRequirementService.class);
        AiReadonlyQueryService service = new AiReadonlyQueryService(
                parser,
                sqlQueryService,
                requirementService,
                new AiQuerySummaryService(),
                new AiSensitiveDataMasker(),
                null,
                new AiToolCallContext(8),
                null,
                new jimmy.ai.service.PermissionEvaluator(),
                null
        );
        when(sqlQueryService.query(anyString())).thenReturn(AiGeneratedSqlQueryResult.skipped());
        when(requirementService.modulePage(anyString(), any(ModuleQueryDTO.class))).thenAnswer(invocation -> {
            String module = invocation.getArgument(0);
            if ("orders".equals(module)) {
                return new PageResult<>(List.of(new ModuleRecordVO(java.util.Map.of(
                        "order_no", "LO-TEST-002",
                        "customer_name", "陈菲"
                ))), 1, 5, 1);
            }
            return new PageResult<>(List.of(), 1, 5, 0);
        });

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.hasPermission(anyString())).thenReturn(true);

            AiReadonlyQueryResult result = service.query("全局查找", "陈菲");

            assertThat(result.executed()).isTrue();
            assertThat(result.answerContext()).contains("陈菲", "运单管理");
            verify(requirementService).modulePage(eq("orders"), any(ModuleQueryDTO.class));
        }
    }

    @Test
    void shouldReusePreviousQueryWhenUserRequestsRemainingRecords() {
        LogisticsRequirementService requirementService = mock(LogisticsRequirementService.class);
        AiReadonlyQueryService service = serviceWithRealParser(requirementService);
        when(requirementService.modulePage(anyString(), any(ModuleQueryDTO.class))).thenReturn(
                new PageResult<>(List.of(new ModuleRecordVO(java.util.Map.of(
                        "order_no", "LO-TEST-REMAIN",
                        "customer_name", "陈土豆"
                ))), 1, 50, 48)
        );

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.hasPermission("order:query")).thenReturn(true);

            AiReadonlyQueryResult result = service.query("查看剩余的28条", "我要看今天的订单的详细数据");

            assertThat(result.executed()).isTrue();
            assertThat(result.answerContext())
                    .contains("运单管理", "共匹配 48 条记录", "结构化记录")
                    .doesNotContain("LO-TEST-REMAIN");
            assertThat(result.rows()).hasSize(1);
            assertThat(result.rows().getFirst()).containsEntry("订单号", "LO-TEST-REMAIN");
            verify(requirementService).modulePage(eq("orders"), any(ModuleQueryDTO.class));
        }
    }

    @Test
    void shouldContinueSpecifiedCursorInsteadOfGuessingLatestQuery() {
        AiGeneratedSqlQueryService sqlQueryService = mock(AiGeneratedSqlQueryService.class);
        LogisticsRequirementService requirementService = mock(LogisticsRequirementService.class);
        AiQueryCursorService cursorService = mock(AiQueryCursorService.class);
        AiReadonlyQueryService service = new AiReadonlyQueryService(
                new AiQueryIntentParser(),
                sqlQueryService,
                requirementService,
                new AiQuerySummaryService(),
                new AiSensitiveDataMasker(),
                cursorService,
                new AiToolCallContext(8),
                null,
                new jimmy.ai.service.PermissionEvaluator(),
                null
        );
        when(sqlQueryService.query(anyString())).thenReturn(AiGeneratedSqlQueryResult.skipped());

        jimmy.ai.model.AiQueryCursor selectedCursor = new jimmy.ai.model.AiQueryCursor();
        selectedCursor.setCursorId("cursor-orders");
        selectedCursor.setConversationId("conv-1");
        selectedCursor.setUserId("user-1");
        selectedCursor.setUserCode("U-1");
        selectedCursor.setToolName("业务数据查询");
        selectedCursor.setModuleCode("orders");
        selectedCursor.setKeyword("abnormal");
        selectedCursor.setPage(1);
        selectedCursor.setPageSize(10);
        selectedCursor.setTotal(25L);
        selectedCursor.setReturnedCount(10);
        when(cursorService.findActive("cursor-orders", "conv-1", "user-1", "U-1"))
                .thenReturn(Optional.of(selectedCursor));

        jimmy.ai.model.AiQueryCursor nextCursor = new jimmy.ai.model.AiQueryCursor();
        nextCursor.setCursorId("cursor-next");
        when(cursorService.create(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt(), anyLong(), anyInt(), any()))
                .thenReturn(Optional.of(nextCursor));
        when(requirementService.modulePage(eq("orders"), any(ModuleQueryDTO.class)))
                .thenReturn(new PageResult<>(List.of(new ModuleRecordVO(java.util.Map.of(
                        "order_no", "LO-CURSOR-011",
                        "customer_name", "测试客户"
                ))), 2, 10, 25));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.hasPermission("order:query")).thenReturn(true);

            AiReadonlyQueryResult result = service.queryCursor("cursor-orders", "conv-1", "user-1", "U-1");

            assertThat(result.executed()).isTrue();
            assertThat(result.rows()).hasSize(1);
            assertThat(result.cursorId()).isEqualTo("cursor-next");
            assertThat(result.returnedCount()).isEqualTo(11);
            assertThat(result.remainingCount()).isEqualTo(14);
            verify(cursorService).findActive("cursor-orders", "conv-1", "user-1", "U-1");
            verify(cursorService, org.mockito.Mockito.never()).latest(any(), any(), any());
            verify(requirementService).modulePage(eq("orders"), org.mockito.ArgumentMatchers.argThat(query ->
                    query.getPage() == 2
                            && query.getPageSize() == 10
                            && "abnormal".equals(query.getKeyword())));
        }
    }

    @Test
    void shouldSearchAcrossAllAllowedModulesWhenQuestionOnlyContainsShortKeyword() {
        AiReadonlyQueryService service = serviceWithRealParser(mockRequirementServiceForKeyword("陈土豆"));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.hasPermission(anyString())).thenReturn(true);

            AiReadonlyQueryResult result = service.query("陈土豆");

            assertThat(result.executed()).isTrue();
            assertThat(result.answerContext()).contains("全场景模糊搜索", "客户管理", "运单管理", "异常管理");
            assertThat(result.toolCalls()).anySatisfy(toolCall ->
                    assertThat(toolCall.toolName()).isEqualTo("全场景模糊搜索"));
        }
    }

    @Test
    void shouldJoinOrdersAndWaybillsWhenUserAsksAllWaybillsAndOrders() {
        LogisticsRequirementService requirementService = mock(LogisticsRequirementService.class);
        AiReadonlyQueryService service = serviceWithRealParser(requirementService);
        when(requirementService.modulePage(anyString(), any(ModuleQueryDTO.class))).thenAnswer(invocation -> {
            String module = invocation.getArgument(0);
            if ("orders".equals(module)) {
                return new PageResult<>(List.of(new ModuleRecordVO(java.util.Map.of("order_no", "LO-TEST-001"))), 1, 5, 1);
            }
            if ("waybills".equals(module)) {
                return new PageResult<>(List.of(new ModuleRecordVO(java.util.Map.of("waybill_no", "WB-TEST-001"))), 1, 5, 1);
            }
            return new PageResult<>(List.of(), 1, 5, 0);
        });

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.hasPermission(anyString())).thenReturn(true);

            AiReadonlyQueryResult result = service.query("给我查一下所有的运单和订单");

            assertThat(result.executed()).isTrue();
            assertThat(result.answerContext()).contains("业务联合查询", "运单管理", "运单中心");
        }
    }

    @Test
    void shouldJoinCustomerOrdersAndFeesWhenUserAsksCustomerBusinessOverview() {
        LogisticsRequirementService requirementService = mock(LogisticsRequirementService.class);
        AiReadonlyQueryService service = serviceWithRealParser(requirementService);
        when(requirementService.modulePage(anyString(), any(ModuleQueryDTO.class))).thenAnswer(invocation -> {
            String module = invocation.getArgument(0);
            if ("customers".equals(module)) {
                return new PageResult<>(List.of(new ModuleRecordVO(java.util.Map.of("customer_name", "陈土豆"))), 1, 5, 1);
            }
            if ("orders".equals(module)) {
                return new PageResult<>(List.of(new ModuleRecordVO(java.util.Map.of("order_no", "LO-TEST-002", "customer_name", "陈土豆"))), 1, 5, 1);
            }
            if ("fees".equals(module)) {
                return new PageResult<>(List.of(new ModuleRecordVO(java.util.Map.of("order_no", "LO-TEST-002", "payment_statusLabel", "未付款"))), 1, 5, 1);
            }
            return new PageResult<>(List.of(), 1, 5, 0);
        });

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.hasPermission(anyString())).thenReturn(true);

            AiReadonlyQueryResult result = service.query("查陈土豆的订单和费用");

            assertThat(result.executed()).isTrue();
            assertThat(result.answerContext()).contains("业务联合查询", "客户管理", "运单管理", "费用结算");
            verify(requirementService).modulePage(eq("customers"), any(ModuleQueryDTO.class));
            verify(requirementService).modulePage(eq("orders"), any(ModuleQueryDTO.class));
            verify(requirementService).modulePage(eq("fees"), any(ModuleQueryDTO.class));
        }
    }

    @Test
    void shouldSupportVehicleTaskTrackJoinedQuery() {
        LogisticsRequirementService requirementService = mock(LogisticsRequirementService.class);
        AiReadonlyQueryService service = serviceWithRealParser(requirementService);
        when(requirementService.modulePage(anyString(), any(ModuleQueryDTO.class))).thenAnswer(invocation -> {
            String module = invocation.getArgument(0);
            if ("vehicles".equals(module)) {
                return new PageResult<>(List.of(new ModuleRecordVO(java.util.Map.of("vehicle_no", "沪A12345"))), 1, 5, 1);
            }
            if ("tasks".equals(module)) {
                return new PageResult<>(List.of(new ModuleRecordVO(java.util.Map.of("task_no", "TASK-001", "vehicle_no", "沪A12345"))), 1, 5, 1);
            }
            if ("tracks".equals(module)) {
                return new PageResult<>(List.of(new ModuleRecordVO(java.util.Map.of("current_location", "天盈广场"))), 1, 5, 1);
            }
            return new PageResult<>(List.of(), 1, 5, 0);
        });

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.hasPermission(anyString())).thenReturn(true);

            AiReadonlyQueryResult result = service.query("查这辆车沪A12345今天的任务和轨迹");

            assertThat(result.executed()).isTrue();
            assertThat(result.answerContext()).contains("业务联合查询", "车辆管理", "运输任务", "物流轨迹");
            verify(requirementService).modulePage(eq("vehicles"), any(ModuleQueryDTO.class));
            verify(requirementService).modulePage(eq("tasks"), any(ModuleQueryDTO.class));
            verify(requirementService).modulePage(eq("tracks"), any(ModuleQueryDTO.class));
        }
    }

    @Test
    void shouldExpandChineseStatusKeywordWhenSearchingWithoutContext() {
        LogisticsRequirementService requirementService = mock(LogisticsRequirementService.class);
        AiReadonlyQueryService service = serviceWithRealParser(requirementService);
        when(requirementService.modulePage(anyString(), any(ModuleQueryDTO.class))).thenAnswer(invocation -> {
            ModuleQueryDTO query = invocation.getArgument(1);
            if ("WAIT_HANDLE".equals(query.getKeyword())) {
                return new PageResult<>(List.of(new ModuleRecordVO(java.util.Map.of("exception_statusLabel", "待处理"))), 1, 5, 1);
            }
            return new PageResult<>(List.of(), 1, 5, 0);
        });

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.hasPermission(anyString())).thenReturn(true);

            AiReadonlyQueryResult result = service.query("待处理");

            assertThat(result.executed()).isTrue();
            assertThat(result.answerContext()).contains("待处理");
        }
    }

    @Test
    void shouldNormalizeThisMonthFeesToFeesModuleWithMonthRangeAndEmptyKeyword() {
        LogisticsRequirementService requirementService = mock(LogisticsRequirementService.class);
        AiReadonlyQueryService service = serviceWithRealParser(requirementService);
        when(requirementService.modulePage(anyString(), any(ModuleQueryDTO.class)))
                .thenReturn(new PageResult<>(List.of(new ModuleRecordVO(java.util.Map.of("payable_fee", 100))), 1, 10, 1));
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        String expectedStart = today.withDayOfMonth(1) + " 00:00:00";
        String expectedEnd = today + " 23:59:59";

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.hasPermission("fee:query")).thenReturn(true);

            AiReadonlyQueryResult result = service.query("看一下这个月的费用");

            assertThat(result.executed()).isTrue();
            verify(requirementService).modulePage(eq("fees"), org.mockito.ArgumentMatchers.argThat(query ->
                    (query.getKeyword() == null || query.getKeyword().isBlank())
                            && expectedStart.equals(query.getStartTime())
                            && expectedEnd.equals(query.getEndTime())));
        }
    }

    @Test
    void shouldNormalizeTodayOrdersToOrdersModuleWithTodayRange() {
        LogisticsRequirementService requirementService = mock(LogisticsRequirementService.class);
        AiReadonlyQueryService service = serviceWithRealParser(requirementService);
        when(requirementService.modulePage(anyString(), any(ModuleQueryDTO.class)))
                .thenReturn(new PageResult<>(List.of(new ModuleRecordVO(java.util.Map.of("order_no", "LO-TODAY"))), 1, 10, 1));
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.hasPermission("order:query")).thenReturn(true);

            AiReadonlyQueryResult result = service.query("今天订单");

            assertThat(result.executed()).isTrue();
            verify(requirementService).modulePage(eq("orders"), org.mockito.ArgumentMatchers.argThat(query ->
                    (query.getKeyword() == null || query.getKeyword().isBlank())
                            && (today + " 00:00:00").equals(query.getStartTime())
                            && (today + " 23:59:59").equals(query.getEndTime())));
        }
    }

    @Test
    void shouldNormalizeRecentSevenDaysPendingExceptionsToStatusKeyword() {
        LogisticsRequirementService requirementService = mock(LogisticsRequirementService.class);
        AiReadonlyQueryService service = serviceWithRealParser(requirementService);
        when(requirementService.modulePage(anyString(), any(ModuleQueryDTO.class))).thenAnswer(invocation -> {
            ModuleQueryDTO query = invocation.getArgument(1);
            if ("待处理".equals(query.getKeyword())) {
                return new PageResult<>(List.of(new ModuleRecordVO(java.util.Map.of("exception_statusLabel", "待处理"))), 1, 10, 1);
            }
            return new PageResult<>(List.of(), 1, 10, 0);
        });
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.hasPermission("exception:query")).thenReturn(true);

            AiReadonlyQueryResult result = service.query("最近7天待处理异常");

            assertThat(result.executed()).isTrue();
            assertThat(result.answerContext()).contains("待处理");
            ArgumentCaptor<ModuleQueryDTO> queryCaptor = ArgumentCaptor.forClass(ModuleQueryDTO.class);
            verify(requirementService).modulePage(eq("exceptions"), queryCaptor.capture());
            ModuleQueryDTO query = queryCaptor.getValue();
            assertThat(query.getKeyword()).isEqualTo("待处理");
            assertThat(query.getStartTime()).isEqualTo(today.minusDays(6) + " 00:00:00");
            assertThat(query.getEndTime()).isEqualTo(today + " 23:59:59");
        }
    }

    @Test
    void broadExceptionQueryShouldStayInExceptionModuleAndUseRecentThirtyDays() {
        LogisticsRequirementService requirementService = mock(LogisticsRequirementService.class);
        AiReadonlyQueryService service = serviceWithRealParser(requirementService);
        when(requirementService.modulePage(anyString(), any(ModuleQueryDTO.class)))
                .thenReturn(new PageResult<>(List.of(), 1, 10, 0));
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.hasPermission("exception:query")).thenReturn(true);

            AiReadonlyQueryResult result = service.query("异常");

            assertThat(result.executed()).isTrue();
            ArgumentCaptor<ModuleQueryDTO> queryCaptor = ArgumentCaptor.forClass(ModuleQueryDTO.class);
            verify(requirementService).modulePage(eq("exceptions"), queryCaptor.capture());
            ModuleQueryDTO query = queryCaptor.getValue();
            assertThat(query.getKeyword()).isBlank();
            assertThat(query.getStartTime()).isEqualTo(today.minusDays(29) + " 00:00:00");
            assertThat(query.getEndTime()).isEqualTo(today + " 23:59:59");
            org.mockito.Mockito.verify(requirementService, org.mockito.Mockito.never()).modulePage(eq("orders"), any(ModuleQueryDTO.class));
            org.mockito.Mockito.verify(requirementService, org.mockito.Mockito.never()).modulePage(eq("fees"), any(ModuleQueryDTO.class));
        }
    }

    @Test
    void shouldSkipUnauthorizedModulesInJoinedQuery() {
        LogisticsRequirementService requirementService = mock(LogisticsRequirementService.class);
        AiReadonlyQueryService service = serviceWithRealParser(requirementService);
        when(requirementService.modulePage(anyString(), any(ModuleQueryDTO.class)))
                .thenReturn(new PageResult<>(List.of(new ModuleRecordVO(java.util.Map.of("order_no", "LO-TEST-003"))), 1, 5, 1));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.hasPermission(anyString())).thenAnswer(invocation -> false);
            stp.when(() -> StpUtil.hasPermission("order:query")).thenReturn(true);
            stp.when(() -> StpUtil.hasPermission("fee:query")).thenReturn(false);

            AiReadonlyQueryResult result = service.joinedSearch("order", "陈土豆", null, null);

            assertThat(result.executed()).isTrue();
            assertThat(result.answerContext()).contains("运单管理");
            assertThat(result.answerContext()).doesNotContain("fee:query", "费用结算");
            verify(requirementService).modulePage(eq("orders"), any(ModuleQueryDTO.class));
        }
    }

    private AiReadonlyQueryService serviceWithRealParser(LogisticsRequirementService requirementService) {
        AiGeneratedSqlQueryService sqlQueryService = mock(AiGeneratedSqlQueryService.class);
        when(sqlQueryService.query(anyString())).thenReturn(AiGeneratedSqlQueryResult.skipped());
        return new AiReadonlyQueryService(
                new AiQueryIntentParser(),
                sqlQueryService,
                requirementService,
                new AiQuerySummaryService(),
                new AiSensitiveDataMasker(),
                null,
                new AiToolCallContext(8),
                null,
                new jimmy.ai.service.PermissionEvaluator(),
                null
        );
    }

    private LogisticsRequirementService mockRequirementServiceForKeyword(String keyword) {
        LogisticsRequirementService requirementService = mock(LogisticsRequirementService.class);
        when(requirementService.modulePage(anyString(), any(ModuleQueryDTO.class))).thenAnswer(invocation -> {
            String module = invocation.getArgument(0);
            ModuleQueryDTO query = invocation.getArgument(1);
            if (!keyword.equals(query.getKeyword())) {
                return new PageResult<>(List.of(), 1, 5, 0);
            }
            if ("customers".equals(module)) {
                return new PageResult<>(List.of(new ModuleRecordVO(java.util.Map.of("customer_name", keyword))), 1, 5, 1);
            }
            if ("orders".equals(module)) {
                return new PageResult<>(List.of(new ModuleRecordVO(java.util.Map.of("order_no", "LO-TEST-004", "customer_name", keyword))), 1, 5, 1);
            }
            if ("exceptions".equals(module)) {
                return new PageResult<>(List.of(new ModuleRecordVO(java.util.Map.of("exception_desc", keyword + "相关异常"))), 1, 5, 1);
            }
            return new PageResult<>(List.of(), 1, 5, 0);
        });
        return requirementService;
    }
}
