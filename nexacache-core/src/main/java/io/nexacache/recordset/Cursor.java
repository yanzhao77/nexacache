package io.nexacache.recordset;

import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 游标，维护记录集内部的位置指针。
 *
 * <p>游标持有一个有序的主键列表（{@code idList}），通过整数索引（{@code position}）
 * 指向当前记录。
 *
 * <p>位置约定：
 * <ul>
 *   <li>{@code -1}：BOF（Before Of File），游标在首条记录之前，需调用 {@code next()} 才能读取</li>
 *   <li>{@code 0 ~ size-1}：有效记录位置</li>
 *   <li>{@code size}：EOF（End Of File），游标越过最后一条记录</li>
 * </ul>
 *
 * <p>注意：OPEN 模式（{@code openAll/open}）初始位置为 BOF（-1），需调用 {@code next()} 移到第一条；
 * START 模式（{@code start}）初始位置直接为 0，可直接调用 {@code read()} 读取。
 *
 * @param <ID> 主键类型
 * @author azir
 * @since 1.1.0
 */
public final class Cursor<ID> {

    /** 记录集中所有主键的有序列表（可变，支持 delete 后移除） */
    private final List<ID> idList;

    /** 当前游标位置，-1 表示 BOF */
    private int position;

    /** 版本快照表：主键 → 实体 identityHashCode（用于乐观锁校验） */
    private final Map<ID, Integer> versionSnapshot = new HashMap<>();

    @Getter
    private CursorState state;

    /**
     * OPEN 模式构造：初始直接指向第一条（position=0），可直接调用 current() 读取，调用 next() 后移到下一条。
     *
     * @param idList 主键列表
     */
    public Cursor(List<ID> idList) {
        this.idList = new ArrayList<>(idList);
        // OPEN 模式：初始直接指向第一条（position=0），与 START 模式语义一致
        this.position = idList.isEmpty() ? -1 : 0;
        this.state = idList.isEmpty() ? CursorState.EOF : CursorState.OPEN;
    }

    /**
     * START 模式构造：单记录游标，初始直接指向该记录（position=0），可直接调用 read()。
     *
     * @param singleId 单条记录的主键
     */
    public Cursor(ID singleId) {
        this.idList = new ArrayList<>(List.of(singleId));
        // START 模式：直接指向第一条（position=0），可立即 read()
        this.position = 0;
        this.state = CursorState.READY;
    }

    /**
     * 获取当前游标指向的主键。
     *
     * @return 当前主键，若越界（BOF/EOF）则返回 empty
     */
    public Optional<ID> currentId() {
        if (position < 0 || position >= idList.size()) {
            return Optional.empty();
        }
        return Optional.of(idList.get(position));
    }

    /**
     * 游标前移一位（FETCH NEXT）。
     *
     * @return 前移后是否仍在有效范围内
     */
    public boolean next() {
        if (position < idList.size()) {
            position++;
        }
        boolean valid = position >= 0 && position < idList.size();
        if (!valid && position >= idList.size()) {
            state = CursorState.EOF;
        }
        return valid;
    }

    /**
     * 游标后移一位（FETCH PRIOR）。
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
     * 将游标移动到第一条记录（FETCH FIRST）。
     */
    public void first() {
        position = idList.isEmpty() ? -1 : 0;
        if (!idList.isEmpty()) state = CursorState.OPEN;
    }

    /**
     * 将游标移动到最后一条记录（FETCH LAST）。
     */
    public void last() {
        position = idList.isEmpty() ? -1 : idList.size() - 1;
        if (!idList.isEmpty()) state = CursorState.OPEN;
    }

    /**
     * 从记录集中移除当前游标指向的主键（DELETE 操作后调用）。
     * 移除后游标位置保持不变（下一条记录自动顶上来），若已是最后一条则退到前一条。
     */
    public void removeCurrent() {
        if (position >= 0 && position < idList.size()) {
            idList.remove(position);
            // 移除后若 position 超出范围，退回到最后一条
            if (position >= idList.size()) {
                position = idList.size() - 1;
            }
            if (idList.isEmpty()) {
                state = CursorState.EOF;
            }
        }
    }

    /**
     * 记录版本快照（用于乐观锁）。
     *
     * @param id     主键
     * @param entity 实体对象
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
     * 返回记录集当前总条数（delete 后会减少）。
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
     * 判断游标是否处于有效位置（可读取数据）。
     */
    public boolean isValid() {
        return (state == CursorState.OPEN || state == CursorState.READY)
                && position >= 0 && position < idList.size();
    }
}
