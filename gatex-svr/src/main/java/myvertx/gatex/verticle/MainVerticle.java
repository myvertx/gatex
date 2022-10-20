package myvertx.gatex.verticle;

import java.util.List;
import java.util.Map;

import com.google.inject.Module;

import io.vertx.core.Verticle;
import myvertx.gatex.inject.MainModule;
import rebue.wheel.vertx.guice.KafkaGuiceModule;
import rebue.wheel.vertx.guice.PulsarGuiceModule;
import rebue.wheel.vertx.verticle.AbstractMainVerticle;

public class MainVerticle extends AbstractMainVerticle {

    /**
     * 添加guice模块
     *
     * @param guiceModules 添加guice模块到此列表
     */
    @Override
    protected void addGuiceModules(final List<Module> guiceModules) {
        guiceModules.add(new PulsarGuiceModule());
        guiceModules.add(new KafkaGuiceModule());
        guiceModules.add(new MainModule());
    }

    /**
     * 添加要部署的Verticle类列表
     *
     * @param verticleClasses 添加Verticle类到此列表
     */
    @Override
    protected void addVerticleClasses(final Map<String, Class<? extends Verticle>> verticleClasses) {
        verticleClasses.put("web", WebVerticle.class);
    }

}
