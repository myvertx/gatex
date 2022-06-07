package myvertx.gatex.config;

import java.util.List;
import java.util.Map;

import io.vertx.ext.web.handler.LoggerFormat;
import lombok.Data;
import myvertx.gatex.api.GatexRoute;

@Data
public class WebProperties {
    /**
     * 是否记录日志
     */
    private Boolean             isLogging    = false;

    /**
     * 日志格式
     */
    private LoggerFormat        loggerFormat = LoggerFormat.SHORT;

    /**
     * 是否需要CORS
     */
    private Boolean             isCors       = false;

    /**
     * httpServerOptions
     */
    private Map<String, Object> server;

    /**
     * 路由列表
     */
    private List<GatexRoute>    routes;

}
