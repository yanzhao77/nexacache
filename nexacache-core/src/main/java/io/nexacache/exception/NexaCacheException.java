package io.nexacache.exception;

/**
 * NexaCache 框架异常体系。
 *
 * <p><b>JDK 25 改造要点：</b>
 * <ul>
 *   <li><b>Sealed Classes（JDK 17+，JEP 409）</b>：将异常体系改为 sealed hierarchy，
 *       明确限定所有子类，使调用方可以通过 switch + Pattern Matching 对异常类型进行
 *       完整、安全的分支处理，无需 default 分支</li>
 *   <li>子类均为 {@code final}，防止外部随意扩展</li>
 * </ul>
 *
 * <p>异常层次：
 * <pre>
 *   NexaCacheException (sealed)
 *   ├── EntityNotRegisteredException  — 实体未注册到缓存区域
 *   ├── CacheAccessException          — 缓存读写操作失败
 *   └── DataAccessException           — 持久层 SPI 调用失败
 * </pre>
 *
 * <p>调用方可以利用 Pattern Matching 对异常进行完整分支处理：
 * <pre>{@code
 * try {
 *     nexaTemplate.opsForEntity(Product.class).get(1L);
 * } catch (NexaCacheException e) {
 *     switch (e) {
 *         case EntityNotRegisteredException ex -> log.error("实体未注册: {}", ex.getMessage());
 *         case CacheAccessException ex        -> log.error("缓存访问失败: {}", ex.getMessage());
 *         case DataAccessException ex         -> log.error("数据库访问失败: {}", ex.getMessage());
 *     }
 * }
 * }</pre>
 *
 * @author azir
 * @since 2.0.0
 */
public sealed class NexaCacheException extends RuntimeException
        permits NexaCacheException.EntityNotRegisteredException,
                NexaCacheException.CacheAccessException,
                NexaCacheException.DataAccessException {

    public NexaCacheException(String message) {
        super(message);
    }

    public NexaCacheException(String message, Throwable cause) {
        super(message, cause);
    }

    // ===================== 子类定义 =====================

    /**
     * 实体类未注册到缓存区域时抛出。
     * 通常原因：实体类未标注 {@code @NexaEntity}，或未被包扫描到。
     */
    public static final class EntityNotRegisteredException extends NexaCacheException {
        public EntityNotRegisteredException(String message) {
            super(message);
        }
        public EntityNotRegisteredException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 缓存读写操作失败时抛出。
     * 通常原因：MethodHandle 调用失败、类型转换错误等。
     */
    public static final class CacheAccessException extends NexaCacheException {
        public CacheAccessException(String message) {
            super(message);
        }
        public CacheAccessException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 持久层 SPI 调用失败时抛出。
     * 通常原因：数据库连接异常、SQL 执行错误等。
     */
    public static final class DataAccessException extends NexaCacheException {
        public DataAccessException(String message) {
            super(message);
        }
        public DataAccessException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // ===================== 工厂方法 =====================

    /** 创建"实体未注册"异常 */
    public static EntityNotRegisteredException notRegistered(String region) {
        return new EntityNotRegisteredException(
                "缓存区域 [" + region + "] 未注册，请确保实体类标注了 @NexaEntity 且已被包扫描到");
    }

    /** 创建"缓存访问失败"异常 */
    public static CacheAccessException cacheAccess(String message, Throwable cause) {
        return new CacheAccessException(message, cause);
    }

    /** 创建"持久层访问失败"异常 */
    public static DataAccessException dataAccess(String message, Throwable cause) {
        return new DataAccessException(message, cause);
    }
}
