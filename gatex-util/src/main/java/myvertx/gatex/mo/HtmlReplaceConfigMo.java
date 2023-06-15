package myvertx.gatex.mo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HtmlReplaceConfigMo {
    private List<SrcPathMo>          srcPaths;
    private List<RegexReplacementMo> regexReplacements;
}
