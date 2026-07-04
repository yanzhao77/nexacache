package io.nexacache.api;

import io.nexacache.cache.CacheRegion;
import io.nexacache.cache.CacheRegistry;
import io.nexacache.domain.EntityMeta;
import io.nexacache.exception.NexaCacheException;
import io.nexacache.recordset.RecordSetSession;
import io.nexacache.spi.DataAccessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
 * <p>简洁 API 示例：
 * <pre>{@code
 * // 查询（优先走缓存）
 * Optional<User> user = nexaTemplate.opsForEntity(User.class).findById(1L);
 *
 * // 写入（持久化 + 更新缓存）
 * nexaTemplate.opsForEntity(User.class).save(user);
 * }</pre>
 *
 * <p>记录集高级 API 示例：
 * <pre>{@code
 * // START + READ：加载单条记录并读取
 * try (RecordSetSession<Product, Long> rs = nexaTemplate.opsForRecordSet(Product.class)) {
 *     rs.start(1L);
 *     Product p = rs.read().orElseThrow();
 *
 *     // REWRITE：带乐观锁更新
 *     p.setPrice(new BigDecimal("199.9"));
 *     rs.rewrite(p);
 * }
 *
 * // OPEN + 游标遍历
 * try (RecordSetSession<Product, Long> rs = nexaTemplate.opsForRecordSet(Product.class)) {
 *     rs.openAll();
 *     while (rs.next()) {
 *         Product cur = rs.current().orElseThrow();
 *         System.out.println(cur.getName());
 *     }
 * }
 * }</pre>
 *
 * @author azir
 * @since 1.0.0
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
     *
     * @param entityClass 实体类型
     * @param <T>         实体类型
     * @param <ID>        主键类型
     * @return 实体操作对象
     */
    @SuppressWarnings("unchecked")
    public <T, ID> EntityOps<T, ID> opsForEntity(Class<T> entityClass) {
        return (EntityOps<T, ID>) opsCache.computeIfAbsent(entityClass, clazz -> {
            CacheRegion<T, ID> region = registry.getRegion(entityClass);
            DataAccessor<T, ID> accessor = (DataAccessor<T, ID>) accessorMap.get(entityClass);
            if (accessor == null) {
                throw new NexaCacheException("未找到实体类 [" + entityClass.getName() + "] 对应的 DataAccessor，请检查适配器配置");
            }
            return new EntityOps<>(region, accessor);
        });
    }

    // ===================== 记录集高级 API =====================

    /**
     * 获取指定实体类型的记录集会话（游标风格高级操作）。
     *
     * <p>返回的 {@link RecordSetSession} 实现了 {@link AutoCloseable}，
     * 推荐配合 try-with-resources 使用，确保游标资源自动释放。
     *
     * <p>操作语义：
     * <ul>
     *   <li>{@code start(id)}   — 将单条记录加载到缓存，建立指针（类似数据库 FETCH）</li>
     *   <li>{@code open(list)}  — 批量加载记录并打开游标（类似 OPEN CURSOR）</li>
     *   <li>{@code openAll()}   — 从数据库查询全部记录并打开游标</li>
     *   <li>{@code read()}      — 读取 start() 加载的单条记录</li>
     *   <li>{@code current()}   — 读取游标当前指向的记录</li>
     *   <li>{@code next()}      — 游标前移（类似 FETCH NEXT）</li>
     *   <li>{@code prev()}      — 游标后移（类似 FETCH PRIOR）</li>
     *   <li>{@code first()}     — 游标移到第一条（类似 FETCH FIRST）</li>
     *   <li>{@code last()}      — 游标移到最后一条（类似 FETCH LAST）</li>
     *   <li>{@code write(e)}    — 新增记录（持久化 + 写缓存）</li>
     *   <li>{@code rewrite(e)}  — 更新记录（含乐观锁校验）</li>
     *   <li>{@code delete()}    — 删除当前游标记录</li>
     *   <li>{@code close()}     — 关闭记录集，释放游标资源</li>
     * </ul>
     *
     * @param entityClass 实体类型
     * @param <T>         实体类型
     * @param <ID>        主键类型
     * @return 记录集会话（AutoCloseable）
     */
    @SuppressWarnings("unchecked")
    public <T, ID> RecordSetSession<T, ID> opsForRecordSet(Class<T> entityClass) {
        CacheRegion<T, ID> region = registry.getRegion(entityClass);
        DataAccessor<T, ID> accessor = (DataAccessor<T, ID>) accessorMap.get(entityClass);
        if (accessor == null) {
            throw new NexaCacheException("未找到实体类 [" + entityClass.getName() + "] 对应的 DataAccessor，请检查适配器配置");
        }
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

    // ===================== 简洁操作内部类 =====================

    /**
     * 针对特定实体类型的简洁操作封装，提供 CRUD + 缓存协调。
     *
     * <p>适合常规业务场景，无需关心游标、版本控制等细节。
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
            Optional<T> cached = region.get(id);
            if (cached.isPresent()) {
                log.debug("[NexaCache] 缓存命中: region={}, id={}", region.getMeta().getRegion(), id);
                return cached;
            }
            log.debug("[NexaCache] 缓存未命中，查询数据库: region={}, id={}", region.getMeta().getRegion(), id);
            Optional<T> fromDb = accessor.findById(id);
            fromDb.ifPresent(region::put);
            return fromDb;
        }

        /**
         * 写入实体（持久化到数据库，并将结果写入缓存）。
         * 若主键为 null，则执行 INSERT 并回填自增主键；否则执行 UPDATE。
         */
        @SuppressWarnings("unchecked")
        public T save(T entity) {
            EntityMeta<T> meta = region.getMeta();
            Object id = meta.extractId(entity);
            if (id == null) {
                accessor.insert(entity);
                log.debug("[NexaCache] INSERT 完成: region={}", meta.getRegion());
            } else {
                accessor.update(entity);
                region.evict((ID) id);
                log.debug("[NexaCache] UPDATE 完成: region={}, id={}", meta.getRegion(), id);
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
            log.debug("[NexaCache] DELETE 完成: region={}, id={}", region.getMeta().getRegion(), id);
        }

        /**
         * 将实体加载到缓存（仅缓存，不操作数据库）。
         * 适用于缓存预热场景。
         */
        public void load(T entity) {
            region.put(entity);
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
    }
}
