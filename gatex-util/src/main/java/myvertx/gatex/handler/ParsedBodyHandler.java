package myvertx.gatex.handler;

import io.vertx.httpproxy.ProxyContext;

/**
 * 解析body后的处理器
 *
 * @author zbz
 */
public interface ParsedBodyHandler {
    void handle(ProxyContext proxyContext, String sRequestBody);
}
