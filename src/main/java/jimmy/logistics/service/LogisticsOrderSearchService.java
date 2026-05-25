package jimmy.logistics.service;

import jimmy.logistics.entity.LogisticsOrder;
import jimmy.logistics.model.LogisticsOrderVO;
import jimmy.logistics.model.OrderSearchQueryDTO;
import jimmy.logistics.repository.LogisticsOrderSearchDocument;
import jimmy.model.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
        NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
                .withQuery(StringUtils.hasText(keyword)
                        ? QueryBuilders.multiMatchQuery(keyword, "orderNo", "customerName", "receiverAddress", "cargoName")
                        : QueryBuilders.matchAllQuery())
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
        order.setCustomerName(document.getCustomerName());
        order.setReceiverAddress(document.getReceiverAddress());
        order.setCargoName(document.getCargoName());
        order.setCargoWeight(document.getCargoWeight());
        order.setStatus(document.getStatus());
        return LogisticsOrderVO.from(order);
    }
}
