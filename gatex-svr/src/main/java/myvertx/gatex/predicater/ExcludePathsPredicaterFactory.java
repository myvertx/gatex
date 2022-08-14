package myvertx.gatex.predicater;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import myvertx.gatex.api.GatexPredicater;
import myvertx.gatex.api.GatexPredicaterFactory;

/**
 * 排除路径的断言器工厂
 *
 * @author zbz
 *
 */
@Slf4j
public class ExcludePathsPredicaterFactory implements GatexPredicaterFactory {

    @Override
    public String name() {
        return "excludePaths";
    }

    @Override
    public GatexPredicater create(Object options) {
        if (options == null) {
            throw new IllegalArgumentException("excludePaths的值为空");
        }

        @SuppressWarnings("unchecked")
        final List<String> regexPaths = (List<String>) options;

        return ctx -> {
            final String uri = ctx.request().uri();
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
