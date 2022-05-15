package myvertx.gatex.verticle;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.CorsHandler;
import lombok.extern.slf4j.Slf4j;
import myvertx.gatex.config.WebProperties;

@Slf4j
public class WebVerticle extends AbstractVerticle {
    public static final String EVENT_BUS_WEB_START = "myvertx.turtest.verticle.web.start";

    private WebProperties      webProperties;
    private HttpServer         httpServer;

    @Override
    public void start() {
        webProperties = config().mapTo(WebProperties.class);

        log.info("创建路由");
        final Router router = Router.router(vertx);
        // CORS
        router.route().handler(CorsHandler.create("*").allowedMethod(HttpMethod.GET));

        log.info("配置路由");
        // // 生成并获取验证码图像
        // router.get("/captcha/gen")
        // .handler(RouteToEBServiceHandler.build(
        // vertx.eventBus(),
        // CaptchaApi.ADDR,
        // "gen"));
        // // 校验验证码
        // router.post("/captcha/verify")
        // .handler(BodyHandler.create())
        // .handler(ValidationHandlerBuilder.create(schemaParser)
        // .body(json(objectSchema())).build())
        // .handler(RouteToEBServiceHandler.build(
        // vertx.eventBus(),
        // CaptchaApi.ADDR,
        // "verify"));

        httpServer = vertx.createHttpServer().requestHandler(router);

        vertx.eventBus()
                .consumer(EVENT_BUS_WEB_START, this::handleStart)
                .completionHandler(this::handleStartCompletion);

        log.info("WebVerticle Started");
    }

    private void handleStart(final Message<Void> message) {
        httpServer.listen(webProperties.getPort(), res -> {
            if (res.succeeded()) {
                log.info("HTTP server started on port " + res.result().actualPort());
            }
            else {
                log.error("HTTP server start fail", res.cause());
            }
        });
    }

    private void handleStartCompletion(final AsyncResult<Void> res) {
        if (res.succeeded()) {
            log.info("Event Bus register success: web.start");
        }
        else {
            log.error("Event Bus register fail: web.start", res.cause());
        }
    }

}
