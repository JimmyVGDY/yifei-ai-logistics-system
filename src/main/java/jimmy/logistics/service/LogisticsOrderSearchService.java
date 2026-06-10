package jimmy.logistics.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.logistics.entity.LogisticsOrder;
import jimmy.logistics.model.LogisticsOrderVO;
import jimmy.logistics.model.OrderSearchQueryDTO;
import jimmy.logistics.repository.LogisticsOrderSearchDocument;
import jimmy.common.model.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 物流订单 ES 搜索服务 —— 订单信息写入 Elasticsearch 并支持多字段全文检索。
 * <p>
 * 索引失败不会阻断主流程（ES 作为辅助搜索渠道，数据库仍为主数据源）。
 */
@Slf4j
@Service
public class LogisticsOrderSearchService {

    private final ElasticsearchOperations elasticsearchOperations;

    public LogisticsOrderSearchService(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    public void saveSearchDocument(LogisticsOrder logisticsOrder) {
        LogisticsOrderSearchDocument document = new LogisticsOrderSearchDocument();
        document.setOrderNo(logisticsOrder.getOrderNo());
        document.setCustomerId(logisticsOrder.getCustomerId());
        document.setStatus(logisticsOrder.getStatus());
        document.setCustomerName(logisticsOrder.getCustomerName());
        document.setReceiverAddress(logisticsOrder.getReceiverAddress());
        document.setCargoName(logisticsOrder.getCargoName());
        document.setCargoWeight(logisticsOrder.getCargoWeight());
        try {
            elasticsearchOperations.save(document);
            log.info("物流订单写入 Elasticsearch 索引，orderNo={}", logisticsOrder.getOrderNo());
        } catch (RuntimeException ignored) {
            log.warn("物流订单写入 Elasticsearch 失败，已忽略，orderNo={}, reason={}",
                    logisticsOrder.getOrderNo(), ignored.getMessage());
        }
    }

    public PageResult<LogisticsOrderVO> search(OrderSearchQueryDTO query) {
        int page = Math.max(1, query == null ? 1 : query.getPage());
        int pageSize = Math.max(1, Math.min(query == null ? 20 : query.getPageSize(), 100));
        String keyword = query == null ? null : query.getKeyword();
        Long customerId = currentCustomerScopeOrNull();
        NativeQuery searchQuery = NativeQuery.builder()
                .withQuery(root -> root.bool(bool -> {
                    if (StringUtils.hasText(keyword)) {
                        bool.must(must -> must.multiMatch(multiMatch -> multiMatch
                                .query(keyword)
                                .fields("orderNo", "customerName", "receiverAddress", "cargoName")));
                    } else {
                        bool.must(must -> must.matchAll(matchAll -> matchAll));
                    }
                    if (customerId != null) {
                        bool.filter(filter -> filter.term(term -> term.field("customerId").value(customerId)));
                    }
                    return bool;
                }))
                .withPageable(PageRequest.of(page - 1, pageSize))
                .build();
        try {
            SearchHits<LogisticsOrderSearchDocument> hits = elasticsearchOperations.search(searchQuery, LogisticsOrderSearchDocument.class);
            List<LogisticsOrderVO> records = new ArrayList<>();
            for (SearchHit<LogisticsOrderSearchDocument> hit : hits) {
                records.add(toVo(hit.getContent()));
            }
            return new PageResult<>(records, page, pageSize, hits.getTotalHits());
        } catch (RuntimeException exception) {
            log.warn("Elasticsearch 订单搜索失败，keyword={}, reason={}", keyword, exception.getMessage());
            return new PageResult<>(Collections.emptyList(), page, pageSize, 0);
        }
    }

    private LogisticsOrderVO toVo(LogisticsOrderSearchDocument document) {
        LogisticsOrder order = new LogisticsOrder();
        order.setOrderNo(document.getOrderNo());
        order.setCustomerId(document.getCustomerId());
        order.setCustomerName(document.getCustomerName());
        order.setReceiverAddress(document.getReceiverAddress());
        order.setCargoName(document.getCargoName());
        order.setCargoWeight(document.getCargoWeight());
        order.setStatus(document.getStatus());
        return LogisticsOrderVO.from(order);
    }

    private Long currentCustomerScopeOrNull() {
        try {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            if (loginId == null) {
                return null;
            }
            Object roleCode = StpUtil.getSessionByLoginId(loginId).get("roleCode");
            if (!"CUSTOMER".equals(String.valueOf(roleCode))) {
                return null;
            }
            Object customerId = StpUtil.getSessionByLoginId(loginId).get("customerId");
            if (customerId instanceof Number number) {
                return number.longValue();
            }
            if (customerId != null && StringUtils.hasText(String.valueOf(customerId))) {
                return Long.valueOf(String.valueOf(customerId));
            }
            throw new IllegalStateException("客户账号未绑定客户档案，禁止搜索业务数据");
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("客户搜索权限校验失败", ex);
        }
    }
}
