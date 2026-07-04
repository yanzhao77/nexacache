package io.nexacache.api;

import io.nexacache.cache.CacheRegion;
import io.nexacache.cache.CacheRegistry;
import io.nexacache.domain.EntityMeta;
import io.nexacache.exception.NexaCacheException;
import io.nexacache.spi.DataAccessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NexaCache 核心门面，提供编程式缓存操作 API。
 * 类似 Spring 的 {@code RedisTemplate}，通过 {@code opsForEntity()} 获取特定实体的操作对象。
 *
 * <p>示例：
 * <pre>{@code
 * @Autowired NexaTemplate nexaTemplate;
 *
 * // 查询（优先走缓存）
 * Optional<User> user = nexaTemplate.opsForEntity(User.class).findById(1L);
 *
 * // 写入（持久化 + 更新缓存）
 * nexaTemplate.opsForEntity(User.class).save(user);
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

    /**
     * 获取指定实体类型的操作对象。
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

    /**
     * 注册 DataAccessor（通常由 Spring 自动装配完成）。
     */
    public <T, ID> void registerAccessor(DataAccessor<T, ID> accessor) {
        accessorMap.put(accessor.entityType(), accessor);
        log.info("[NexaCache] 注册 DataAccessor: {}", accessor.entityType().getSimpleName());
    }

    /**
     * 清空所有缓存。
     */
    public void clearAll() {
        registry.clearAll();
    }

    // ===================== 内部操作类 =====================

    /**
     * 针对特定实体类型的操作封装，提供 CRUD + 缓存协调。
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
            // 1. 查指针层（O(1)）
            Optional<T> cached = region.get(id);
            if (cached.isPresent()) {
                log.debug("[NexaCache] 缓存命中: region={}, id={}", region.getMeta().getRegion(), id);
                return cached;
            }
            // 2. 查数据库
            log.debug("[NexaCache] 缓存未命中，查询数据库: region={}, id={}", region.getMeta().getRegion(), id);
            Optional<T> fromDb = accessor.findById(id);
            // 3. 回填缓存
            fromDb.ifPresent(region::put);
            return fromDb;
        }

        /**
         * 写入实体（持久化到数据库，并将结果写入缓存）。
         * 若主键为 null，则执行 INSERT 并回填自增主键；否则执行 UPDATE。
         */
        public T save(T entity) {
            EntityMeta<T> meta = region.getMeta();
            Object id = meta.extractId(entity);
            if (id == null) {
                // INSERT
                accessor.insert(entity);
                log.debug("[NexaCache] INSERT 完成: region={}", meta.getRegion());
            } else {
                // UPDATE
                accessor.update(entity);
                // 驱逐旧缓存，写入新值
                region.evict((ID) id);
                log.debug("[NexaCache] UPDATE 完成: region={}, id={}", meta.getRegion(), id);
            }
            // 回填缓存（INSERT 后主键已由 MyBatis 回填到实体）
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
         * 将实体加载到缓存指针（仅缓存，不操作数据库）。
         * 相当于原框架的 START 操作。
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
         * 返回当前缓存区域的条目数。
         */
        public long cacheSize() {
            return region.size();
        }
    }
}
