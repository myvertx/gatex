package myvertx.gatex.config;

import lombok.Data;
import myvertx.gatex.api.GatexRoute;

import java.util.List;

@Data
public class MainProperties {
    /**
     * 是否严格模式
     * 如果是严格模式，启动时读取配置的时候，配置的格式不对或值不符合要求，会停止运行，否则只是警告
     * 默认为true
     */
    private Boolean          strict = true;
    /**
     * 路由列表
     */
    private List<GatexRoute> routes;

}
