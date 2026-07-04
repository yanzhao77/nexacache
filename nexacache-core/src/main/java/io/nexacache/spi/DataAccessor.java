package io.nexacache.spi;

import java.util.List;
import java.util.Optional;

/**
 * 持久层数据访问 SPI 接口。
 * NexaCache 核心引擎通过此接口与底层 ORM 框架解耦，
 * 不同 ORM 框架（MyBatis、JPA 等）只需提供对应的实现即可。
 *
 * @param <T>  实体类型
 * @param <ID> 主键类型
 * @author azir
 * @since 1.0.0
 */
public interface DataAccessor<T, ID> {

    /**
     * 根据主键查询单条记录。
     *
     * @param id 主键值
     * @return 实体 Optional 包装
     */
    Optional<T> findById(ID id);

    /**
     * 查询所有记录。
     *
     * @return 实体列表
     */
    List<T> findAll();

    /**
     * 插入一条记录，并回填自增主键。
     *
     * @param entity 待插入实体
     * @return 影响行数
     */
    int insert(T entity);

    /**
     * 根据主键更新一条记录（全字段更新）。
     *
     * @param entity 待更新实体
     * @return 影响行数
     */
    int update(T entity);

    /**
     * 根据主键删除一条记录。
     *
     * @param id 主键值
     * @return 影响行数
     */
    int deleteById(ID id);

    /**
     * 获取此访问器对应的实体类型。
     *
     * @return 实体 Class 对象
     */
    Class<T> entityType();
}
