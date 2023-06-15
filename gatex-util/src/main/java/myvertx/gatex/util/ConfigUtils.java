package myvertx.gatex.util;

import com.google.common.base.Splitter;
import io.vertx.core.impl.Arguments;
import io.vertx.httpproxy.ProxyContext;
import lombok.extern.slf4j.Slf4j;
import myvertx.gatex.mo.RegexReplacementMo;
import myvertx.gatex.mo.SrcPathMo;
import org.apache.commons.lang3.StringUtils;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
public class ConfigUtils {
    /**
     * 判断是否匹配srcPath
     *
     * @param proxyContext 代理上下文
     * @param srcPaths     srcPath列表
     * @return 是否匹配
     */
    public static boolean isMatchSrcPath(ProxyContext proxyContext, List<SrcPathMo> srcPaths) {
        boolean isMatch = false;
        if (srcPaths == null || srcPaths.isEmpty()) {
            isMatch = true;
        } else {
            String method = proxyContext.request().getMethod().name();
            String uri    = proxyContext.request().getURI();
            log.debug("判断{}:{}是否匹配srcPath", method, uri);
            for (SrcPathMo srcPath : srcPaths) {
                if (StringUtils.isNotBlank(srcPath.getMethod()) && !srcPath.getMethod().equalsIgnoreCase(method)) {
                    continue;
                }
                if (srcPath.getRegexPath().matcher(uri).find()) {
                    isMatch = true;
                    break;
                }
            }
        }
        return isMatch;
    }

    /**
     * 读取srcPath的配置
     *
     * @param pluginName 插件名称
     * @param optionsMap Map类型的选项配置
     * @return srcPaths
     */
    public static List<SrcPathMo> readSrcPath(String pluginName, Map<?, ?> optionsMap) {
        List<SrcPathMo> result     = new LinkedList<>();
        Object          srcPathObj = optionsMap.get("srcPath");
        if (srcPathObj instanceof String srcPathStr) {
            Arguments.require(StringUtils.isNotBlank(srcPathStr), "并未配置%s的srcPath".formatted(pluginName));
            addSrcPathStrToSrcPathMoList(result, srcPathStr);
            return result;
        } else if (srcPathObj instanceof List<?>) {
            @SuppressWarnings("unchecked")
            List<String> srcPathList = (List<String>) srcPathObj;
            for (String srcPathStr : srcPathList) {
                addSrcPathStrToSrcPathMoList(result, srcPathStr);
            }
            return result;
        } else {
            throw new IllegalArgumentException("配置%s的srcPath格式错误".formatted(pluginName));
        }
    }

    private static void addSrcPathStrToSrcPathMoList(List<SrcPathMo> srcPathMoList, String srcPathStr) {
        SrcPathMo srcPathMo = new SrcPathMo();
        if (srcPathStr.indexOf(':') == -1) {
            srcPathMo.setRegexPath(Pattern.compile(srcPathStr));
        } else {
            String[] split = srcPathStr.split(":");
            srcPathMo.setMethod(split[0]);
            srcPathMo.setRegexPath(Pattern.compile(split[1]));
        }
        srcPathMoList.add(srcPathMo);
    }

    public static RegexReplacementMo readReplacement(String pluginName, String regexReplacement) {
        // 默认":"为分隔符
        char separator = ':';
        // 如果":"不是有且仅有1个，那么以第1个字符为分隔符
        int index = regexReplacement.indexOf(separator);
        if (index == -1 || index != regexReplacement.lastIndexOf(separator)) {
            separator = regexReplacement.charAt(0);
            // 如果分隔符不是有且仅有1个，那么报格式错误
            index = regexReplacement.indexOf(separator);
            if (index == -1 || index != regexReplacement.lastIndexOf(separator)) {
                throw new IllegalArgumentException("配置%s的replacement格式错误".formatted(pluginName));
            }
        }
        Iterator<String> replacementIterator = Splitter.on(separator).trimResults().omitEmptyStrings().split(regexReplacement).iterator();
        Arguments.require(replacementIterator.hasNext(), "并未配置%s的replacement".formatted(pluginName));
        String regex       = replacementIterator.next();
        String replacement = replacementIterator.hasNext() ? replacementIterator.next() : "";
        Arguments.require(!replacementIterator.hasNext(), "配置%s的replacement格式错误".formatted(pluginName));
        return RegexReplacementMo.builder()
            .regex(regex)
            .replacement(replacement)
            .build();
    }

    public static List<RegexReplacementMo> readReplacements(String pluginName, List<String> replacementList) {
        List<RegexReplacementMo> result = new LinkedList<>();
        for (final String regexReplacement : replacementList) {
            result.add(readReplacement(pluginName, regexReplacement));
        }
        return result;
    }


}
