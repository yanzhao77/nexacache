package io.nexacache.adapter.mybatis;

import io.nexacache.domain.EntityMeta;
import io.nexacache.exception.NexaCacheException;
import io.nexacache.spi.DataAccessor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis 持久层适配器抽象基类。
 * 子类只需指定实体类型与 Mapper 接口，即可获得完整的 CRUD 能力。
 *
 * <p>设计思路：通过 MyBatis 的 {@link SqlSession} 直接调用 Mapper 方法，
 * 利用 {@link EntityMeta} 中的元数据构建 SQL 语句 ID，实现通用 CRUD。
 *
 * <p>子类示例：
 * <pre>{@code
 * @Component
 * public class UserAccessor extends AbstractMyBatisAccessor<User, Long> {
 *     public UserAccessor(SqlSessionFactory factory) {
 *         super(factory, User.class, UserMapper.class);
 *     }
 * }
 * }</pre>
 *
 * @param <T>  实体类型
 * @param <ID> 主键类型
 * @author azir
 * @since 1.0.0
 */
@Slf4j
public abstract class AbstractMyBatisAccessor<T, ID> implements DataAccessor<T, ID> {

    private final SqlSessionFactory sqlSessionFactory;
    private final Class<T> entityClass;
    private final Class<?> mapperClass;
    private final EntityMeta<T> meta;

    protected AbstractMyBatisAccessor(SqlSessionFactory sqlSessionFactory,
                                       Class<T> entityClass,
                                       Class<?> mapperClass) {
        this.sqlSessionFactory = sqlSessionFactory;
        this.entityClass = entityClass;
        this.mapperClass = mapperClass;
        this.meta = EntityMeta.of(entityClass);
    }

    @Override
    public Optional<T> findById(ID id) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            Object mapper = session.getMapper(mapperClass);
            T result = invokeMapper(mapper, "selectById", id);
            return Optional.ofNullable(result);
        } catch (Exception e) {
            throw new NexaCacheException("findById 执行失败: entity=" + entityClass.getSimpleName() + ", id=" + id, e);
        }
    }

    @Override
    public List<T> findAll() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            Object mapper = session.getMapper(mapperClass);
            return invokeMapper(mapper, "selectAll");
        } catch (Exception e) {
            throw new NexaCacheException("findAll 执行失败: entity=" + entityClass.getSimpleName(), e);
        }
    }

    @Override
    public int insert(T entity) {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            Object mapper = session.getMapper(mapperClass);
            int rows = (int) invokeMapper(mapper, "insert", entity);
            log.debug("[NexaCache-MyBatis] INSERT {} 影响行数: {}", entityClass.getSimpleName(), rows);
            return rows;
        } catch (Exception e) {
            throw new NexaCacheException("insert 执行失败: entity=" + entityClass.getSimpleName(), e);
        }
    }

    @Override
    public int update(T entity) {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            Object mapper = session.getMapper(mapperClass);
            int rows = (int) invokeMapper(mapper, "updateById", entity);
            log.debug("[NexaCache-MyBatis] UPDATE {} 影响行数: {}", entityClass.getSimpleName(), rows);
            return rows;
        } catch (Exception e) {
            throw new NexaCacheException("update 执行失败: entity=" + entityClass.getSimpleName(), e);
        }
    }

    @Override
    public int deleteById(ID id) {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            Object mapper = session.getMapper(mapperClass);
            int rows = (int) invokeMapper(mapper, "deleteById", id);
            log.debug("[NexaCache-MyBatis] DELETE {} id={} 影响行数: {}", entityClass.getSimpleName(), id, rows);
            return rows;
        } catch (Exception e) {
            throw new NexaCacheException("deleteById 执行失败: entity=" + entityClass.getSimpleName() + ", id=" + id, e);
        }
    }

    @Override
    public Class<T> entityType() {
        return entityClass;
    }

    /**
     * 通过反射调用 Mapper 方法。
     * 支持 0 个或 1 个参数的方法调用。
     */
    @SuppressWarnings("unchecked")
    private <R> R invokeMapper(Object mapper, String methodName, Object... args) {
        try {
            if (args.length == 0) {
                return (R) mapperClass.getMethod(methodName).invoke(mapper);
            } else {
                Class<?>[] paramTypes = new Class[args.length];
                for (int i = 0; i < args.length; i++) {
                    paramTypes[i] = args[i].getClass();
                }
                // 尝试精确匹配，失败则遍历方法名匹配
                try {
                    return (R) mapperClass.getMethod(methodName, paramTypes).invoke(mapper, args);
                } catch (NoSuchMethodException e) {
                    return (R) java.util.Arrays.stream(mapperClass.getMethods())
                            .filter(m -> m.getName().equals(methodName) && m.getParameterCount() == args.length)
                            .findFirst()
                            .orElseThrow(() -> new NexaCacheException("Mapper 方法未找到: " + methodName))
                            .invoke(mapper, args);
                }
            }
        } catch (NexaCacheException e) {
            throw e;
        } catch (Exception e) {
            throw new NexaCacheException("Mapper 方法调用失败: " + methodName, e);
        }
    }
}
