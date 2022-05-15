package myvertx.gatex.config;

import lombok.Data;

@Data
public class WebProperties {
    /**
     * Web服务器监听的端口号
     */
    private Integer port = 0;
}
