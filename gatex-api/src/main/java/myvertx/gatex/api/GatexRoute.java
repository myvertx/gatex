package myvertx.gatex.api;

import java.util.Map;

import lombok.Data;

@Data
public class GatexRoute {

    /**
     * 请求的来源
     */
    private Src                   src;

    /**
     * 转发的目的地
     */
    private Dst                   dst;

    /**
     * 断言列表
     */
    private Map<String, Object>[] predicates;

    @Data
    public static class Src {
        private Object path;
        private Object regexPath;
    }

    @Data
    public static class Dst {
        private String  host;
        private Integer port;
        private String  path;
    }

}