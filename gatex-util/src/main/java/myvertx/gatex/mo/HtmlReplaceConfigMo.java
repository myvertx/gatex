package myvertx.gatex.mo;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HtmlReplaceConfigMo {
    private List<SrcPathMo>          srcPaths;
    private List<RegexReplacementMo> regexReplacements;
}
