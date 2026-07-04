package io.nexacache.recordset;

/**
 * 游标状态枚举，描述 {@link RecordSetSession} 的当前生命周期阶段。
 *
 * <p><b>JDK 25 改造要点：</b>
 * <ul>
 *   <li><b>Switch Expression + Pattern Matching for Enums（JEP 441，JDK 21 正式）</b>：
 *       {@link #describe()} 和 {@link #isOperable()} 使用 switch 表达式，
 *       编译器强制覆盖所有 case，无需 default 分支，消除遗漏 case 的风险</li>
 * </ul>
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
 * @since 2.0.0
 */
public enum CursorState {

    /** 初始状态：记录集尚未启动，无任何缓存指针。 */
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
     * 此状态下任何操作均会抛出 {@link IllegalStateException}。
     */
    CLOSED;

    /**
     * 利用 JDK 21+ Switch Expression + Pattern Matching for Enums（JEP 441）
     * 对枚举进行完整的模式匹配，编译器强制覆盖所有 case，无需 default。
     *
     * @return 当前状态的中文描述
     */
    public String describe() {
        return switch (this) {
            case IDLE   -> "空闲：记录集尚未初始化";
            case READY  -> "就绪：单条记录已加载到缓存，可直接读取";
            case OPEN   -> "打开：多条记录已加载，游标处于活跃状态";
            case EOF    -> "越界：游标已超出记录集边界";
            case CLOSED -> "关闭：记录集已释放，不可再操作";
        };
    }

    /**
     * 判断当前状态是否允许读写操作。
     * 使用 switch 表达式替代 if-else 链，更清晰且编译器保证完整性。
     */
    public boolean isOperable() {
        return switch (this) {
            case READY, OPEN -> true;
            case IDLE, EOF, CLOSED -> false;
        };
    }

    /**
     * 判断当前状态是否允许游标移动（next/prev/first/last）。
     */
    public boolean isCursorMovable() {
        return this == OPEN;
    }
}
