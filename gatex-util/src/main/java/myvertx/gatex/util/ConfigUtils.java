package myvertx.gatex.util;

import com.google.common.base.Splitter;
import io.vertx.core.impl.Arguments;
import myvertx.gatex.mo.SrcPathMo;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

public class ConfigUtils {
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
            srcPathMo.setRegexPath(srcPathStr);
        } else {
            String[] split = srcPathStr.split(":");
            srcPathMo.setMethod(split[0]);
            srcPathMo.setRegexPath(split[1]);
        }
        srcPathMoList.add(srcPathMo);
    }

    public static Map<String, String> readReplacement(String pluginName, List<String> replacementList) {
        Map<String, String> replacements = new LinkedHashMap<>();
        for (final String replacement : replacementList) {
            // 默认":"为分隔符
            char separator = ':';
            // 如果":"不是有且仅有1个，那么以第1个字符为分隔符
            int index = replacement.indexOf(separator);
            if (index == -1 || index != replacement.lastIndexOf(separator)) {
                separator = replacement.charAt(0);
                // 如果分隔符不是有且仅有1个，那么报格式错误
                index = replacement.indexOf(separator);
                if (index == -1 || index != replacement.lastIndexOf(separator)) {
                    throw new IllegalArgumentException("配置%s的replacement格式错误".formatted(pluginName));
                }
            }
            Iterator<String> replacementIterator = Splitter.on(separator).trimResults().omitEmptyStrings().split(replacement).iterator();
            Arguments.require(replacementIterator.hasNext(), "并未配置%s的replacement".formatted(pluginName));
            String pattern = replacementIterator.next();
            String replace = replacementIterator.hasNext() ? replacementIterator.next() : "";
            Arguments.require(!replacementIterator.hasNext(), "配置%s的replacement格式错误".formatted(pluginName));
            replacements.put(pattern, replace);
        }
        return replacements;
    }


}
