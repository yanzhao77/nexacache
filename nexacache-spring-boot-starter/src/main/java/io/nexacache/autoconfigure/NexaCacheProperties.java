package io.nexacache.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * NexaCache Spring Boot 配置属性。
 * 在 {@code application.yml} 中以 {@code nexacache} 为前缀进行配置。
 *
 * <p>示例配置：
 * <pre>
 * nexacache:
 *   enabled: true
 *   scan-packages:
 *     - io.nexacache.demo.entity
 * </pre>
 *
 * @author azir
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "nexacache")
public class NexaCacheProperties {

    /** 是否启用 NexaCache，默认 true */
    private boolean enabled = true;

    /** 要扫描 @NexaEntity 注解的包路径列表 */
    private List<String> scanPackages = new ArrayList<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public List<String> getScanPackages() { return scanPackages; }
    public void setScanPackages(List<String> scanPackages) { this.scanPackages = scanPackages; }
}
