package io.nexacache.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.nexacache.domain.EntityMeta;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缓存区域，对应一个 {@link io.nexacache.annotation.NexaEntity} 的完整缓存空间。
 *
 * <p>采用双层结构：
 * <ul>
 *   <li><b>指针层（Pointer Layer）</b>：{@link ConcurrentHashMap} 维护 {@code cacheKey -> 实体引用}，
 *       常驻内存，O(1) 定位，无 GC 压力（弱引用由数据层管理）。</li>
 *   <li><b>数据层（Data Layer）</b>：{@link Caffeine} 管理实体对象本身，支持 LRU 淘汰与 TTL 过期。
 *       当数据层条目被淘汰时，指针层对应条目同步失效。</li>
 * </ul>
 *
 * @param <T>  实体类型
 * @param <ID> 主键类型
 * @author azir
 * @since 1.0.0
 */
@Slf4j
public final class CacheRegion<T, ID> {

    @Getter
    private final EntityMeta<T> meta;

    /** 指针层：cacheKey -> 实体引用（与数据层保持同步） */
    private final ConcurrentHashMap<String, Object> pointerLayer = new ConcurrentHashMap<>();

    /** 数据层：cacheKey -> 实体对象（Caffeine 管理生命周期） */
    private final Cache<String, T> dataLayer;

    public CacheRegion(EntityMeta<T> meta) {
        this.meta = meta;

        Caffeine<Object, Object> builder = Caffeine.newBuilder()
                .maximumSize(meta.getMaxSize())
                // 当数据层条目被驱逐时，同步清理指针层
                .removalListener((key, value, cause) -> {
                    if (key != null) {
                        pointerLayer.remove(key.toString());
                        log.debug("[NexaCache] 区域[{}] 缓存驱逐: key={}, 原因={}", meta.getRegion(), key, cause);
                    }
                });

        if (meta.getTtl() > 0) {
            builder.expireAfterWrite(meta.getTtl(), meta.getTimeUnit());
        }

        this.dataLayer = builder.build();
    }

    /**
     * 将实体写入缓存（同时更新指针层和数据层）。
     *
     * @param entity 实体对象（主键不能为 null）
     */
    @SuppressWarnings("unchecked")
    public void put(T entity) {
        Object id = meta.extractId(entity);
        if (id == null) {
            log.warn("[NexaCache] 区域[{}] 写入缓存失败：主键为 null", meta.getRegion());
            return;
        }
        String key = meta.buildCacheKey(id);
        dataLayer.put(key, entity);
        pointerLayer.put(key, id);
        log.debug("[NexaCache] 区域[{}] 写入缓存: key={}", meta.getRegion(), key);
    }

    /**
     * 根据主键从缓存中读取实体。
     *
     * @param id 主键值
     * @return 实体 Optional 包装
     */
    public Optional<T> get(ID id) {
        String key = meta.buildCacheKey(id);
        // 先查指针层，确认指针存在再查数据层（避免无效的 Caffeine 查询）
        if (!pointerLayer.containsKey(key)) {
            return Optional.empty();
        }
        T entity = dataLayer.getIfPresent(key);
        if (entity == null) {
            // 数据层已过期，清理孤立指针
            pointerLayer.remove(key);
            return Optional.empty();
        }
        return Optional.of(entity);
    }

    /**
     * 根据主键驱逐缓存条目（指针层 + 数据层同步清除）。
     *
     * @param id 主键值
     */
    public void evict(ID id) {
        String key = meta.buildCacheKey(id);
        dataLayer.invalidate(key);
        pointerLayer.remove(key);
        log.debug("[NexaCache] 区域[{}] 手动驱逐: key={}", meta.getRegion(), key);
    }

    /**
     * 清空整个缓存区域。
     */
    public void clear() {
        dataLayer.invalidateAll();
        pointerLayer.clear();
        log.info("[NexaCache] 区域[{}] 已全部清空", meta.getRegion());
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
