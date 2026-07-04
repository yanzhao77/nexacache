<div align="center">

<br/>

<img src="https://img.shields.io/badge/NexaCache-v2.0.0-6C63FF?style=for-the-badge&logo=databricks&logoColor=white" alt="NexaCache"/>

<br/><br/>

**高性能 · 零侵入 · 注解驱动的 Java 本地缓存框架**

<br/>

[![Java](https://img.shields.io/badge/Java-25-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/25/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.3-6DB33F?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring Framework](https://img.shields.io/badge/Spring%20Framework-6.2.x-6DB33F?style=flat-square&logo=spring&logoColor=white)](https://spring.io/projects/spring-framework)
[![Caffeine](https://img.shields.io/badge/Caffeine-3.2.0-FF6B35?style=flat-square&logo=coffeescript&logoColor=white)](https://github.com/ben-manes/caffeine)
[![MyBatis](https://img.shields.io/badge/MyBatis-3.0.4-E74C3C?style=flat-square)](https://mybatis.org/)
[![License](https://img.shields.io/badge/License-MIT-27AE60?style=flat-square)](LICENSE)
[![Build](https://img.shields.io/badge/Build-Passing-27AE60?style=flat-square&logo=github-actions&logoColor=white)]()
[![Tests](https://img.shields.io/badge/Tests-55%2F55%20Passing-27AE60?style=flat-square&logo=junit5&logoColor=white)]()
[![GitHub Packages](https://img.shields.io/badge/GitHub%20Packages-Published-2EA44F?style=flat-square&logo=github&logoColor=white)](https://github.com/yanzhao77/nexacache/packages)

<br/>

[快速开始](#-快速开始) · [架构设计](#-架构设计) · [JDK 25 新特性](#-jdk-25-新特性-v200) · [API 文档](#-api-文档) · [性能基准](#-性能基准) · [升级报告](./doc/NexaCache_v2.0.0_Upgrade_Report.md)

<br/>

</div>

---

## 🪪 About

**NexaCache** 是一个面向 Spring Boot 应用的**进程内本地缓存框架**，在应用层与数据库之间构建双层指针缓存体系，将高频访问数据常驻 JVM 堆内存，缓存命中时读取延迟可达微秒级，显著降低数据库 I/O 压力。

与引入 Redis 相比，NexaCache 无需额外部署任何外部服务，没有网络序列化开销，适合对延迟极度敏感、希望保持架构简洁的单机或小规模集群场景。

**v2.0.0 核心技术特点：**
- **全面拥抱 JDK 25**：引入 Record、Virtual Threads、Stream Gatherers、Pattern Matching 等最新语言特性
- **Spring Boot 3.5.3 + Spring Framework 6.2.x**：全栈升级至最新稳定版，获得最新安全补丁与性能优化
- 缓存引擎基于 [Caffeine 3.2.0](https://github.com/ben-manes/caffeine)，支持 LRU 淘汰与 TTL 过期
- 持久层通过 `DataAccessor` SPI 接口与 ORM 完全解耦，内置 MyBatis 适配器
- 支持注解声明式（`@NexaCacheable` / `@NexaCacheEvict`）和编程式（`NexaTemplate`）两套 API
- 高级记录集 API 支持数据库游标风格的逐条操作与无侵入乐观锁
- 提供 Spring Boot Starter，引入依赖即可开箱使用

---

## 🚀 JDK 25 新特性 (v2.0.0)

NexaCache v2.0.0 已全面重构，深度融合 JDK 25 的现代语言特性，带来更安全、更高效、更具表现力的开发体验：

| JDK 25 特性 | NexaCache 应用场景 | 带来的优势 |
|---|---|---|
| **Record Classes (JEP 395)** | `EntityMeta` 重构为 Record | 不可变数据载体，自动生成 equals/hashCode，内存占用更小 |
| **Virtual Threads (JEP 444)** | `CacheRegion.warmUp()` 并发预热 | 轻量级线程极大提升大批量数据预热时的吞吐量 |
| **Stream Gatherers (JEP 461)** | `CacheRegion.getAll()` 分批查询 | 使用 `windowFixed` 实现优雅的流式分批处理，替代传统 for 循环 |
| **Pattern Matching (JEP 394)** | `AbstractMyBatisAccessor` 方法调用 | 简化类型检查与强转，使反射调用更加安全简洁 |
| **Sealed Classes (JEP 409)** | `NexaCacheException` 异常体系 | 限制异常子类，配合 switch 表达式实现穷尽的异常处理 |
| **Switch Expressions (JEP 361)** | 方法路由与异常处理 | 替代繁琐的 if-else 链，代码逻辑更清晰 |
| **Text Blocks (JEP 378)** | 日志格式化与统计快照 | 多行字符串拼接更直观，告别 `\n` 和 `+` 号拼接 |

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

- **JDK 25**
- **Spring Boot 3.5.3+**
- Maven 3.9+

### Step 1：引入依赖

NexaCache 已发布到 **GitHub Packages**，使用前需要在 `~/.m2/settings.xml` 中配置认证：

```xml
<settings>
  <servers>
    <server>
      <id>github-nexacache</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>YOUR_GITHUB_TOKEN</password>  <!-- 需要 read:packages 权限 -->
    </server>
  </servers>
</settings>
```

在项目 `pom.xml` 中添加仓库地址：

```xml
<repositories>
  <repository>
    <id>github-nexacache</id>
    <name>NexaCache GitHub Packages</name>
    <url>https://maven.pkg.github.com/yanzhao77/nexacache</url>
  </repository>
</repositories>
```

然后引入依赖：

```xml
<!-- Spring Boot Starter（推荐，包含自动装配） -->
<dependency>
    <groupId>io.nexacache</groupId>
    <artifactId>nexacache-spring-boot-starter</artifactId>
    <version>2.0.0</version>
</dependency>

<!-- MyBatis 适配器 -->
<dependency>
    <groupId>io.nexacache</groupId>
    <artifactId>nexacache-adapter-mybatis</artifactId>
    <version>2.0.0</version>
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

### `NexaTemplate.EntityOps<T, ID>` 方法（简洁 API）

| 方法 | 说明 |
|---|---|
| `findById(ID id)` | 优先查缓存，未命中则查库并回填，返回 `Optional<T>` |
| `save(T entity)` | 主键为 null 则 INSERT，否则 UPDATE，并同步更新缓存 |
| `deleteById(ID id)` | 删除数据库记录，并同步驱逐缓存 |
| `load(T entity)` | 仅将实体写入缓存（不操作数据库），用于缓存预热 |
| `evict(ID id)` | 仅驱逐缓存（不操作数据库） |
| `hasPointer(ID id)` | 判断指针层是否存在该主键 |
| `cacheSize()` | 返回当前缓存区域的估算条目数 |

### `RecordSetSession<T, ID>` 方法（记录集高级 API）

通过 `nexaTemplate.opsForRecordSet(Entity.class)` 获取，实现 `AutoCloseable`，推荐配合 `try-with-resources` 使用。

| 方法 | 操作语义 | 说明 |
|---|---|---|
| `start(ID id)` | START | 将单条记录加载到缓存并记录版本快照（乐观锁基准） |
| `open(List<T>)` | OPEN | 批量加载指定记录列表并打开游标 |
| `openAll()` | OPEN ALL | 从数据库查询全部记录并打开游标 |
| `read()` | READ | 读取 `start()` 加载的单条记录 |
| `current()` | CURRENT | 读取游标当前指向的记录 |
| `next()` | FETCH NEXT | 游标前移，返回是否移动成功 |
| `prev()` | FETCH PRIOR | 游标后移，返回是否移动成功 |
| `first()` | FETCH FIRST | 游标移到第一条 |
| `last()` | FETCH LAST | 游标移到最后一条 |
| `write(T entity)` | WRITE | 新增记录（持久化 + 写缓存） |
| `rewrite(T entity)` | REWRITE | 更新记录（含乐观锁校验，冲突时抛 `ConcurrentModificationException`） |
| `delete()` | DELETE | 删除游标当前记录（数据库 + 缓存同步） |
| `deleteById(ID id)` | DELETE BY ID | 按主键删除记录 |
| `size()` | — | 返回当前记录集条目数 |
| `state()` | — | 返回游标状态（`IDLE/READY/OPEN/EOF/CLOSED`） |
| `close()` | CLOSE | 关闭记录集，释放游标资源 |

---

## 📊 性能基准

以下数据基于 SQLite 数据库、JDK 25、M1 Pro 环境下的实测结果：

| 操作 | 执行次数 | 总耗时 | 单次均耗时 | 说明 |
|---|---|---|---|---|
| **缓存命中读取** | 100,000 次 | ~82 ms | **~0.82 µs** | 纯内存，无 DB 访问 |
| **缓存未命中读取** | 1,000 次 | ~295 ms | ~0.30 ms | 查库 + 回填缓存 |
| **INSERT** | 100 次 | ~92 ms | ~0.92 ms | 持久化 + 写缓存 |
| **UPDATE** | 1,000 次 | ~550 ms | ~0.55 ms | 持久化 + 驱逐旧缓存 |
| **DELETE** | 1,000 次 | ~415 ms | ~0.42 ms | 持久化 + 驱逐缓存 |
| **缓存预热（load）** | 10,000 次 | ~120 ms | **~12 µs** | 虚拟线程并发预热，极速 |

> 缓存命中时，读取延迟约为直接查库的 **300-500 倍**提升。相比 v1.1.0（JDK 21），v2.0.0 在 JDK 25 下各项操作平均性能提升约 **13%**。

---

## 📂 模块说明

```
nexacache/
├── nexacache-core/                     # 核心引擎（无框架依赖，可独立使用）
│   └── io.nexacache
│       ├── annotation/                 # @NexaEntity @NexaId @NexaCacheable @NexaCacheEvict
│       ├── api/                        # NexaTemplate 门面 API
│       ├── cache/                      # CacheRegion（双层缓存）CacheRegistry CacheAspect
│       ├── domain/                     # EntityMeta（Record 类，MethodHandle 加速）
│       ├── exception/                  # NexaCacheException（Sealed Classes 体系）
│       └── spi/                        # DataAccessor 接口定义
│
├── nexacache-adapter-mybatis/          # MyBatis 持久层适配器
│   └── AbstractMyBatisAccessor        # 继承即可获得完整 CRUD 能力
│
├── nexacache-spring-boot-starter/      # Spring Boot 自动装配
│   └── NexaCacheAutoConfiguration     # 自动扫描注册、注入 Bean
│
├── nexacache-demo/                     # 完整可运行示例
│   ├── entity/Product.java             # 示例实体（纯 POJO）
│   ├── mapper/ProductMapper.java       # MyBatis Mapper
│   ├── config/ProductAccessor.java     # 适配器实现（3 行代码）
│   └── service/ProductService.java     # 两种 API 使用示例
│
└── doc/                                # 项目文档
    ├── NexaCache_v2.0.0_Upgrade_Report.md   # v2.0.0 升级亮点与技术细节报告
    ├── NexaCache_v2.0.0_Upgrade_Report.pdf  # PDF 版本
    └── images/                              # 报告配套数据可视化图表（7 张）
```

---

## 🔬 测试覆盖

核心模块与 Demo 模块包含 **55 个测试用例**，全部通过，覆盖以下场景：

| 测试套件 | 测试数 | 覆盖场景 |
|---|---|---|
| `EntityMetaTests` | 5 | 注解解析、默认值、主键提取、缓存键构建、异常处理 |
| `CacheRegionTests` | 7 | 写入/读取、未命中、驱逐、指针同步、清空、更新覆盖 |
| `CacheRegistryTests` | 4 | 注册、幂等性、未注册异常、全量清空 |
| `RecordSetSessionTest` | 19 | START 加载、游标导航、乐观锁、读写操作、异常处理 |
| `ProductServiceTest` | 20 | Spring Boot 集成、注解驱动、CRUD、缓存预热、统计信息 |

运行测试：

```bash
mvn test
```

---

## 📦 版本历史

| 版本 | 发布日期 | 主要变更 |
|---|---|---|
| **v2.0.0** | 2026-07-05 | JDK 25 全面升级、Spring Boot 3.5.3、7 大 JDK 25 新特性应用、Bug 修复 |
| v1.1.0 | 2024-xx-xx | 记录集高级 API、游标导航、乐观锁支持 |
| v1.0.0 | 2024-xx-xx | 初始版本，双层缓存架构、注解驱动、MyBatis 适配器 |

> 详细升级说明请查阅 [doc/NexaCache_v2.0.0_Upgrade_Report.md](./doc/NexaCache_v2.0.0_Upgrade_Report.md)

---

## 🛣️ 路线图

- [ ] Spring Boot 4.x / Spring Framework 7 适配（探索分支）
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
