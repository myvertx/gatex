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
        /**
         * 匹配来源的路径(匹配前面部分)
         */
        private Object              path;
        /**
         * 匹配来源的路径(正则表达式)
         */
        private Object              regexPath;
        /**
         * 匹配器列表
         */
        private Map<String, Object> matchers;
    }

    @Data
    public static class Dst {
        /**
         * 目的地的主机名
         */
        private String              host;
        /**
         * 目的地的端口号
         */
        private Integer             port;
        /**
         * 目的地路径的前缀
         */
        private String              path;
        /**
         * httpClientOptions
         */
        private Map<String, Object> client;

        /**
         * 前置过滤器
         */
        private Map<String, Object> preFilters;

        /**
         * 后置过滤器
         */
        private Map<String, Object> postFilters;
    }

}