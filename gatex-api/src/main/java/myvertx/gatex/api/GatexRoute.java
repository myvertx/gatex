package myvertx.gatex.api;

import java.util.Map;

import lombok.Data;

@Data
public class GatexRoute {

    /**
     * 请求的来源
     */
    private Src                 src;

    /**
     * 转发的目的地
     */
    private Dst                 dst;

    /**
     * 断言列表
     */
    private Map<String, Object> predicates;

    /**
     * 来源配置
     *
     * path和regexPath可以同时使用，是or的逻辑
     *
     */
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

    /**
     * 目的地配置
     */
    @Data
    public static class Dst {
        /**
         * 目的地的主机名(如果是本机静态网站，请填static，且不用填写port项)
         */
        private String              host;
        /**
         * 目的地的端口号
         */
        private Integer             port;
        /**
         * 静态网站的HistoryMode(Hash/Html5/Memory)，默认为Html5
         */
        private String              historyMode = "Html5";

        /**
         * 客户端的配置选项
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

        /**
         * 代理拦截器
         */
        private Map<String, Object> proxyInterceptors;
    }

}