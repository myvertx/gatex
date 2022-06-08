package myvertx.gatex.inject;

import javax.inject.Named;
import javax.inject.Singleton;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import io.vertx.core.json.JsonObject;
import myvertx.gatex.config.MainProperties;

public class MainModule extends AbstractModule {

    @Singleton
    @Provides
    public MainProperties getMainProperties(@Named("config") final JsonObject config) {
        return config.getJsonObject("main").mapTo(MainProperties.class);
    }

}
