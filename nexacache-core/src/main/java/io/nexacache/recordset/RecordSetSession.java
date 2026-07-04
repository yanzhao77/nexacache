package io.nexacache.recordset;

import io.nexacache.cache.CacheRegion;
import io.nexacache.exception.NexaCacheException;
import io.nexacache.spi.DataAccessor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 记录集会话，提供数据库游标风格的高级操作 API。
 *
 * <p>RecordSetSession 是 NexaCache 的高级操作层，在 {@link io.nexacache.api.NexaTemplate.EntityOps}
 * 简洁 API 的基础上，增加了游标导航、乐观锁更新、批量预热等能力，
 * 适合需要逐条遍历或精细控制缓存生命周期的场景。
 *
 * <p>典型使用方式：
 * <pre>{@code
 * try (RecordSetSession<Product, Long> rs = nexaTemplate.opsForRecordSet(Product.class)) {
 *
 *     // START：将单条记录加载到缓存
 *     rs.start(1L);
 *     Product p = rs.read().orElseThrow();
 *
 *     // OPEN：打开批量游标
 *     rs.open(productMapper.selectAll());
 *     while (rs.next()) {
 *         Product cur = rs.current().orElseThrow();
 *         // 处理当前记录...
 *     }
 *
 *     // REWRITE：乐观锁更新
 *     rs.start(1L);
 *     rs.rewrite(updatedProduct);
 *
 * } // 自动 CLOSE
 * }</pre>
 *
 * @param <T>  实体类型
 * @param <ID> 主键类型
 * @author azir
 * @since 1.1.0
 */
@Slf4j
public final class RecordSetSession<T, ID> implements AutoCloseable {

    private final CacheRegion<T, ID> region;
    private final DataAccessor<T, ID> accessor;
    private Cursor<ID> cursor;

    public RecordSetSession(CacheRegion<T, ID> region, DataAccessor<T, ID> accessor) {
        this.region = region;
        this.accessor = accessor;
        this.cursor = null;
    }

    // ===================== START 操作 =====================

    /**
     * START：将指定主键的记录从数据库加载到缓存，建立指针。
     * 若缓存中已存在则直接使用，不重复查库。
     *
     * @param id 主键值
     * @return 当前 RecordSetSession（支持链式调用）
     */
    public RecordSetSession<T, ID> start(ID id) {
        Optional<T> cached = region.get(id);
        T entity;
        if (cached.isPresent()) {
            entity = cached.get();
            log.debug("[NexaCache-RS] START 缓存命中: region={}, id={}", region.getMeta().region(), id);
        } else {
            entity = accessor.findById(id)
                    .orElseThrow(() -> new NexaCacheException(
                            "START 失败：数据库中不存在 id=" + id + " 的记录"));
            region.put(entity);
            log.debug("[NexaCache-RS] START 从数据库加载: region={}, id={}", region.getMeta().region(), id);
        }
        this.cursor = new Cursor<>(id);
        this.cursor.snapshot(id, entity);
        return this;
    }

    // ===================== OPEN 操作 =====================

    /**
     * OPEN：将一批实体加载到缓存，并打开游标指向第一条记录。
     * 通常与 Mapper 的 {@code selectAll()} 或条件查询配合使用。
     *
     * @param entities 实体列表
     * @return 当前 RecordSetSession（支持链式调用）
     */
    @SuppressWarnings("unchecked")
    public RecordSetSession<T, ID> open(List<T> entities) {
        if (entities == null || entities.isEmpty()) {
            log.warn("[NexaCache-RS] OPEN 传入空列表，游标将处于 EOF 状态");
            this.cursor = new Cursor<>(List.of());
            return this;
        }
        // 批量写入缓存
        entities.forEach(region::put);
        // 提取主键列表，建立游标
        List<ID> idList = entities.stream()
                .map(e -> (ID) region.getMeta().extractId(e))
                .collect(Collectors.toList());
        this.cursor = new Cursor<>(idList);
        // 为每条记录记录版本快照
        entities.forEach(e -> cursor.snapshot((ID) region.getMeta().extractId(e), e));
        log.info("[NexaCache-RS] OPEN 完成: region={}, 共 {} 条记录", region.getMeta().region(), entities.size());
        return this;
    }

    /**
     * OPEN：直接从数据库查询全部记录并打开游标。
     *
     * @return 当前 RecordSetSession（支持链式调用）
     */
    public RecordSetSession<T, ID> openAll() {
        return open(accessor.findAll());
    }

    // ===================== READ 操作 =====================

    /**
     * READ：读取游标当前指向的记录（优先从缓存读取）。
     *
     * @return 当前记录的 Optional 包装
     */
    @SuppressWarnings("unchecked")
    public Optional<T> current() {
        ensureNotClosed();
        return cursor.currentId().flatMap(id -> {
            Optional<T> cached = region.get(id);
            if (cached.isPresent()) {
                return cached;
            }
            // 缓存未命中（可能被驱逐），从数据库补查并回填
            log.debug("[NexaCache-RS] current() 缓存未命中，从数据库补查: id={}", id);
            Optional<T> fromDb = accessor.findById(id);
            fromDb.ifPresent(region::put);
            return fromDb;
        });
    }

    /**
     * READ（START 后使用）：读取 start() 加载的单条记录。
     * 等同于 {@link #current()}，语义更明确。
     *
     * @return 记录的 Optional 包装
     */
    public Optional<T> read() {
        return current();
    }

    // ===================== 游标导航 =====================

    /**
     * NEXT：游标前移一位。
     *
     * @return 前移后游标是否仍指向有效记录
     */
    public boolean next() {
        ensureNotClosed();
        boolean valid = cursor.next();
        log.debug("[NexaCache-RS] NEXT: position={}, valid={}", cursor.position(), valid);
        return valid;
    }

    /**
     * PREV：游标后移一位。
     *
     * @return 后移后游标是否仍指向有效记录
     */
    public boolean prev() {
        ensureNotClosed();
        boolean valid = cursor.prev();
        log.debug("[NexaCache-RS] PREV: position={}, valid={}", cursor.position(), valid);
        return valid;
    }

    /**
     * 将游标移动到第一条记录。
     */
    public RecordSetSession<T, ID> first() {
        ensureNotClosed();
        cursor.first();
        return this;
    }

    /**
     * 将游标移动到最后一条记录。
     */
    public RecordSetSession<T, ID> last() {
        ensureNotClosed();
        cursor.last();
        return this;
    }

    // ===================== WRITE 操作 =====================

    /**
     * WRITE：新增一条记录（持久化到数据库 + 写入缓存）。
     *
     * @param entity 待新增实体（主键应为 null，由数据库自动生成）
     * @return 新增后的实体（含自增主键）
     */
    public T write(T entity) {
        ensureNotClosed();
        accessor.insert(entity);
        region.put(entity);
        log.debug("[NexaCache-RS] WRITE 完成: region={}", region.getMeta().region());
        return entity;
    }

    // ===================== REWRITE 操作（含乐观锁） =====================

    /**
     * REWRITE：更新当前游标指向的记录，内置乐观锁校验。
     *
     * <p>框架会比对 {@link #start(Object)} 时记录的版本快照与当前缓存实体，
     * 若期间有其他线程修改了该记录，将抛出 {@link java.util.ConcurrentModificationException}。
     *
     * @param entity 更新后的实体
     * @return 更新后的实体
     * @throws java.util.ConcurrentModificationException 若乐观锁校验失败
     */
    @SuppressWarnings("unchecked")
    public T rewrite(T entity) {
        ensureNotClosed();
        ID id = (ID) region.getMeta().extractId(entity);

        // 乐观锁校验（cursor 为 null 时跳过，说明未经 start() 直接调用）
        Optional<T> current = region.get(id);
        if (cursor != null && current.isPresent() && !cursor.checkVersion(id, current.get())) {
            throw new java.util.ConcurrentModificationException(
                    "[NexaCache-RS] REWRITE 乐观锁冲突：记录 id=" + id + " 已被其他线程修改，请重新 START 后再操作");
        }

        accessor.update(entity);
        region.evict(id);
        region.put(entity);
        // 更新版本快照（cursor 为 null 时跳过）
        if (cursor != null) cursor.snapshot(id, entity);
        log.debug("[NexaCache-RS] REWRITE 完成: region={}, id={}", region.getMeta().region(), id);
        return entity;
    }

    // ===================== DELETE 操作 =====================

    /**
     * DELETE：删除当前游标指向的记录（数据库 + 缓存同步删除）。
     */
    @SuppressWarnings("unchecked")
    public void delete() {
        ensureNotClosed();
        cursor.currentId().ifPresent(id -> {
            accessor.deleteById(id);
            region.evict(id);
            cursor.removeCurrent(); // 从游标 idList 中移除该条记录
            log.debug("[NexaCache-RS] DELETE 完成: region={}, id={}", region.getMeta().region(), id);
        });
    }

    /**
     * DELETE：根据指定主键删除记录（不依赖游标位置）。
     *
     * @param id 主键值
     */
    public void deleteById(ID id) {
        ensureNotClosed();
        accessor.deleteById(id);
        region.evict(id);
        log.debug("[NexaCache-RS] DELETE by id 完成: region={}, id={}", region.getMeta().region(), id);
    }

    // ===================== CLOSE 操作 =====================

    /**
     * CLOSE：关闭记录集，释放游标资源。
     * 实现 {@link AutoCloseable}，支持 try-with-resources 语法。
     */
    @Override
    public void close() {
        if (cursor != null && cursor.getState() != CursorState.CLOSED) {
            cursor.close();
            log.debug("[NexaCache-RS] CLOSE: region={}", region.getMeta().region());
        }
    }

    // ===================== 状态查询 =====================

    /**
     * 返回记录集中的总记录数。
     */
    public int size() {
        return cursor == null ? 0 : cursor.size();
    }

    /**
     * 返回当前游标位置（0-based）。
     */
    public int position() {
        return cursor == null ? -1 : cursor.position();
    }

    /**
     * 返回当前游标状态。
     */
    public CursorState state() {
        return cursor == null ? CursorState.IDLE : cursor.getState();
    }

    /**
     * 判断游标是否处于有效位置（可读取数据）。
     */
    public boolean isValid() {
        return cursor != null && cursor.isValid();
    }

    // ===================== 内部工具 =====================

    private void ensureNotClosed() {
        if (cursor != null && cursor.getState() == CursorState.CLOSED) {
            throw new IllegalStateException("[NexaCache-RS] 记录集已关闭，请重新打开后操作");
        }
        // cursor 为 null 表示尚未初始化（IDLE 状态），允许部分操作（如 write、rewrite）
    }
}
