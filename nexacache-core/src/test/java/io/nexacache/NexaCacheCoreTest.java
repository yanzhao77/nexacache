package io.nexacache;

import io.nexacache.annotation.NexaEntity;
import io.nexacache.annotation.NexaId;
import io.nexacache.cache.CacheRegion;
import io.nexacache.cache.CacheRegistry;
import io.nexacache.domain.EntityMeta;
import io.nexacache.exception.NexaCacheException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.*;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NexaCache 核心引擎单元测试。
 * 测试范围：EntityMeta 解析、CacheRegion 双层缓存、CacheRegistry 注册表。
 *
 * @author azir
 */
@DisplayName("NexaCache 核心引擎测试")
class NexaCacheCoreTest {

    // ===================== 测试实体 =====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @NexaEntity(region = "test_product", maxSize = 100, ttl = 5, timeUnit = TimeUnit.MINUTES)
    static class TestProduct {
        @NexaId
        private Long id;
        private String name;
        private double price;
    }

    @Data
    @NexaEntity  // 使用默认 region（类名小写）
    static class SimpleItem {
        @NexaId
        private Integer id;
        private String label;
    }

    // ===================== EntityMeta 测试 =====================

    @Nested
    @DisplayName("EntityMeta 元数据解析")
    class EntityMetaTests {

        @Test
        @DisplayName("正确解析 @NexaEntity 注解属性")
        void shouldParseNexaEntityAnnotation() {
            EntityMeta<TestProduct> meta = EntityMeta.of(TestProduct.class);
            assertEquals("test_product", meta.getRegion());
            assertEquals(100L, meta.getMaxSize());
            assertEquals(5L, meta.getTtl());
            assertEquals(TimeUnit.MINUTES, meta.getTimeUnit());
        }

        @Test
        @DisplayName("未指定 region 时，默认使用类名小写")
        void shouldUseClassNameAsDefaultRegion() {
            EntityMeta<SimpleItem> meta = EntityMeta.of(SimpleItem.class);
            assertEquals("simpleitem", meta.getRegion());
        }

        @Test
        @DisplayName("正确提取实体主键值")
        void shouldExtractIdFromEntity() {
            EntityMeta<TestProduct> meta = EntityMeta.of(TestProduct.class);
            TestProduct product = TestProduct.builder().id(42L).name("测试商品").build();
            Object id = meta.extractId(product);
            assertEquals(42L, id);
        }

        @Test
        @DisplayName("正确构建缓存键")
        void shouldBuildCacheKey() {
            EntityMeta<TestProduct> meta = EntityMeta.of(TestProduct.class);
            String key = meta.buildCacheKey(99L);
            assertEquals("test_product:99", key);
        }

        @Test
        @DisplayName("实体类缺少 @NexaEntity 时应抛出异常")
        void shouldThrowWhenMissingNexaEntity() {
            assertThrows(NexaCacheException.class, () -> EntityMeta.of(String.class));
        }
    }

    // ===================== CacheRegion 测试 =====================

    @Nested
    @DisplayName("CacheRegion 双层缓存")
    class CacheRegionTests {

        private CacheRegion<TestProduct, Long> region;

        @BeforeEach
        void setUp() {
            EntityMeta<TestProduct> meta = EntityMeta.of(TestProduct.class);
            region = new CacheRegion<>(meta);
        }

        @Test
        @DisplayName("写入后应能从缓存读取")
        void shouldGetAfterPut() {
            TestProduct product = TestProduct.builder().id(1L).name("商品A").price(99.9).build();
            region.put(product);

            Optional<TestProduct> result = region.get(1L);
            assertTrue(result.isPresent());
            assertEquals("商品A", result.get().getName());
        }

        @Test
        @DisplayName("未写入的 ID 应返回 empty")
        void shouldReturnEmptyForMissingKey() {
            Optional<TestProduct> result = region.get(999L);
            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("驱逐后应无法读取")
        void shouldNotGetAfterEvict() {
            TestProduct product = TestProduct.builder().id(2L).name("商品B").build();
            region.put(product);
            assertTrue(region.get(2L).isPresent());

            region.evict(2L);
            assertFalse(region.get(2L).isPresent());
        }

        @Test
        @DisplayName("写入后指针层应存在指针")
        void shouldHavePointerAfterPut() {
            TestProduct product = TestProduct.builder().id(3L).name("商品C").build();
            region.put(product);
            assertTrue(region.hasPointer(3L));
        }

        @Test
        @DisplayName("驱逐后指针层应同步清除")
        void shouldClearPointerAfterEvict() {
            TestProduct product = TestProduct.builder().id(4L).name("商品D").build();
            region.put(product);
            region.evict(4L);
            assertFalse(region.hasPointer(4L));
        }

        @Test
        @DisplayName("clear() 后缓存应为空")
        void shouldBeEmptyAfterClear() {
            for (long i = 1; i <= 5; i++) {
                region.put(TestProduct.builder().id(i).name("商品" + i).build());
            }
            region.clear();
            for (long i = 1; i <= 5; i++) {
                assertFalse(region.get(i).isPresent());
            }
        }

        @Test
        @DisplayName("更新实体后应读取到最新值")
        void shouldReturnUpdatedValue() {
            TestProduct product = TestProduct.builder().id(5L).name("旧名称").price(10.0).build();
            region.put(product);

            TestProduct updated = TestProduct.builder().id(5L).name("新名称").price(20.0).build();
            region.put(updated);

            Optional<TestProduct> result = region.get(5L);
            assertTrue(result.isPresent());
            assertEquals("新名称", result.get().getName());
            assertEquals(20.0, result.get().getPrice());
        }
    }

    // ===================== CacheRegistry 测试 =====================

    @Nested
    @DisplayName("CacheRegistry 注册表")
    class CacheRegistryTests {

        private CacheRegistry registry;

        @BeforeEach
        void setUp() {
            registry = new CacheRegistry();
        }

        @Test
        @DisplayName("注册实体后应能获取对应 CacheRegion")
        void shouldGetRegionAfterRegister() {
            registry.register(TestProduct.class);
            CacheRegion<TestProduct, Long> region = registry.getRegion("test_product");
            assertNotNull(region);
        }

        @Test
        @DisplayName("重复注册应保持幂等")
        void shouldBeIdempotentOnDuplicateRegister() {
            registry.register(TestProduct.class);
            registry.register(TestProduct.class); // 不应抛出异常
            assertEquals(1, registry.registeredRegions().size());
        }

        @Test
        @DisplayName("获取未注册的区域应抛出异常")
        void shouldThrowForUnregisteredRegion() {
            assertThrows(NexaCacheException.class, () -> registry.getRegion("nonexistent"));
        }

        @Test
        @DisplayName("clearAll() 后所有区域应为空")
        void shouldClearAllRegions() {
            registry.register(TestProduct.class);
            CacheRegion<TestProduct, Long> region = registry.getRegion("test_product");
            region.put(TestProduct.builder().id(1L).name("测试").build());

            registry.clearAll();
            assertFalse(region.get(1L).isPresent());
        }
    }
}
