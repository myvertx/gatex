package myvertx.gatex.api;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class GatexRoute {

    /**
     * 请求的来源
     */
    private Src src;

    /**
     * 转发的目的地
     */
    private Dst dst;

    /**
     * 来源配置
     * <p>
     * path和regexPath可以同时使用，是or的逻辑
     */
    @Data
    public static class Src {
        /**
         * 匹配来源的路径(匹配前面部分)
         */
        private Object path;
        /**
         * 匹配来源的路径(正则表达式)
         */
        private Object regexPath;
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
         * 请求目的地的路径(默认不设置，请求路径为来源请求的路径，如果设置，请求路径将由此值替换)
         */
        private String              path;
        /**
         * 请求目的地路径的前缀(默认不设置，如果设置，请求路径的前面会添加此值)
         */
        private String              pathPrefix;
        /**
         * 替换请求目的地的路径(默认不设置，如果设置，此值将替换请求路径的前面部分)
         * 设置的值应为":"分隔的两节，第1节为要被替换的部分，第2节为用来替换的值
         * 如果设置的值缺少第2节，意思是请求路径直接去掉第1节的部分
         */
        private String              pathPrefixReplace;
        /**
         * 静态网站的HistoryMode(Hash/Html5/Memory)，默认为Html5
         */
        private String              historyMode = "Html5";
        /**
         * 是否SSL加密
         */
        private Boolean             isSsl       = false;
        /**
         * 代理发出请求的选项
         */
        private Map<String, Object> request;
        /**
         * 创建客户端的配置选项
         * httpClientOptions
         */
        private Map<String, Object> client;
        /**
         * 断言列表
         */
        private Map<String, Object> predicates;
        /**
         * 过滤器
         */
        private Map<String, Object> filters     = new LinkedHashMap<>();

    }

}