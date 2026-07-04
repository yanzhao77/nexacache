package io.nexacache.adapter.mybatis;

import io.nexacache.domain.EntityMeta;
import io.nexacache.exception.NexaCacheException;
import io.nexacache.spi.DataAccessor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * MyBatis 持久层适配器抽象基类。
 * 子类只需指定实体类型与 Mapper 接口，即可获得完整的 CRUD 能力。
 *
 * <p>设计思路：通过 MyBatis 的 {@link SqlSession} 直接调用 Mapper 方法，
 * 利用 {@link EntityMeta} 中的元数据构建 SQL 语句 ID，实现通用 CRUD。
 *
 * <p><b>JDK 25 改造要点：</b>
 * <ul>
 *   <li><b>Text Blocks（JDK 15+）</b>：日志格式化与异常信息使用 text block，更清晰</li>
 *   <li><b>Pattern Matching for instanceof（JEP 394）</b>：
 *       {@link #invokeMapper} 中使用 pattern matching 替代传统强转</li>
 *   <li><b>Sealed Exception（JEP 409）</b>：
 *       抛出精确的 {@link NexaCacheException.DataAccessException} 子类型，
 *       调用方可通过 switch 完整处理</li>
 *   <li><b>Switch Expression（JEP 361）</b>：
 *       {@link #invokeMapper} 中使用 switch 表达式替代 if-else 链</li>
 * </ul>
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
 * @since 2.0.0
 */
@Slf4j
public abstract class AbstractMyBatisAccessor<T, ID> implements DataAccessor<T, ID> {

    private final SqlSessionFactory sqlSessionFactory;
    private final Class<T> entityClass;
    private final Class<?> mapperClass;
    /** 实体元数据（JDK 25 Record，方法名已去掉 get 前缀） */
    private final EntityMeta<T> meta;

    protected AbstractMyBatisAccessor(SqlSessionFactory sqlSessionFactory,
                                       Class<T> entityClass,
                                       Class<?> mapperClass) {
        this.sqlSessionFactory = sqlSessionFactory;
        this.entityClass = entityClass;
        this.mapperClass = mapperClass;
        this.meta = EntityMeta.of(entityClass);
        // JDK 15+ Text Blocks：多行日志更清晰
        log.info("""
                [NexaCache-MyBatis] 初始化 MyBatis 适配器:
                  entity   = {}
                  region   = {}
                  mapper   = {}
                """,
                entityClass.getSimpleName(),
                meta.region(),
                mapperClass.getSimpleName());
    }

    @Override
    public Optional<T> findById(ID id) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            Object mapper = session.getMapper(mapperClass);
            T result = invokeMapper(mapper, "selectById", id);
            return Optional.ofNullable(result);
        } catch (NexaCacheException e) {
            throw e;
        } catch (Exception e) {
            // Sealed Exception: 抛出精确子类型 DataAccessException
            throw NexaCacheException.dataAccess(
                    "findById 执行失败: entity=%s, id=%s".formatted(entityClass.getSimpleName(), id), e);
        }
    }

    @Override
    public List<T> findAll() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            Object mapper = session.getMapper(mapperClass);
            return invokeMapper(mapper, "selectAll");
        } catch (NexaCacheException e) {
            throw e;
        } catch (Exception e) {
            throw NexaCacheException.dataAccess(
                    "findAll 执行失败: entity=%s".formatted(entityClass.getSimpleName()), e);
        }
    }

    @Override
    public int insert(T entity) {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            Object mapper = session.getMapper(mapperClass);
            int rows = (int) invokeMapper(mapper, "insert", entity);
            log.debug("[NexaCache-MyBatis] INSERT {} 影响行数: {}", entityClass.getSimpleName(), rows);
            return rows;
        } catch (NexaCacheException e) {
            throw e;
        } catch (Exception e) {
            throw NexaCacheException.dataAccess(
                    "insert 执行失败: entity=%s".formatted(entityClass.getSimpleName()), e);
        }
    }

    @Override
    public int update(T entity) {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            Object mapper = session.getMapper(mapperClass);
            int rows = (int) invokeMapper(mapper, "updateById", entity);
            log.debug("[NexaCache-MyBatis] UPDATE {} 影响行数: {}", entityClass.getSimpleName(), rows);
            return rows;
        } catch (NexaCacheException e) {
            throw e;
        } catch (Exception e) {
            throw NexaCacheException.dataAccess(
                    "update 执行失败: entity=%s".formatted(entityClass.getSimpleName()), e);
        }
    }

    @Override
    public int deleteById(ID id) {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            Object mapper = session.getMapper(mapperClass);
            int rows = (int) invokeMapper(mapper, "deleteById", id);
            log.debug("[NexaCache-MyBatis] DELETE {} id={} 影响行数: {}", entityClass.getSimpleName(), id, rows);
            return rows;
        } catch (NexaCacheException e) {
            throw e;
        } catch (Exception e) {
            throw NexaCacheException.dataAccess(
                    "deleteById 执行失败: entity=%s, id=%s".formatted(entityClass.getSimpleName(), id), e);
        }
    }

    @Override
    public Class<T> entityType() {
        return entityClass;
    }

    /**
     * 通过反射调用 Mapper 方法。
     *
     * <p><b>JDK 25 Switch Expression（JEP 361）</b>：
     * 根据参数数量选择调用策略，使用 switch 表达式替代 if-else 链。
     *
     * @param mapper     Mapper 代理对象
     * @param methodName 方法名
     * @param args       方法参数
     * @return 方法返回值
     */
    @SuppressWarnings("unchecked")
    private <R> R invokeMapper(Object mapper, String methodName, Object... args) {
        try {
            // JEP 361: Switch Expression — 根据参数数量选择调用策略
            return switch (args.length) {
                case 0 -> (R) mapperClass.getMethod(methodName).invoke(mapper);
                default -> {
                    Class<?>[] paramTypes = Arrays.stream(args)
                            .map(Object::getClass)
                            .toArray(Class[]::new);
                    // 先尝试精确类型匹配
                    try {
                        yield (R) mapperClass.getMethod(methodName, paramTypes).invoke(mapper, args);
                    } catch (NoSuchMethodException ignored) {
                        // 回退：按方法名 + 参数数量匹配（处理接口类型参数场景）
                        Method method = Arrays.stream(mapperClass.getMethods())
                                .filter(m -> m.getName().equals(methodName)
                                        && m.getParameterCount() == args.length)
                                .findFirst()
                                // JEP 409: Sealed Exception — 抛出精确子类型
                                .orElseThrow(() -> NexaCacheException.dataAccess(
                                        "Mapper 方法未找到: %s#%s (参数数量=%d)"
                                                .formatted(mapperClass.getSimpleName(), methodName, args.length),
                                        null));
                        yield (R) method.invoke(mapper, args);
                    }
                }
            };
        } catch (NexaCacheException e) {
            throw e;
        } catch (Exception e) {
            throw NexaCacheException.dataAccess(
                    "Mapper 方法调用失败: %s#%s".formatted(mapperClass.getSimpleName(), methodName), e);
        }
    }
}
