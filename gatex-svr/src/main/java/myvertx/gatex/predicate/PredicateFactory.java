package myvertx.gatex.predicate;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import myvertx.gatex.api.GatexPredicate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class PredicateFactory {
    /**
     * 已注册的断言列表
     */
    private static Map<String, GatexPredicate> registeredPredicates = new HashMap<>();

    @SneakyThrows
    public static void create(Class<? extends GatexPredicate> predicateClass) {
        GatexPredicate predicate = predicateClass.getConstructor().newInstance();
        final String name = predicate.name();
        log.info("注册predicate: {}", name);
        registeredPredicates.put(name, predicate);
    }

    public static GatexPredicate get(String predicateName) {
        return registeredPredicates.get(predicateName);
    }
}
