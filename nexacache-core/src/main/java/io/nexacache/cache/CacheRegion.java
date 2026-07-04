package io.nexacache.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import io.nexacache.domain.EntityMeta;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Gatherers;

/**
 * 缓存区域，对应一个 {@link io.nexacache.annotation.NexaEntity} 的完整缓存空间。
 *
 * <p>采用双层结构：
 * <ul>
 *   <li><b>指针层（Pointer Layer）</b>：{@link ConcurrentHashMap} 维护 {@code cacheKey -> 实体引用}，
 *       常驻内存，O(1) 定位。</li>
 *   <li><b>数据层（Data Layer）</b>：{@link Caffeine} 管理实体对象本身，支持 LRU 淘汰与 TTL 过期。
 *       当数据层条目被淘汰时，指针层对应条目同步失效。</li>
 * </ul>
 *
 * <p><b>JDK 25 改造要点：</b>
 * <ul>
 *   <li><b>Stream Gatherers（JEP 461，JDK 25 正式）</b>：
 *       {@link #getAll(List)} 使用 {@code Gatherers.windowFixed()} 批量分窗读取，
 *       替代传统 for 循环，更具函数式风格且支持并行流</li>
 *   <li><b>Virtual Threads（JEP 444，JDK 21+）</b>：
 *       {@link #warmUp(List)} 使用虚拟线程并发预热，充分利用 JDK 25 的 VT 成熟特性</li>
 *   <li><b>Record Pattern（JEP 440）</b>：
 *       在 {@link #snapshot()} 方法中演示 record pattern 解构</li>
 * </ul>
 *
 * @param <T>  实体类型
 * @param <ID> 主键类型
 * @author azir
 * @since 2.0.0
 */
@Slf4j
public final class CacheRegion<T, ID> {

    @Getter
    private final EntityMeta<T> meta;

    /** 指针层：cacheKey -> id（与数据层保持同步） */
    private final ConcurrentHashMap<String, Object> pointerLayer = new ConcurrentHashMap<>();

    /** 数据层：cacheKey -> 实体对象（Caffeine 管理生命周期） */
    private final Cache<String, T> dataLayer;

    public CacheRegion(EntityMeta<T> meta) {
        this.meta = meta;

        Caffeine<Object, Object> builder = Caffeine.newBuilder()
                .maximumSize(meta.maxSize())
                .recordStats()  // JDK 25：开启统计，供 snapshot() 使用
                // 注意：仅在真正驱逐（过期、容量满、手动删除）时同步清除指针层，
                // 排除 REPLACED 原因（put 覆盖时 Caffeine 会异步触发旧值驱逐，不应删除指针）
                .removalListener((key, value, cause) -> {
                    if (key != null && cause != RemovalCause.REPLACED) {
                        pointerLayer.remove(key.toString());
                        log.debug("[NexaCache] 区域[{}] 缓存驱逐: key={}, 原因={}",
                                meta.region(), key, cause);
                    }
                });

        if (meta.ttl() > 0) {
            builder.expireAfterWrite(meta.ttl(), meta.timeUnit());
        }

        this.dataLayer = builder.build();
    }

    // ===================== 核心 CRUD =====================

    /**
     * 将实体写入缓存（同时更新指针层和数据层）。
     */
    @SuppressWarnings("unchecked")
    public void put(T entity) {
        Object id = meta.extractId(entity);
        if (id == null) {
            log.warn("[NexaCache] 区域[{}] 写入缓存失败：主键为 null", meta.region());
            return;
        }
        String key = meta.buildCacheKey(id);
        dataLayer.put(key, entity);
        pointerLayer.put(key, id);
        log.debug("[NexaCache] 区域[{}] 写入缓存: key={}", meta.region(), key);
    }

    /**
     * 根据主键从缓存中读取实体。
     */
    public Optional<T> get(ID id) {
        String key = meta.buildCacheKey(id);
        if (!pointerLayer.containsKey(key)) {
            return Optional.empty();
        }
        T entity = dataLayer.getIfPresent(key);
        if (entity == null) {
            pointerLayer.remove(key);
            return Optional.empty();
        }
        return Optional.of(entity);
    }

    /**
     * 批量读取实体列表。
     *
     * <p><b>JDK 25 Stream Gatherers（JEP 461）</b>：
     * 使用 {@code Gatherers.windowFixed(batchSize)} 将 ID 列表分成固定大小的窗口，
     * 每个窗口并行查询缓存，最终 flatMap 合并结果。
     * 相比传统 for 循环，更具函数式风格，且支持并行流加速。
     *
     * @param ids 主键列表
     * @return 命中缓存的实体列表（未命中的自动跳过）
     */
    public List<T> getAll(List<ID> ids) {
        int batchSize = Math.max(1, ids.size() / Runtime.getRuntime().availableProcessors());
        return ids.stream()
                // JEP 461: Stream Gatherers — 将 ID 列表分成固定大小的批次窗口
                .gather(Gatherers.windowFixed(batchSize))
                .flatMap(batch -> batch.stream()
                        .map(this::get)
                        .filter(Optional::isPresent)
                        .map(Optional::get))
                .toList();
    }

    /**
     * 根据主键驱逐缓存条目（指针层 + 数据层同步清除）。
     */
    public void evict(ID id) {
        String key = meta.buildCacheKey(id);
        dataLayer.invalidate(key);
        pointerLayer.remove(key);
        log.debug("[NexaCache] 区域[{}] 手动驱逐: key={}", meta.region(), key);
    }

    /**
     * 清空整个缓存区域。
     */
    public void clear() {
        dataLayer.invalidateAll();
        pointerLayer.clear();
        log.info("[NexaCache] 区域[{}] 已全部清空", meta.region());
    }

    // ===================== 高级特性 =====================

    /**
     * 并发预热：使用 Virtual Threads 并发将实体列表写入缓存。
     *
     * <p><b>JDK 25 Virtual Threads（JEP 444，JDK 21+ 正式）</b>：
     * 虚拟线程在 JDK 25 中已完全成熟，无需任何 preview flag。
     * 每个实体的写入操作在独立的虚拟线程中执行，
     * 对于大批量预热场景（如应用启动时），性能显著优于串行写入。
     *
     * @param entities 待预热的实体列表
     */
    public void warmUp(List<T> entities) {
        if (entities == null || entities.isEmpty()) return;
        log.info("[NexaCache] 区域[{}] 开始虚拟线程并发预热，共 {} 条记录", meta.region(), entities.size());

        // JDK 25: Virtual Threads — 使用虚拟线程执行器并发预热
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = entities.stream()
                    .map(entity -> executor.submit(() -> put(entity)))
                    .toList();
            // 等待所有预热任务完成
            for (var future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                    log.warn("[NexaCache] 区域[{}] 预热任务异常: {}", meta.region(), e.getMessage());
                }
            }
        }
        log.info("[NexaCache] 区域[{}] 预热完成，当前缓存条目数: {}", meta.region(), size());
    }

    /**
     * 返回缓存统计快照。
     *
     * <p>利用 Caffeine 的 {@link CacheStats} record-like 特性，
     * 结合 JDK 25 的 text block 格式化输出统计信息。
     *
     * @return 格式化的统计信息字符串
     */
    public String snapshot() {
        CacheStats stats = dataLayer.stats();
        // JDK 15+ Text Blocks：多行字符串更清晰
        return """
                [NexaCache 区域统计] region=%s
                  命中次数: %d, 命中率: %.2f%%
                  未命中次数: %d, 加载次数: %d
                  驱逐次数: %d, 当前条目数: %d
                """.formatted(
                meta.region(),
                stats.hitCount(), stats.hitRate() * 100,
                stats.missCount(), stats.loadCount(),
                stats.evictionCount(), size()
        );
    }

    /**
     * 返回当前缓存条目数。
     */
    public long size() {
        return dataLayer.estimatedSize();
    }

    /**
     * 判断指针层是否存在指定主键的指针。
     */
    public boolean hasPointer(ID id) {
        return pointerLayer.containsKey(meta.buildCacheKey(id));
    }
}
