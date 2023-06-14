package myvertx.gatex.mo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SrcPathMo {
    /**
     * 请求的方法
     */
    private String method;
    /**
     * 请求路径匹配的正则表达式
     */
    private String regexPath;
}
