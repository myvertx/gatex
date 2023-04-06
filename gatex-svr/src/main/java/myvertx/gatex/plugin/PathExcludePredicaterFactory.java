package myvertx.gatex.plugin;

import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import myvertx.gatex.api.GatexPredicater;
import myvertx.gatex.api.GatexPredicaterFactory;

import java.util.List;

/**
 * 排除路径的断言器工厂
 *
 * @author zbz
 */
@Slf4j
@Deprecated
public class PathExcludePredicaterFactory implements GatexPredicaterFactory {

    @Override
    public String name() {
        return "pathExclude";
    }

    @Override
    public GatexPredicater create(Vertx vertx, Object options) {
        if (options == null) {
            throw new IllegalArgumentException("并未配置pathExclude的值");
        }

        @SuppressWarnings("unchecked") final List<String> regexPaths = (List<String>) options;

        return ctx -> {
            final String uri = ctx.request().uri();
            log.debug("pathExclude.test: {}", uri);
            for (final String regexPath : regexPaths) {
                if (uri.matches(regexPath)) {
                    log.debug("排除路径: {}", uri);
                    return false;
                }
            }
            return true;
        };
    }

}
