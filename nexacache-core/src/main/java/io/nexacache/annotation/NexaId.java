package io.nexacache.annotation;

import java.lang.annotation.*;

/**
 * 标记实体类的主键字段。
 * NexaCache 通过此注解自动提取主键，用于构建缓存键，无需手动实现序列化方法。
 *
 * <p>示例：
 * <pre>{@code
 * @NexaEntity
 * public class User {
 *     @NexaId
 *     private Long id;
 * }
 * }</pre>
 *
 * @author azir
 * @since 1.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NexaId {
}
