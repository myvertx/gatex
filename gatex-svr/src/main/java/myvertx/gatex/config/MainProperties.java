package myvertx.gatex.config;

import lombok.Data;
import myvertx.gatex.api.GatexRoute;

import java.util.List;

@Data
public class MainProperties {
    /**
     * 路由列表
     */
    private List<GatexRoute> routes;

}
