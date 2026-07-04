package io.nexacache.exception;

/**
 * NexaCache 框架统一运行时异常。
 *
 * @author azir
 * @since 1.0.0
 */
public class NexaCacheException extends RuntimeException {

    public NexaCacheException(String message) {
        super(message);
    }

    public NexaCacheException(String message, Throwable cause) {
        super(message, cause);
    }
}
