package myvertx.gatex.config;

import java.util.List;

import lombok.Data;
import myvertx.gatex.api.GatexRoute;

@Data
public class MainProperties {
    /**
     * 路由列表
     */
    private List<GatexRoute> routes;

}
