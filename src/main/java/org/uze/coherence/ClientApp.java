package org.uze.coherence;

import com.google.common.base.Stopwatch;
import com.tangosol.net.CacheFactory;
import com.tangosol.net.NamedCache;
import com.tangosol.util.ValueExtractor;
import com.tangosol.util.extractor.KeyExtractor;
import com.tangosol.util.filter.AlwaysFilter;
import com.tangosol.util.filter.InFilter;
import com.tangosol.util.processor.ConditionalRemove;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.uze.coherence.model.Item;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
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
        items.clear();
        items.addIndex(extractor, false, null);

        final Stopwatch timer = Stopwatch.createStarted();
        long total = 0, totalSuccess = 0;
        int iterations = 0;
        final ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            while (!Thread.currentThread().isInterrupted()) {
                final Set<?> keys = fillCache(items, 1500);
                final int success = readAll(items, keys, pool);
                total += keys.size();
                totalSuccess += success;
                Thread.sleep(20);
                if (timer.elapsed(TimeUnit.SECONDS) > 5) {
                    logger.info("{} of {} was successful, {}%, cache size: {}", totalSuccess, total, 100.0 * totalSuccess / total, items.size());
                    iterations++;
                    timer.reset().start();
                }
                if (iterations > 50){
                    break;
                }
            }
        } finally {
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }
        logger.info("Done!");
    }

    private Set<?> fillCache(NamedCache items, int count) {
        final Map<Long, Item> map = new HashMap<>(count);
        for (int i = 0; i < count; i++) {
            final long id = sequence.incrementAndGet();
            map.put(
                    id,
                    new Item(
                            "id#" + id,
                            "value#" + id
                    )
            );
        }
        items.putAll(map);
        return map.keySet();
    }

    private int readAll(NamedCache items, Set<?> keys, ExecutorService pool) {
        final List<CompletableFuture<Boolean>> futures = keys
                .stream()
                .map(key -> CompletableFuture.supplyAsync(() -> call(items, key), pool))
                .collect(Collectors.toList());
        return futures.stream()
                .map(CompletableFuture::join)
                .mapToInt(v -> v ? 1 : 0)
                .sum();
    }

    private boolean call(NamedCache cache, Object key) {
        //final Object value = cache.get(key);
        //boolean success = value != null;
        final InFilter filter = new InFilter(extractor, Collections.singleton(key));
        final Set<Map.Entry<Object, Item>> set = cache.entrySet(filter);
        final List<Item> result = set.stream()
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
        boolean success = 1 == result.size();
        if (!success) {
            logger.info("Expected 1 got 0");
        }
//        Object v2 = cache.remove(key);
//        if (v2 == null) {
//            logger.info("Failed to remove key!");
//        }
//        success &= v2 != null;
        cache.invokeAll(filter, new ConditionalRemove(AlwaysFilter.INSTANCE));
        return success;
    }

}
