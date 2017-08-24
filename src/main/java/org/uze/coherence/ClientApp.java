package org.uze.coherence;

import com.tangosol.net.CacheFactory;
import com.tangosol.net.NamedCache;
import com.tangosol.util.ValueExtractor;
import com.tangosol.util.extractor.KeyExtractor;
import com.tangosol.util.filter.InFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.uze.coherence.model.Item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Yuriy Kiselev (uze@yandex.ru).
 */
public final class ClientApp {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final AtomicLong sequence = new AtomicLong(System.nanoTime());

    private final ValueExtractor extractor = new KeyExtractor();

    public static void main(String[] args) throws Exception {
        new ClientApp().run();
    }

    private void run() throws Exception {
        logger.info("Starting...");
        final NamedCache items = CacheFactory.getCache("Items");

        items.addIndex(extractor, false, null);

        logger.info("Clearing...");
        items.clear();

        logger.info("Generating...");
        final Map<Object, Item> map = generate(10_000);

        final List<String> keys = new ArrayList<>();
        map.values()
                .stream()
                .map(Item::id)
                .forEach(keys::add);

        // Read
        final ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            for (int k = 0; k < 50; k++) {
                logger.info("Iteration #{}", k);
                items.clear();

                logger.info("Filling...");
                items.putAll(map);

                logger.info("Reading...");

                final List<Future<Boolean>> futures = new ArrayList<>();
                for (String key : keys) {
                    futures.add(
                            pool.submit(() -> call(items, Collections.singleton(key)))
                    );
                }
                int success = 0;
                for (Future<Boolean> future : futures) {
                    if (future.get()) {
                        success++;
                    }
                }
                logger.info("Cache size: {}, {} of {} reads was successful", items.size(), success, futures.size());
            }
        } finally {
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }
        logger.info("Done!");
    }

    private Map<Object, Item> generate(int count) {
        final Map<Object, Item> items = new HashMap<>();
        for (int i = 0; i < count; i++) {
            items.put(
                    sequence.incrementAndGet(),
                    new Item(
                            "key#" + i,
                            "value#" + i
                    )
            );
        }
        return items;
    }

    private boolean call(NamedCache cache, Object key) throws Exception {
        final InFilter filter = new InFilter(extractor, Collections.singleton(key));
        final Set<Map.Entry<Object, Item>> set = cache.entrySet(filter);
        final List<Item> result = set.stream()
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
        boolean success = 1 == result.size();
        if (!success) {
            logger.info("Expected 1 got {}", result);
        }
        final List<Object> collect = set.stream()
                .map(Map.Entry::getKey)
                .map((Function<Object, Object>) cache::remove)
                .collect(Collectors.toList());
        success &= collect.size() != set.size();
        if (collect.size() != set.size()) {
            logger.info("Expected {}, got {}", set.size(), collect.size());
        }
        //cache.invokeAll(filter, new ConditionalRemove(AlwaysFilter.INSTANCE));
        return success;
    }

}
