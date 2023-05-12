package myvertx.gatex.drools.fact;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequestFact {
    /**
     * 请求的URI
     */
    private String     uri;
    /**
     * 请求的body
     */
    private JsonObject body;
}
