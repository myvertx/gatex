package myvertx.gatex.verticle;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Verticle;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.DatabindCodec;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SuppressWarnings("deprecation")
public class MainVerticle extends AbstractVerticle {

    static {
        // 初始化jackson的功能
        DatabindCodec.mapper()
                .disable(
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES   // 忽略没有的字段
                )
                .enable(
                        MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES    // 忽略字段和属性的大小写
                )
                .registerModule(new JavaTimeModule());  // 支持Java8的LocalDate/LocalDateTime类型
    }

    @Override
    public void start(final Promise<Void> startPromise) throws Exception {
        final ConfigRetriever retriever = ConfigRetriever.create(this.vertx);
        retriever.getConfig(configRes -> {
            log.info("config result: {}", configRes.result());

            if (configRes.failed()) {
                log.warn("Get config failed", configRes.cause());
                startPromise.fail(configRes.cause());
            }

            final JsonObject config = configRes.result();
            if (config == null || config.isEmpty()) {
                startPromise.fail("Get config is empty");
                return;
            }

            log.info("部署verticle");
            final Future<String> webVerticleFuture = deployVerticle("web", WebVerticle.class, config);

            // 部署成功或失败事件
            webVerticleFuture
                    .onSuccess(handle -> {
                        log.info("部署Verticle完成");
                        this.vertx.eventBus().publish(WebVerticle.EVENT_BUS_WEB_START, null);
                        log.info("启动完成.");
                        startPromise.complete();
                    })
                    .onFailure(err -> {
                        log.error("启动失败.", err);
                        startPromise.fail(err);
                        this.vertx.close();
                    });
        });
    }

    /**
     * 部署Verticle
     *
     * @param verticleName  Verticle的名称
     * @param verticleClass Verticle类
     * @param config        当前的配置对象
     *
     * @return Future
     */
    private Future<String> deployVerticle(final String verticleName, final Class<? extends Verticle> verticleClass, final JsonObject config) {
        return this.vertx.deployVerticle(verticleClass,
                new DeploymentOptions(config.getJsonObject(verticleName)));
    }
}
