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
 * <p><b>JDK 25 改造要点：</b>
 * <ul>
 *   <li><b>Pattern Matching for instanceof（JDK 16+，JEP 394）</b>：
 *       {@link #getRegion(String)} 中用 pattern matching 替代传统 null 检查 + 强转</li>
 *   <li><b>Sealed Class 异常（JEP 409）</b>：
 *       抛出精确的 {@link NexaCacheException.EntityNotRegisteredException} 子类型</li>
 *   <li><b>Text Blocks（JDK 15+）</b>：日志格式化使用 text block</li>
 * </ul>
 *
 * @author azir
 * @since 2.0.0
 */
@Slf4j
public class CacheRegistry {

    /** region 名称 -> CacheRegion 映射 */
    private final ConcurrentHashMap<String, CacheRegion<?, ?>> regionMap = new ConcurrentHashMap<>();

    /**
     * 注册一个实体类的缓存区域。
     * 若该区域已存在则跳过（幂等操作）。
     *
     * @param entityClass 标注了 @NexaEntity 的实体类
     */
    public <T> void register(Class<T> entityClass) {
        EntityMeta<T> meta = EntityMeta.of(entityClass);
        regionMap.computeIfAbsent(meta.region(), k -> {
            // JDK 15+ Text Blocks：多行日志格式更清晰
            log.info("""
                    [NexaCache] 注册缓存区域:
                      region   = {}
                      maxSize  = {}
                      ttl      = {}
                    """,
                    meta.region(),
                    meta.maxSize(),
                    meta.ttl() > 0 ? meta.ttl() + " " + meta.timeUnit() : "永不过期");
            return new CacheRegion<>(meta);
        });
    }

    /**
     * 根据区域名称获取 CacheRegion。
     *
     * <p><b>JDK 25 Pattern Matching for instanceof（JEP 394）</b>：
     * 使用 {@code instanceof} 模式匹配替代传统 null 检查 + 强转，代码更简洁安全。
     *
     * @param region 区域名称
     * @return CacheRegion 实例
     * @throws NexaCacheException.EntityNotRegisteredException 若区域未注册
     */
    @SuppressWarnings("unchecked")
    public <T, ID> CacheRegion<T, ID> getRegion(String region) {
        // JEP 394: Pattern Matching for instanceof — 直接解构并命名变量
        if (regionMap.get(region) instanceof CacheRegion<?, ?> cacheRegion) {
            return (CacheRegion<T, ID>) cacheRegion;
        }
        // Sealed Class 异常：抛出精确子类型，调用方可用 switch 完整处理
        throw NexaCacheException.notRegistered(region);
    }

    /**
     * 根据实体类获取 CacheRegion。
     */
    public <T, ID> CacheRegion<T, ID> getRegion(Class<T> entityClass) {
        EntityMeta<T> meta = EntityMeta.of(entityClass);
        return getRegion(meta.region());
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

    /**
     * 输出所有区域的统计快照（用于监控和调试）。
     */
    public String allSnapshots() {
        var sb = new StringBuilder();
        regionMap.values().forEach(region -> sb.append(region.snapshot()));
        return sb.toString();
    }
}
