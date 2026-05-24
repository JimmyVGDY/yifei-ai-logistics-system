package jimmy.service;

import com.google.common.hash.BloomFilter;
import org.springframework.stereotype.Service;

@Service
public class BloomFilterService {

    private final BloomFilter<CharSequence> stringBloomFilter;

    public BloomFilterService(BloomFilter<CharSequence> stringBloomFilter) {
        this.stringBloomFilter = stringBloomFilter;
    }

    public boolean put(String value) {
        return stringBloomFilter.put(value);
    }

    public boolean mightContain(String value) {
        return stringBloomFilter.mightContain(value);
    }
}
