package io.nexacache.api;

import io.nexacache.cache.CacheRegion;
import io.nexacache.cache.CacheRegistry;
import io.nexacache.domain.EntityMeta;
import io.nexacache.exception.NexaCacheException;
import io.nexacache.recordset.RecordSetSession;
import io.nexacache.spi.DataAccessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NexaCache 核心门面，提供两套编程式缓存操作 API：
 *
 * <ul>
 *   <li><b>简洁 API</b>（{@link #opsForEntity(Class)}）：适合常规 CRUD 场景，
 *       一行代码完成缓存感知的增删改查。</li>
 *   <li><b>记录集高级 API</b>（{@link #opsForRecordSet(Class)}）：适合需要游标导航、
 *       逐条遍历或精细控制缓存生命周期的场景，提供 START/OPEN/READ/NEXT/PREV/
 *       REWRITE/DELETE/CLOSE 等数据库游标风格操作。</li>
 * </ul>
 *
 * <p><b>JDK 25 改造要点：</b>
 * <ul>
 *   <li><b>Pattern Matching for instanceof（JEP 394）</b>：accessor 查找时使用模式匹配</li>
 *   <li><b>Sealed Exception（JEP 409）</b>：抛出精确的异常子类型</li>
 *   <li><b>Text Blocks（JDK 15+）</b>：Javadoc 示例代码更清晰</li>
 *   <li><b>Stream Gatherers（JEP 461）</b>：{@link EntityOps#findAll(List)} 使用 Gatherers 批量查询</li>
 * </ul>
 *
 * <p>简洁 API 示例：
 * <pre>{@code
 * // 查询（优先走缓存）
 * Optional<User> user = nexaTemplate.opsForEntity(User.class).findById(1L);
 *
 * // 写入（持久化 + 更新缓存）
 * nexaTemplate.opsForEntity(User.class).save(user);
 *
 * // 批量查询（Stream Gatherers 分批并行）
 * List<User> users = nexaTemplate.opsForEntity(User.class).findAll(ids);
 * }</pre>
 *
 * <p>记录集高级 API 示例：
 * <pre>{@code
 * try (var rs = nexaTemplate.opsForRecordSet(Product.class)) {
 *     rs.start(1L);
 *     Product p = rs.read().orElseThrow();
 *     p.setPrice(new BigDecimal("199.9"));
 *     rs.rewrite(p);  // 带乐观锁更新
 * }
 * }</pre>
 *
 * @author azir
 * @since 2.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class NexaTemplate {

    private final CacheRegistry registry;
    private final Map<Class<?>, DataAccessor<?, ?>> accessorMap;

    /** 操作对象缓存，避免重复创建 */
    private final ConcurrentHashMap<Class<?>, EntityOps<?, ?>> opsCache = new ConcurrentHashMap<>();

    // ===================== 简洁 API =====================

    /**
     * 获取指定实体类型的简洁操作对象（CRUD + 缓存协调）。
     */
    @SuppressWarnings("unchecked")
    public <T, ID> EntityOps<T, ID> opsForEntity(Class<T> entityClass) {
        return (EntityOps<T, ID>) opsCache.computeIfAbsent(entityClass, clazz -> {
            CacheRegion<T, ID> region = registry.getRegion(entityClass);
            // JEP 394: Pattern Matching for instanceof — 替代传统强转
            if (!(accessorMap.get(entityClass) instanceof DataAccessor<?, ?> raw)) {
                throw new NexaCacheException.DataAccessException(
                        "未找到实体类 [" + entityClass.getName() + "] 对应的 DataAccessor，请检查适配器配置");
            }
            DataAccessor<T, ID> accessor = (DataAccessor<T, ID>) raw;
            return new EntityOps<>(region, accessor);
        });
    }

    // ===================== 记录集高级 API =====================

    /**
     * 获取指定实体类型的记录集会话（游标风格高级操作）。
     * 推荐配合 try-with-resources 使用，确保游标资源自动释放。
     */
    @SuppressWarnings("unchecked")
    public <T, ID> RecordSetSession<T, ID> opsForRecordSet(Class<T> entityClass) {
        CacheRegion<T, ID> region = registry.getRegion(entityClass);
        // JEP 394: Pattern Matching for instanceof
        if (!(accessorMap.get(entityClass) instanceof DataAccessor<?, ?> raw)) {
            throw new NexaCacheException.DataAccessException(
                    "未找到实体类 [" + entityClass.getName() + "] 对应的 DataAccessor，请检查适配器配置");
        }
        DataAccessor<T, ID> accessor = (DataAccessor<T, ID>) raw;
        return new RecordSetSession<>(region, accessor);
    }

    // ===================== 通用操作 =====================

    /**
     * 注册 DataAccessor（通常由 Spring 自动装配完成）。
     */
    public <T, ID> void registerAccessor(DataAccessor<T, ID> accessor) {
        accessorMap.put(accessor.entityType(), accessor);
        log.info("[NexaCache] 注册 DataAccessor: {}", accessor.entityType().getSimpleName());
    }

    /**
     * 清空所有缓存区域。
     */
    public void clearAll() {
        registry.clearAll();
    }

    /**
     * 输出所有区域的统计快照（用于监控和调试）。
     */
    public String stats() {
        return registry.allSnapshots();
    }

    // ===================== 简洁操作内部类 =====================

    /**
     * 针对特定实体类型的简洁操作封装，提供 CRUD + 缓存协调。
     *
     * <p><b>JDK 25 改造要点：</b>
     * <ul>
     *   <li>{@link #findAll(List)} 使用 {@code Stream Gatherers（JEP 461）} 分批并行查询</li>
     *   <li>{@link #save(Object)} 使用 {@code var} 局部变量类型推断（JDK 10+）</li>
     * </ul>
     */
    @Slf4j
    @RequiredArgsConstructor
    public static final class EntityOps<T, ID> {

        private final CacheRegion<T, ID> region;
        private final DataAccessor<T, ID> accessor;

        /**
         * 根据主键查询（优先从缓存读取，未命中则查库并回填缓存）。
         */
        public Optional<T> findById(ID id) {
            var cached = region.get(id);
            if (cached.isPresent()) {
                log.debug("[NexaCache] 缓存命中: region={}, id={}", region.getMeta().region(), id);
                return cached;
            }
            log.debug("[NexaCache] 缓存未命中，查询数据库: region={}, id={}", region.getMeta().region(), id);
            var fromDb = accessor.findById(id);
            fromDb.ifPresent(region::put);
            return fromDb;
        }

        /**
         * 批量根据主键查询，优先从缓存读取，未命中的批量查库回填。
         *
         * <p><b>JDK 25 Stream Gatherers（JEP 461）</b>：
         * 委托给 {@link CacheRegion#getAll(List)} 使用 {@code Gatherers.windowFixed()}
         * 分批并行查询，充分利用多核。
         *
         * @param ids 主键列表
         * @return 命中的实体列表
         */
        public List<T> findAll(List<ID> ids) {
            return region.getAll(ids);
        }

        /**
         * 写入实体（持久化到数据库，并将结果写入缓存）。
         * 若主键为 null，则执行 INSERT 并回填自增主键；否则执行 UPDATE。
         */
        @SuppressWarnings("unchecked")
        public T save(T entity) {
            var meta = region.getMeta();
            var id = meta.extractId(entity);
            if (id == null) {
                accessor.insert(entity);
                log.debug("[NexaCache] INSERT 完成: region={}", meta.region());
            } else {
                accessor.update(entity);
                region.evict((ID) id);
                log.debug("[NexaCache] UPDATE 完成: region={}, id={}", meta.region(), id);
            }
            region.put(entity);
            return entity;
        }

        /**
         * 根据主键删除（数据库删除 + 缓存驱逐）。
         */
        public void deleteById(ID id) {
            accessor.deleteById(id);
            region.evict(id);
            log.debug("[NexaCache] DELETE 完成: region={}, id={}", region.getMeta().region(), id);
        }

        /**
         * 将实体加载到缓存（仅缓存，不操作数据库）。适用于缓存预热场景。
         */
        public void load(T entity) {
            region.put(entity);
        }

        /**
         * 批量预热：使用 Virtual Threads 并发写入缓存。
         *
         * @param entities 待预热的实体列表
         */
        public void warmUp(List<T> entities) {
            region.warmUp(entities);
        }

        /**
         * 驱逐指定主键的缓存（不操作数据库）。
         */
        public void evict(ID id) {
            region.evict(id);
        }

        /**
         * 判断指针层是否存在指定主键。
         */
        public boolean hasPointer(ID id) {
            return region.hasPointer(id);
        }

        /**
         * 返回当前缓存区域的估算条目数。
         */
        public long cacheSize() {
            return region.size();
        }

        /**
         * 返回当前区域的统计快照。
         */
        public String snapshot() {
            return region.snapshot();
        }
    }
}
