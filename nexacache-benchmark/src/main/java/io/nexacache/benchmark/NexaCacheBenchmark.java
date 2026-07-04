package io.nexacache.benchmark;

import io.nexacache.annotation.NexaEntity;
import io.nexacache.annotation.NexaId;
import io.nexacache.cache.CacheRegion;
import io.nexacache.cache.CacheRegistry;
import io.nexacache.domain.EntityMeta;
import io.nexacache.recordset.Cursor;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * NexaCache 关键路径 JMH 基准测试。
 *
 * <p>本测试覆盖本次修复涉及的所有关键路径，量化每处变更对性能的实际影响：
 * <ul>
 *   <li>Fix-1: current() 缓存命中 vs 缓存未命中（补查路径）</li>
 *   <li>Fix-2: Cursor 构造与 position 初始化</li>
 *   <li>Fix-6: buildCacheKey 生成（Integer vs Long id）</li>
 *   <li>Fix-7: NexaCacheAspect instanceof Optional 检查</li>
 *   <li>综合: CacheRegion put/get/evict 全链路</li>
 * </ul>
 *
 * @author azir
 * @since 1.1.0
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class NexaCacheBenchmark {

    // ===================== 测试实体 =====================

    @NexaEntity(region = "product")
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Product {
        @NexaId
        private Long id;
        private String name;
        private BigDecimal price;
        private int stock;
    }

    // ===================== 测试状态 =====================

    private CacheRegistry registry;
    private CacheRegion<Product, Long> region;
    private EntityMeta<Product> meta;

    // 预置数据
    private Product cachedProduct;       // 已在缓存中的实体
    private Product uncachedProduct;     // 不在缓存中的实体（模拟 miss）
    private List<Long> idList100;        // 100 条记录的 id 列表（游标遍历用）
    private List<Product> products100;   // 100 条记录（openAll 用）

    @Setup(Level.Trial)
    public void setUp() {
        registry = new CacheRegistry();
        registry.register(Product.class);
        region = registry.getRegion(Product.class);
        meta = EntityMeta.of(Product.class);

        // 预置 1 条缓存命中数据
        cachedProduct = new Product(1L, "MacBook Pro", new BigDecimal("12999.00"), 50);
        region.put(cachedProduct);

        // 未缓存数据（用于 miss 路径测试，不放入缓存）
        uncachedProduct = new Product(9999L, "Not Cached", new BigDecimal("1.00"), 0);

        // 预置 100 条数据（游标遍历测试用）
        idList100 = new ArrayList<>(100);
        products100 = new ArrayList<>(100);
        for (int i = 1; i <= 100; i++) {
            Product p = new Product((long) i, "Product-" + i, new BigDecimal(i * 10), i);
            products100.add(p);
            idList100.add((long) i);
            region.put(p);
        }
    }

    // ===================== Fix-1: current() 缓存命中路径 =====================

    /**
     * 基准：缓存命中时 region.get() 的延迟（修复前后热路径相同）。
     * 期望：< 200 ns（Caffeine 查询）
     */
    @Benchmark
    public void fix1_cacheHit(Blackhole bh) {
        Optional<Product> result = region.get(1L);
        bh.consume(result);
    }

    /**
     * 对比：缓存未命中时 region.get() 的延迟（修复前行为：直接返回 empty）。
     * 期望：< 100 ns（Caffeine miss 快速返回）
     */
    @Benchmark
    public void fix1_cacheMiss_regionOnly(Blackhole bh) {
        Optional<Product> result = region.get(9999L);
        bh.consume(result);
    }

    /**
     * 对比：模拟修复后 current() 补查路径的额外开销（不含实际 DB 调用，仅量化 Optional 链开销）。
     * 修复后新增：miss 时执行 flatMap 内的 lambda + ifPresent 回填
     */
    @Benchmark
    public void fix1_cacheMiss_withFallbackLogic(Blackhole bh) {
        Optional<Product> cached = region.get(9999L);
        // 模拟修复后的补查逻辑（不含 DB IO，量化纯框架开销）
        Optional<Product> result = cached.isPresent() ? cached : Optional.empty();
        result.ifPresent(region::put);
        bh.consume(result);
    }

    // ===================== Fix-2: Cursor 构造与 position 初始化 =====================

    /**
     * 基准：Cursor(List) 构造（position=0，修复后）。
     */
    @Benchmark
    public void fix2_cursorConstruct_list(Blackhole bh) {
        Cursor<Long> cursor = new Cursor<>(idList100);
        bh.consume(cursor);
    }

    /**
     * 基准：Cursor(ID) 构造（单记录，start 模式）。
     */
    @Benchmark
    public void fix2_cursorConstruct_single(Blackhole bh) {
        Cursor<Long> cursor = new Cursor<>(1L);
        bh.consume(cursor);
    }

    /**
     * 基准：游标遍历 100 条记录（next + currentId）。
     */
    @Benchmark
    public void fix2_cursorIterate100(Blackhole bh) {
        Cursor<Long> cursor = new Cursor<>(idList100);
        // position=0，直接读第一条
        bh.consume(cursor.currentId());
        // 遍历剩余 99 条
        while (cursor.next()) {
            bh.consume(cursor.currentId());
        }
    }

    // ===================== Fix-6: buildCacheKey（Integer vs Long）=====================

    /**
     * 基准：Long 类型 id 的 buildCacheKey（正常路径）。
     */
    @Benchmark
    public void fix6_buildCacheKey_long(Blackhole bh) {
        String key = meta.buildCacheKey(1L);
        bh.consume(key);
    }

    /**
     * 对比：Integer 类型 id 的 buildCacheKey（SQLite 回填场景）。
     * 修复后 toString() 统一，两者结果相同。
     */
    @Benchmark
    public void fix6_buildCacheKey_integer(Blackhole bh) {
        String key = meta.buildCacheKey(1);
        bh.consume(key);
    }

    // ===================== Fix-7: instanceof Optional 检查 =====================

    /**
     * 基准：修复后 AOP 回填时的 instanceof 检查（非 Optional 返回值，最常见路径）。
     */
    @Benchmark
    public void fix7_instanceofCheck_notOptional(Blackhole bh) {
        Object result = cachedProduct;  // 模拟方法返回 T（非 Optional）
        boolean isOptional = result instanceof Optional;
        Object toCache = isOptional ? ((Optional<?>) result).orElse(null) : result;
        bh.consume(toCache);
    }

    /**
     * 对比：修复后 AOP 回填时的 instanceof 检查（Optional<T> 返回值）。
     */
    @Benchmark
    public void fix7_instanceofCheck_isOptional(Blackhole bh) {
        Object result = Optional.of(cachedProduct);  // 模拟方法返回 Optional<T>
        boolean isOptional = result instanceof Optional;
        Object toCache = isOptional ? ((Optional<?>) result).orElse(null) : result;
        bh.consume(toCache);
    }

    // ===================== 综合路径：CacheRegion put/get/evict =====================

    /**
     * 综合基准：put → get → evict 全链路（模拟一次完整缓存生命周期）。
     */
    @Benchmark
    public void comprehensive_putGetEvict(Blackhole bh) {
        Product p = new Product(88888L, "Bench", new BigDecimal("99.9"), 10);
        region.put(p);
        bh.consume(region.get(88888L));
        region.evict(88888L);
    }

    /**
     * 综合基准：100 条记录批量 put（模拟 openAll 预热）。
     */
    @Benchmark
    public void comprehensive_batchPut100(Blackhole bh) {
        for (Product p : products100) {
            region.put(p);
        }
        bh.consume(region.size());
    }

    // ===================== main 入口 =====================

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(NexaCacheBenchmark.class.getSimpleName())
                .warmupIterations(3)
                .warmupTime(TimeValue.seconds(1))
                .measurementIterations(5)
                .measurementTime(TimeValue.seconds(1))
                .forks(1)
                .build();
        new Runner(opt).run();
    }
}
