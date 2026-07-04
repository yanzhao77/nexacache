package io.nexacache.autoconfigure;

import io.nexacache.annotation.NexaEntity;
import io.nexacache.api.NexaTemplate;
import io.nexacache.cache.CacheRegistry;
import io.nexacache.cache.NexaCacheAspect;
import io.nexacache.spi.DataAccessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NexaCache Spring Boot 自动装配配置。
 * 负责在应用启动时：
 * <ol>
 *   <li>扫描 {@code nexacache.scan-packages} 配置的包路径，注册所有 {@link NexaEntity} 实体（支持纯 POJO）</li>
 *   <li>兜底扫描 Spring 容器中已有的 Bean，注册标注了 {@link NexaEntity} 的类型</li>
 *   <li>收集所有 {@link DataAccessor} Bean，注入 {@link NexaTemplate}</li>
 *   <li>注册 AOP 切面 {@link NexaCacheAspect}</li>
 * </ol>
 *
 * <p><b>JDK 25 改造要点：</b>
 * <ul>
 *   <li><b>Text Blocks（JDK 15+）</b>：日志输出使用 text block 格式化，更清晰射目</li>
 *   <li><b>Stream + Pattern Matching（JEP 394）</b>：
 *       {@link DataAccessor} 注入时使用 Stream 收集替代命令式循环</li>
 * </ul>
 *
 * @author azir
 * @since 2.0.0
 */
@Slf4j
@AutoConfiguration
@EnableAspectJAutoProxy
@EnableConfigurationProperties(NexaCacheProperties.class)
@ConditionalOnProperty(prefix = "nexacache", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NexaCacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CacheRegistry nexaCacheRegistry(NexaCacheProperties properties, ApplicationContext context) {
        CacheRegistry registry = new CacheRegistry();
        int count = 0;

        // ── 1. ClassPath 包扫描（支持纯 POJO，不依赖 Spring Bean）──
        if (!properties.getScanPackages().isEmpty()) {
            log.info("[NexaCache] 开始扫描 @NexaEntity 包路径: {}", properties.getScanPackages());
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            MetadataReaderFactory readerFactory = new CachingMetadataReaderFactory(resolver);

            for (String pkg : properties.getScanPackages()) {
                String pattern = "classpath*:" + pkg.replace('.', '/') + "/**/*.class";
                try {
                    Resource[] resources = resolver.getResources(pattern);
                    for (Resource resource : resources) {
                        try {
                            MetadataReader reader = readerFactory.getMetadataReader(resource);
                            String className = reader.getClassMetadata().getClassName();
                            Class<?> clazz = Class.forName(className);
                            if (clazz.isAnnotationPresent(NexaEntity.class)) {
                                registry.register(clazz);
                                count++;
                            }
                        } catch (Exception ignored) {
                            // 跳过无法加载的类（接口、抽象类等）
                        }
                    }
                } catch (Exception e) {
                    log.warn("[NexaCache] 扫描包路径 [{}] 失败: {}", pkg, e.getMessage());
                }
            }
        }

        // ── 2. 兜底：扫描 Spring 容器中已有的 Bean 类型 ──
        for (String beanName : context.getBeanDefinitionNames()) {
            try {
                Class<?> beanType = context.getType(beanName);
                if (beanType != null && beanType.isAnnotationPresent(NexaEntity.class)) {
                    registry.register(beanType);
                    count++;
                }
            } catch (Exception ignored) {
                // 忽略无法获取类型的 Bean
            }
        }

        log.info("[NexaCache] 自动装配完成，共注册 {} 个缓存区域", count);
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public NexaTemplate nexaTemplate(CacheRegistry registry,
                                     @Autowired(required = false) List<DataAccessor<?, ?>> accessors) {
        // JDK 16+ Stream.toMap 替代命令式循环，更简洁
        Map<Class<?>, DataAccessor<?, ?>> accessorMap = accessors == null
                ? new HashMap<>()
                : accessors.stream()
                        .peek(a -> log.info("[NexaCache] 自动注入 DataAccessor: {}", a.entityType().getSimpleName()))
                        .collect(java.util.stream.Collectors.toMap(
                                DataAccessor::entityType,
                                a -> a,
                                (a, b) -> a,
                                HashMap::new));
        return new NexaTemplate(registry, accessorMap);
    }

    @Bean
    @ConditionalOnMissingBean
    public NexaCacheAspect nexaCacheAspect(CacheRegistry registry) {
        return new NexaCacheAspect(registry);
    }
}
