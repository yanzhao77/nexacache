package io.nexacache.recordset;

/**
 * 游标状态枚举，描述 {@link RecordSetSession} 的当前生命周期阶段。
 *
 * <p>状态流转：
 * <pre>
 *   IDLE ──start()──► READY ──open()──► OPEN ──close()──► CLOSED
 *                       │                 │
 *                    read()            next()/prev()
 *                       │                 │
 *                    返回实体          游标移动（到达末尾时变为 EOF）
 * </pre>
 *
 * @author azir
 * @since 1.1.0
 */
public enum CursorState {

    /**
     * 初始状态：记录集尚未启动，无任何缓存指针。
     */
    IDLE,

    /**
     * 就绪状态：已通过 {@code start()} 将单条记录加载到缓存，指针已建立。
     * 可直接调用 {@code read()} 读取，或调用 {@code open()} 进入批量游标模式。
     */
    READY,

    /**
     * 打开状态：记录集已通过 {@code open()} 加载多条记录，游标处于活跃状态。
     * 可调用 {@code next()}、{@code prev()}、{@code current()} 等游标操作。
     */
    OPEN,

    /**
     * 游标越界状态：{@code next()} 或 {@code prev()} 已超出记录集边界。
     * 此时 {@code current()} 返回 {@code Optional.empty()}。
     */
    EOF,

    /**
     * 关闭状态：记录集已通过 {@code close()} 关闭，游标资源已释放。
     * 此状态下任何操作均会抛出 {@link io.nexacache.exception.NexaCacheException}。
     */
    CLOSED
}
