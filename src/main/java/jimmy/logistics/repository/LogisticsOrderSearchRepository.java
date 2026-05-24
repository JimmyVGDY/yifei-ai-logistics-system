package jimmy.logistics.repository;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface LogisticsOrderSearchRepository extends ElasticsearchRepository<LogisticsOrderSearchDocument, String> {

    List<LogisticsOrderSearchDocument> findByCustomerNameContaining(String customerName);
}
