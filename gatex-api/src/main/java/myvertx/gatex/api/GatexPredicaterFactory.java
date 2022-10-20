package myvertx.gatex.api;

import io.vertx.core.Vertx;

/**
 * 断言器工厂
 *
 * @author zbz
 */
public interface GatexPredicaterFactory {

    /**
     * @return 工厂名称
     */
    String name();

    /**
     * 创建断言器
     *
     * @param options 创建拦截器的参数
     */
    GatexPredicater create(Vertx vertx, Object options);

}
