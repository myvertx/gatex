package myvertx.gatex.util;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Splitter;

import io.vertx.core.impl.Arguments;
import lombok.extern.slf4j.Slf4j;
import myvertx.gatex.mo.RegexReplacementMo;
import myvertx.gatex.mo.SrcPathMo;
import rebue.wheel.core.StrUtils;

@Slf4j
public class ConfigUtils {
    /**
     * 判断是否匹配srcPath
     *
     * @param uri      URI
     * @param srcPaths srcPath列表
     * @return 是否匹配
     */
    public static boolean isMatchSrcPath(String uri, List<SrcPathMo> srcPaths) {
        if (srcPaths == null || srcPaths.isEmpty()) {
            return true;
        } else {
            for (SrcPathMo srcPath : srcPaths) {
                log.debug("判断{}是否匹配{}", uri, srcPath);
                if (srcPath.getRegexPath().matcher(uri).find()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 读取srcPath的配置
     *
     * @param optionsMap Map类型的选项配置
     * @param pluginName 插件名称(用于出错时反馈)
     * @return 源路径列表
     */
    public static List<SrcPathMo> readSrcPath(Map<?, ?> optionsMap, String pluginName) {
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

    /**
     * 分隔符字符集
     */
    private final static String SEPARATORS = ":,;~`!@#$%^&";

    /**
     * 从字符串中解析出替换信息
     *
     * @param regexReplacement 要解析的字符串
     * @param pluginName       插件名称(用于出错时反馈)
     * @return 替换信息
     */
    public static RegexReplacementMo parseReplacement(String regexReplacement, String pluginName) {
        // 先假设第1个字符为分隔符
        char separator = regexReplacement.charAt(0);
        // 如果分隔符出现的次数为2，说明第1个字符是分隔符
        if (StrUtils.count(regexReplacement, separator) == 2) {
            // 去掉第1个分隔符
            regexReplacement = regexReplacement.substring(1);
        }
        // 如果分隔符出现的次数不为2，说明第1个字符不是分隔符
        else {
            // 按分隔符字符集顺序查看哪个分隔符只出现1次，就以此为分隔符
            separator = '0';
            for (int i = 0; i < SEPARATORS.length(); i++) {
                char targetChar = SEPARATORS.charAt(i);
                if (StrUtils.count(regexReplacement, targetChar) == 1) {
                    separator = targetChar;
                    break;
                }
            }
            // 如果未找到分隔符，那么报格式错误
            if (separator == '0') {
                throw new IllegalArgumentException("配置%s的replacement格式错误".formatted(pluginName));
            }
        }

        Iterator<String> replacementIterator = Splitter.on(separator).trimResults().omitEmptyStrings()
                .split(regexReplacement).iterator();
        Arguments.require(replacementIterator.hasNext(), "并未配置%s的replacement".formatted(pluginName));
        String regex       = replacementIterator.next();
        String replacement = replacementIterator.hasNext() ? replacementIterator.next() : "";
        Arguments.require(!replacementIterator.hasNext(), "配置%s的replacement格式错误".formatted(pluginName));
        return RegexReplacementMo.builder()
                .regex(regex)
                .replacement(replacement)
                .build();
    }

    /**
     * 从字符串列表中解析出替换信息列表
     *
     * @param replacementList 要解析的字符串列表
     * @param pluginName      插件名称(用于出错时记录日志)
     * @return 替换信息列表
     */
    public static List<RegexReplacementMo> parseReplacements(List<String> replacementList, String pluginName) {
        List<RegexReplacementMo> result = new LinkedList<>();
        for (final String regexReplacement : replacementList) {
            result.add(parseReplacement(regexReplacement, pluginName));
        }
        return result;
    }

}
