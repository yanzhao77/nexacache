package io.nexacache.recordset;

import lombok.Getter;

import java.util.List;
import java.util.Optional;

/**
 * 游标，维护记录集内部的位置指针。
 *
 * <p>游标持有一个有序的主键列表（{@code idList}），通过整数索引（{@code position}）
 * 指向当前记录。位置从 0 开始，-1 表示游标在首条记录之前（BOF），
 * {@code idList.size()} 表示游标在末条记录之后（EOF）。
 *
 * @param <ID> 主键类型
 * @author azir
 * @since 1.1.0
 */
public final class Cursor<ID> {

    /** 记录集中所有主键的有序列表 */
    private final List<ID> idList;

    /** 当前游标位置，-1 表示 BOF */
    private int position;

    /** 版本快照表：主键 → 实体 hashCode（用于乐观锁校验） */
    private final java.util.Map<ID, Integer> versionSnapshot = new java.util.HashMap<>();

    @Getter
    private CursorState state;

    public Cursor(List<ID> idList) {
        this.idList = idList;
        this.position = idList.isEmpty() ? -1 : 0;
        this.state = CursorState.OPEN;
    }

    /** 单记录游标（START 操作后使用） */
    public Cursor(ID singleId) {
        this(java.util.List.of(singleId));
        this.state = CursorState.READY;
    }

    /**
     * 获取当前游标指向的主键。
     *
     * @return 当前主键，若越界则返回 empty
     */
    public Optional<ID> currentId() {
        if (position < 0 || position >= idList.size()) {
            return Optional.empty();
        }
        return Optional.of(idList.get(position));
    }

    /**
     * 游标前移一位。
     *
     * @return 前移后是否仍在有效范围内
     */
    public boolean next() {
        if (position < idList.size()) {
            position++;
        }
        boolean valid = position < idList.size();
        if (!valid) state = CursorState.EOF;
        return valid;
    }

    /**
     * 游标后移一位。
     *
     * @return 后移后是否仍在有效范围内
     */
    public boolean prev() {
        if (position > -1) {
            position--;
        }
        boolean valid = position >= 0;
        if (!valid) state = CursorState.EOF;
        return valid;
    }

    /**
     * 将游标移动到第一条记录。
     */
    public void first() {
        position = idList.isEmpty() ? -1 : 0;
        if (!idList.isEmpty()) state = CursorState.OPEN;
    }

    /**
     * 将游标移动到最后一条记录。
     */
    public void last() {
        position = idList.isEmpty() ? -1 : idList.size() - 1;
        if (!idList.isEmpty()) state = CursorState.OPEN;
    }

    /**
     * 记录版本快照（用于乐观锁）。
     *
     * @param id      主键
     * @param entity  实体对象
     */
    public void snapshot(ID id, Object entity) {
        versionSnapshot.put(id, System.identityHashCode(entity));
    }

    /**
     * 校验版本快照（乐观锁检查）。
     *
     * @param id     主键
     * @param entity 当前实体对象
     * @return true 表示版本未变（可安全更新），false 表示已被其他线程修改
     */
    public boolean checkVersion(ID id, Object entity) {
        Integer snapshot = versionSnapshot.get(id);
        if (snapshot == null) return true; // 未记录快照，跳过检查
        return snapshot.equals(System.identityHashCode(entity));
    }

    /**
     * 关闭游标，释放资源。
     */
    public void close() {
        this.state = CursorState.CLOSED;
        this.versionSnapshot.clear();
    }

    /**
     * 返回记录集总条数。
     */
    public int size() {
        return idList.size();
    }

    /**
     * 返回当前游标位置（0-based）。
     */
    public int position() {
        return position;
    }

    /**
     * 判断游标是否处于有效位置。
     */
    public boolean isValid() {
        return state == CursorState.OPEN || state == CursorState.READY;
    }
}
