## 🎉 NexaCache v1.0.0 — 首个正式版本

这是 NexaCache 的第一个正式发布版本，包含完整的核心框架、MyBatis 适配器、Spring Boot Starter 以及可运行的 Demo 示例。

---

### ✨ 本版本特性

**核心引擎（nexacache-core）**

- 注解驱动接入：`@NexaEntity`、`@NexaId`、`@NexaCacheable`、`@NexaCacheEvict`
- 双层缓存架构：`ConcurrentHashMap` 指针层 + `Caffeine` 数据层，缓存命中延迟约 0.85µs
- `EntityMeta` 元数据解析器：使用 `MethodHandle` 替代普通反射，主键提取性能提升 3-5 倍
- `CacheRegistry` 全局注册表：管理所有缓存区域的生命周期
- `NexaTemplate` 编程式门面 API：支持 `findById`、`save`、`deleteById`、`load`、`evict` 等操作
- `NexaCacheAspect` AOP 拦截器：基于 Spring AOP + SpEL 表达式实现声明式缓存
- `DataAccessor` SPI 接口：核心引擎与 ORM 框架完全解耦

**MyBatis 适配器（nexacache-adapter-mybatis）**

- `AbstractMyBatisAccessor` 抽象基类：继承即可获得完整 CRUD 能力，无需编写任何方法体

**Spring Boot Starter（nexacache-spring-boot-starter）**

- `NexaCacheAutoConfiguration` 自动装配：扫描 `@NexaEntity`、自动注入 `DataAccessor`、注册 AOP 切面
- `NexaCacheProperties` 配置属性：支持 `nexacache.enabled`、`nexacache.scan-packages` 等配置项
- 兼容 Spring Boot 3.x 自动装配规范（`AutoConfiguration.imports`）

**Demo 示例（nexacache-demo）**

- 完整的 `Product` 实体 + `ProductMapper` + `ProductAccessor` + `ProductService` 示例
- 演示注解式与编程式两种使用方式

---

### 🧪 测试覆盖

核心模块包含 **16 个单元测试**，全部通过：

- `EntityMetaTests`：5 个测试（注解解析、主键提取、缓存键构建、异常处理）
- `CacheRegionTests`：7 个测试（写入/读取、驱逐、指针同步、清空、更新覆盖）
- `CacheRegistryTests`：4 个测试（注册、幂等性、未注册异常、全量清空）

---

### 📦 发布产物

| 文件 | 说明 |
|---|---|
| `nexacache-core-1.0.0.jar` | 核心引擎，无框架强依赖 |
| `nexacache-adapter-mybatis-1.0.0.jar` | MyBatis 持久层适配器 |
| `nexacache-spring-boot-starter-1.0.0.jar` | Spring Boot 自动装配 Starter |
| `nexacache-demo-1.0.0.jar` | 完整示例模块 |

---

### 🛠️ 环境要求

- JDK 21+
- Spring Boot 3.2.x
- Maven 3.8+
