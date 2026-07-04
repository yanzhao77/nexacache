package io.nexacache.cache;

import io.nexacache.annotation.NexaCacheEvict;
import io.nexacache.annotation.NexaCacheable;
import io.nexacache.exception.NexaCacheException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Optional;

/**
 * NexaCache AOP 拦截器，处理 {@link NexaCacheable} 和 {@link NexaCacheEvict} 注解。
 * 通过 Spring AOP 的环绕通知，在方法调用前后自动进行缓存读取与驱逐。
 *
 * @author azir
 * @since 1.0.0
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class NexaCacheAspect {

    private final CacheRegistry registry;
    private final ExpressionParser spelParser = new SpelExpressionParser();

    /**
     * 处理 @NexaCacheable：先查缓存，未命中则执行方法并回填。
     */
    @Around("@annotation(cacheable)")
    public Object aroundCacheable(ProceedingJoinPoint pjp, NexaCacheable cacheable) throws Throwable {
        String region = cacheable.region();
        Object cacheKey = evalSpel(cacheable.key(), pjp);

        CacheRegion<Object, Object> cacheRegion = registry.getRegion(region);
        Optional<Object> cached = cacheRegion.get(cacheKey);
        if (cached.isPresent()) {
            log.debug("[NexaCache] @NexaCacheable 命中: region={}, key={}", region, cacheKey);
            return cached.get();
        }

        // 未命中，执行原方法
        Object result = pjp.proceed();
        if (result != null) {
            cacheRegion.put(result);
            log.debug("[NexaCache] @NexaCacheable 回填: region={}, key={}", region, cacheKey);
        }
        return result;
    }

    /**
     * 处理 @NexaCacheEvict：根据 beforeInvocation 决定驱逐时机。
     */
    @Around("@annotation(evict)")
    public Object aroundEvict(ProceedingJoinPoint pjp, NexaCacheEvict evict) throws Throwable {
        String region = evict.region();
        Object cacheKey = evalSpel(evict.key(), pjp);
        CacheRegion<Object, Object> cacheRegion = registry.getRegion(region);

        if (evict.beforeInvocation()) {
            cacheRegion.evict(cacheKey);
            log.debug("[NexaCache] @NexaCacheEvict 前置驱逐: region={}, key={}", region, cacheKey);
        }

        Object result = pjp.proceed();

        if (!evict.beforeInvocation()) {
            cacheRegion.evict(cacheKey);
            log.debug("[NexaCache] @NexaCacheEvict 后置驱逐: region={}, key={}", region, cacheKey);
        }
        return result;
    }

    /**
     * 解析 SpEL 表达式，将方法参数绑定到上下文。
     */
    private Object evalSpel(String expression, ProceedingJoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = pjp.getArgs();

        EvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < parameters.length; i++) {
            context.setVariable(parameters[i].getName(), args[i]);
        }

        try {
            return spelParser.parseExpression(expression).getValue(context);
        } catch (Exception e) {
            throw new NexaCacheException("SpEL 表达式解析失败: [" + expression + "]", e);
        }
    }
}
