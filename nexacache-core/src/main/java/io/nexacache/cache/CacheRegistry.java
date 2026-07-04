package io.nexacache.cache;

import io.nexacache.annotation.NexaEntity;
import io.nexacache.domain.EntityMeta;
import io.nexacache.exception.NexaCacheException;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局缓存注册表，负责管理所有 {@link CacheRegion} 的生命周期。
 * 采用单例模式，由 Spring 容器管理，在应用启动时扫描并注册所有 {@link NexaEntity} 实体。
 *
 * @author azir
 * @since 1.0.0
 */
@Slf4j
public class CacheRegistry {

    /** region名称 -> CacheRegion 映射 */
    private final ConcurrentHashMap<String, CacheRegion<?, ?>> regionMap = new ConcurrentHashMap<>();

    /**
     * 注册一个实体类的缓存区域。
     * 若该区域已存在则跳过（幂等操作）。
     *
     * @param entityClass 标注了 @NexaEntity 的实体类
     */
    public <T> void register(Class<T> entityClass) {
        EntityMeta<T> meta = EntityMeta.of(entityClass);
        regionMap.computeIfAbsent(meta.getRegion(), k -> {
            log.info("[NexaCache] 注册缓存区域: region={}, maxSize={}, ttl={}{}",
                    meta.getRegion(), meta.getMaxSize(),
                    meta.getTtl() > 0 ? meta.getTtl() : "永不过期",
                    meta.getTtl() > 0 ? " " + meta.getTimeUnit() : "");
            return new CacheRegion<>(meta);
        });
    }

    /**
     * 根据区域名称获取 CacheRegion。
     *
     * @param region 区域名称
     * @return CacheRegion 实例
     * @throws NexaCacheException 若区域未注册
     */
    @SuppressWarnings("unchecked")
    public <T, ID> CacheRegion<T, ID> getRegion(String region) {
        CacheRegion<?, ?> cacheRegion = regionMap.get(region);
        if (cacheRegion == null) {
            throw new NexaCacheException("缓存区域 [" + region + "] 未注册，请确保实体类标注了 @NexaEntity");
        }
        return (CacheRegion<T, ID>) cacheRegion;
    }

    /**
     * 根据实体类获取 CacheRegion。
     */
    public <T, ID> CacheRegion<T, ID> getRegion(Class<T> entityClass) {
        EntityMeta<T> meta = EntityMeta.of(entityClass);
        return getRegion(meta.getRegion());
    }

    /**
     * 返回所有已注册的区域名称。
     */
    public Collection<String> registeredRegions() {
        return Collections.unmodifiableSet(regionMap.keySet());
    }

    /**
     * 清空所有缓存区域。
     */
    public void clearAll() {
        regionMap.values().forEach(CacheRegion::clear);
        log.info("[NexaCache] 所有缓存区域已清空，共 {} 个", regionMap.size());
    }
}
