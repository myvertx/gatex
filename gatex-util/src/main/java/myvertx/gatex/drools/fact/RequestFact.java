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
     * 请求的方法
     */
    private String     method;
    /**
     * 请求的主机
     */
    private String     host;
    /**
     * 请求的端口
     */
    private Integer    port;
    /**
     * 请求的URI
     */
    private String     uri;
    /**
     * 请求的body
     */
    private JsonObject body;
}
