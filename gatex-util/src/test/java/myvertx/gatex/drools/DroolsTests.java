package myvertx.gatex.drools;

import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import myvertx.gatex.drools.fact.RequestFact;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

import java.util.UUID;

@Slf4j
@TestMethodOrder(MethodOrderer.MethodName.class)
public class DroolsTests {
    @RepeatedTest(1000)
    @Execution(ExecutionMode.CONCURRENT)
    public void test01() {
        log.info("aaa");
        KieServices kieServices = KieServices.Factory.get();
        log.info("bbb");
        KieContainer kieContainer = kieServices.getKieClasspathContainer();
        log.info("ccc");
        KieSession kieSession = kieContainer.newKieSession("test01");
        log.info("ddd");
        String random = System.nanoTime() + UUID.randomUUID().toString().replaceAll("-", "");
        JsonObject body = new JsonObject()
                .put("0", random)
                .put("a", "AAA")
                .put("b", "BBB")
                .put("c", "CCC")
                .put("d", "DDD")
                .put("e", "EEE")
                .put("f", "FFF")
                .put("g", "GGG");
        kieSession.insert(RequestFact.builder()
                .uri("/abc")
                .body(body)
                .build());
        int rulesCount = kieSession.fireAllRules();
        log.info("result: {}", body);
        Assertions.assertEquals(1, rulesCount);
        Assertions.assertEquals("{\"0\":\"" + random + "\",\"c\":\"CCCCC\",\"d\":\"DDDDD\",\"f\":\"FFF\",\"g\":\"GGG\",\"h\":\"HHHHH\",\"i\":\"IIIII\",\"j\":\"CCCCC\",\"k\":\"EEE\",\"l\":\"EEE\"}", body.encode());

        kieSession.dispose();
    }

}
