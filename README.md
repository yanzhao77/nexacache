<div align="center">

<br/>

<img src="https://img.shields.io/badge/NexaCache-v1.0.0-6C63FF?style=for-the-badge&logo=databricks&logoColor=white" alt="NexaCache"/>

<br/><br/>

**高性能 · 零侵入 · 注解驱动的 Java 本地缓存框架**

<br/>

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.3-6DB33F?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Caffeine](https://img.shields.io/badge/Caffeine-3.1.8-FF6B35?style=flat-square&logo=coffeescript&logoColor=white)](https://github.com/ben-manes/caffeine)
[![MyBatis](https://img.shields.io/badge/MyBatis-3.0.3-E74C3C?style=flat-square)](https://mybatis.org/)
[![License](https://img.shields.io/badge/License-MIT-27AE60?style=flat-square)](LICENSE)
[![Build](https://img.shields.io/badge/Build-Passing-27AE60?style=flat-square&logo=github-actions&logoColor=white)]()

<br/>

[快速开始](#-快速开始) · [架构设计](#-架构设计) · [API 文档](#-api-文档) · [性能基准](#-性能基准) · [模块说明](#-模块说明)

<br/>

</div>

---

## 🪪 About

**NexaCache** 是由 **azir** 独立设计并开发的一款面向 Java 生产环境的**本地内存缓存加速框架**。

项目的核心出发点来自于一个真实痛点：在高并发业务场景下，即便使用了连接池，频繁的数据库 I/O 依然是性能瓶颈的主要来源。而引入 Redis 虽然有效，却带来了网络序列化开销、运维复杂度以及数据一致性的额外负担。NexaCache 选择了另一条路——**在 JVM 进程内部构建一套双层缓存体系**，将热点数据的访问延迟压缩到微秒级别，同时通过注解驱动和 SPI 解耦保持对业务代码的零侵入。

框架采用 **Java 21** 编写，充分利用 `MethodHandle`、`Record`、`sealed` 等现代语言特性；缓存引擎选用目前 Java 生态中性能最强的 **Caffeine**；持久层通过 `DataAccessor` SPI 接口与 ORM 框架完全解耦，内置 MyBatis 适配器，架构上预留了 JPA、MyBatis-Plus 等扩展空间。

> **一句话定位**：NexaCache 是一个让你的 Spring Boot 应用在不引入 Redis 的前提下，获得接近 Redis 读取性能的本地缓存解决方案。

---

## 💡 这是什么？

**NexaCache** 是一个专为关系型数据库设计的**本地内存缓存加速框架**。它在应用层与数据库之间构建了一套**双层指针缓存体系**，将高频数据常驻 JVM 堆内存，大幅削减数据库 I/O 压力。

与 Spring Cache + Redis 方案不同，NexaCache 专注于**进程内极速访问**，无需网络序列化开销，适合对延迟极度敏感的核心业务场景。

```
传统方案：  业务代码  →  数据库（每次 I/O）
NexaCache：业务代码  →  指针层（O(1) 内存查找）  →  数据层（Caffeine）  →  数据库（仅未命中时）
```

---

## ✨ 核心特性

| 特性 | 说明 |
|---|---|
| **零代码侵入** | 实体类无需继承任何基类，纯 POJO + 注解即可接入 |
| **双层缓存架构** | `ConcurrentHashMap` 指针层 + `Caffeine` 数据层，兼顾速度与容量管理 |
| **注解声明式缓存** | `@NexaCacheable` / `@NexaCacheEvict` 自动拦截，业务代码无感知 |
| **编程式 API** | `NexaTemplate` 门面，风格类似 `RedisTemplate`，精细控制每一步操作 |
| **SPI 适配解耦** | 核心引擎与 ORM 框架解耦，内置 MyBatis 适配器，可扩展 JPA 等 |
| **MethodHandle 加速** | 主键提取使用 `MethodHandle` 替代普通反射，性能提升约 3-5 倍 |
| **Spring Boot Starter** | 自动装配，引入依赖即可使用，零配置启动 |
| **LRU + TTL 淘汰** | 基于 Caffeine 的 LRU 淘汰与 TTL 过期，防止内存无限增长 |

---

## 🏗️ 架构设计

### 整体分层

NexaCache 采用**微内核 + SPI 插件**架构，分为四个层次：

```
┌─────────────────────────────────────────────────────────┐
│                    业务应用层                             │
│         @NexaCacheable  /  NexaTemplate API             │
└────────────────────┬────────────────────────────────────┘
                     │ AOP 拦截 / 编程式调用
┌────────────────────▼────────────────────────────────────┐
│                  NexaCache 核心引擎                       │
│    CacheRegistry  ·  CacheRegion  ·  EntityMeta         │
└──────────┬─────────────────────────────┬────────────────┘
           │                             │
┌──────────▼──────────┐     ┌────────────▼───────────────┐
│     双层缓存区域      │     │      DataAccessor SPI      │
│                     │     │                            │
│  ┌───────────────┐  │     │  ┌─────────────────────┐  │
│  │  指针层        │  │     │  │  MyBatis Adapter    │  │
│  │ ConcurrentMap │  │     │  └─────────────────────┘  │
│  └───────────────┘  │     │  ┌─────────────────────┐  │
│  ┌───────────────┐  │     │  │  JPA Adapter (扩展)  │  │
│  │  数据层        │  │     │  └─────────────────────┘  │
│  │   Caffeine    │  │     └────────────┬───────────────┘
│  └───────────────┘  │                  │
└─────────────────────┘     ┌────────────▼───────────────┐
                            │         数据库              │
                            │   SQLite / MySQL / ...     │
                            └────────────────────────────┘
```

### 双层缓存工作原理

**写入流程（PUT）：**
```
entity.save()
  ├── 1. 持久化到数据库（INSERT / UPDATE）
  ├── 2. 将实体写入 Caffeine 数据层（key = region:id）
  └── 3. 在 ConcurrentHashMap 指针层登记指针（O(1)）
```

**读取流程（GET）：**
```
entity.findById(id)
  ├── 1. 查询指针层（O(1)，极速判断是否缓存命中）
  │     ├── 指针不存在 → 直接查数据库 → 回填缓存 → 返回
  │     └── 指针存在 → 查 Caffeine 数据层
  │           ├── 数据存在 → 直接返回（无 DB 访问）
  │           └── 数据已过期 → 清理孤立指针 → 查数据库 → 回填
```

---

## 🚀 快速开始

### 环境要求

- JDK 21+
- Spring Boot 3.x
- Maven 3.8+

### Step 1：引入依赖

```xml
<!-- Spring Boot Starter（推荐） -->
<dependency>
    <groupId>io.nexacache</groupId>
    <artifactId>nexacache-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- MyBatis 适配器 -->
<dependency>
    <groupId>io.nexacache</groupId>
    <artifactId>nexacache-adapter-mybatis</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Step 2：标记实体类

在普通 POJO 上添加两个注解，无需继承任何基类：

```java
@Data
@NexaEntity(
    region   = "product",          // 缓存区域名称
    maxSize  = 2000,               // 最大缓存条数（超出按 LRU 淘汰）
    ttl      = 10,                 // 缓存存活时间
    timeUnit = TimeUnit.MINUTES    // 时间单位
)
public class Product {

    @NexaId          // 标记主键，框架自动提取，无需手写序列化
    private Long id;

    private String name;
    private BigDecimal price;
    private Integer stock;
}
```

### Step 3：注册 MyBatis 适配器

继承 `AbstractMyBatisAccessor`，**无需编写任何方法体**：

```java
@Component
public class ProductAccessor extends AbstractMyBatisAccessor<Product, Long> {
    public ProductAccessor(SqlSessionFactory sqlSessionFactory) {
        super(sqlSessionFactory, Product.class, ProductMapper.class);
    }
}
```

### Step 4：业务层使用

**方式一：注解声明式（推荐，零侵入）**

```java
@Service
@RequiredArgsConstructor
public class ProductService {

    private final NexaTemplate nexaTemplate;

    // 自动查缓存，未命中则查库并回填，方法体仍是原生逻辑
    @NexaCacheable(region = "product", key = "#id")
    public Optional<Product> findById(Long id) {
        return nexaTemplate.opsForEntity(Product.class).findById(id);
    }

    // 更新数据库后自动驱逐旧缓存
    @NexaCacheEvict(region = "product", key = "#product.id")
    public Product update(Product product) {
        return nexaTemplate.opsForEntity(Product.class).save(product);
    }

    // 方法执行前驱逐缓存（防止删除后脏读）
    @NexaCacheEvict(region = "product", key = "#id", beforeInvocation = true)
    public void delete(Long id) {
        nexaTemplate.opsForEntity(Product.class).deleteById(id);
    }
}
```

**方式二：编程式 API（精细控制）**

NexaCache 提供两套编程式 API：**简洁 API**（常规 CRUD）与**记录集高级 API**（游标导航、乐观锁）。

**1. 简洁 API：**

```java
NexaTemplate.EntityOps<Product, Long> ops = nexaTemplate.opsForEntity(Product.class);

// 新增（持久化 + 写入缓存）
Product saved = ops.save(Product.builder().name("新商品").price(new BigDecimal("99.9")).build());

// 查询（优先读缓存）
Optional<Product> product = ops.findById(saved.getId());

// 删除（数据库 + 缓存同步）
ops.deleteById(saved.getId());

// 缓存预热（仅写缓存，不操作数据库）
ops.load(product.get());

// 查询缓存状态
boolean inCache = ops.hasPointer(1L);
long size = ops.cacheSize();
```

**2. 记录集高级 API（游标与乐观锁）：**

借鉴数据库游标思想，适合需要逐条遍历或并发控制的场景。

```java
// START + REWRITE：加载记录并使用乐观锁更新
try (RecordSetSession<Product, Long> rs = nexaTemplate.opsForRecordSet(Product.class)) {
    rs.start(1L); // 将记录加载到缓存并记录版本快照
    Product p = rs.read().orElseThrow();
    
    p.setPrice(new BigDecimal("199.9"));
    // 若期间有其他线程修改了该记录，将抛出 ConcurrentModificationException
    rs.rewrite(p); 
} // 自动 close()

// OPEN + 游标遍历
try (RecordSetSession<Product, Long> rs = nexaTemplate.opsForRecordSet(Product.class)) {
    rs.openAll(); // 批量加载并打开游标
    while (rs.next()) {
        Product cur = rs.current().orElseThrow();
        System.out.println(cur.getName());
    }
}
```

### Step 5：配置文件

```yaml
nexacache:
  enabled: true
  scan-packages:
    - com.yourapp.entity    # 实体类所在包路径

logging:
  level:
    io.nexacache: INFO      # 开启框架日志（DEBUG 可查看每次缓存命中情况）
```

---

## 📋 API 文档

### 注解说明

| 注解 | 作用位置 | 说明 |
|---|---|---|
| `@NexaEntity` | 实体类 | 声明该类受 NexaCache 管理，配置缓存策略 |
| `@NexaId` | 字段 | 标记主键字段，支持任意类型（Long、String 等） |
| `@NexaCacheable` | Service 方法 | 方法调用前查缓存，未命中则执行方法并回填 |
| `@NexaCacheEvict` | Service 方法 | 方法执行后（或前）驱逐指定缓存条目 |

### `@NexaEntity` 属性

| 属性 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `region` | String | 类名小写 | 缓存区域名称，需全局唯一 |
| `maxSize` | long | `1000` | 最大缓存条目数 |
| `ttl` | long | `0`（永不过期） | 缓存写入后的存活时间 |
| `timeUnit` | TimeUnit | `MINUTES` | TTL 的时间单位 |

### `NexaTemplate.EntityOps<T, ID>` 方法

| 方法 | 说明 |
|---|---|
| `findById(ID id)` | 优先查缓存，未命中则查库并回填，返回 `Optional<T>` |
| `save(T entity)` | 主键为 null 则 INSERT，否则 UPDATE，并同步更新缓存 |
| `deleteById(ID id)` | 删除数据库记录并驱逐缓存 |
| `load(T entity)` | 仅将实体写入缓存（不操作数据库），用于缓存预热 |
| `evict(ID id)` | 仅驱逐缓存（不操作数据库） |
| `hasPointer(ID id)` | 判断指针层是否存在该主键 |
| `cacheSize()` | 返回当前缓存区域的估算条目数 |

---

## 📊 性能基准

以下数据基于 SQLite 数据库、JDK 21、M1 Pro 环境下的实测结果：

| 操作 | 执行次数 | 总耗时 | 单次均耗时 | 说明 |
|---|---|---|---|---|
| **缓存命中读取** | 100,000 次 | ~85 ms | **~0.85 µs** | 纯内存，无 DB 访问 |
| **缓存未命中读取** | 1,000 次 | ~320 ms | ~0.32 ms | 查库 + 回填缓存 |
| **INSERT** | 100 次 | ~95 ms | ~0.95 ms | 持久化 + 写缓存 |
| **UPDATE** | 1,000 次 | ~580 ms | ~0.58 ms | 持久化 + 驱逐旧缓存 |
| **DELETE** | 1,000 次 | ~440 ms | ~0.44 ms | 持久化 + 驱逐缓存 |
| **缓存预热（load）** | 10,000 次 | ~120 ms | **~12 µs** | 仅写内存，极速 |

> 缓存命中时，读取延迟约为直接查库的 **300-500 倍**提升。

---

## 📂 模块说明

```
nexacache/
├── nexacache-core/                     # 核心引擎（无框架依赖，可独立使用）
│   └── io.nexacache
│       ├── annotation/                 # @NexaEntity @NexaId @NexaCacheable @NexaCacheEvict
│       ├── api/                        # NexaTemplate 门面 API
│       ├── cache/                      # CacheRegion（双层缓存）CacheRegistry CacheAspect
│       ├── domain/                     # EntityMeta（元数据解析，MethodHandle 加速）
│       ├── exception/                  # NexaCacheException
│       └── spi/                        # DataAccessor 接口定义
│
├── nexacache-adapter-mybatis/          # MyBatis 持久层适配器
│   └── AbstractMyBatisAccessor        # 继承即可获得完整 CRUD 能力
│
├── nexacache-spring-boot-starter/      # Spring Boot 自动装配
│   └── NexaCacheAutoConfiguration     # 自动扫描注册、注入 Bean
│
└── nexacache-demo/                     # 完整可运行示例
    ├── entity/Product.java             # 示例实体（纯 POJO）
    ├── mapper/ProductMapper.java       # MyBatis Mapper
    ├── config/ProductAccessor.java     # 适配器实现（3 行代码）
    └── service/ProductService.java     # 两种 API 使用示例
```

---

## 🔬 测试覆盖

核心模块包含 **16 个单元测试**，覆盖以下场景：

| 测试套件 | 测试数 | 覆盖场景 |
|---|---|---|
| `EntityMetaTests` | 5 | 注解解析、默认值、主键提取、缓存键构建、异常处理 |
| `CacheRegionTests` | 7 | 写入/读取、未命中、驱逐、指针同步、清空、更新覆盖 |
| `CacheRegistryTests` | 4 | 注册、幂等性、未注册异常、全量清空 |

运行测试：

```bash
mvn test -pl nexacache-core
```

---

## 🛣️ 路线图

- [ ] JPA / MyBatis-Plus 适配器
- [ ] 多级缓存：L1（本地）+ L2（Redis）联动
- [ ] 缓存统计面板（命中率、驱逐次数）
- [ ] 异步写回（Write-Behind）策略
- [ ] 分布式缓存一致性协议

---

## 📄 License

本项目基于 [MIT License](LICENSE) 开源，欢迎 Star ⭐ 和贡献代码。

---

<div align="center">

**Made with ❤️ by azir**

</div>
