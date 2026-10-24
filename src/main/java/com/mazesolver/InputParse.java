// @anchor: input_parse_intro
// 数值输入解析：把"输入框文本 + 合法区间"统一折算成值或 null，界面各处不再各写一遍 try/catch。
package com.mazesolver;

/**
 * 输入框数值解析。尺寸、解数上限、叠画条数、步数/分数过滤都是"文本 → 区间内整数"，
 * 判定口径集中在这里：非法返回 null（或约定的哨兵值），由调用方决定回退与提示。
 */
public final class InputParse {

    // @anchor: input_parse_range
    // 区间校验：非整数、超出 [min,max] 一律返回 null，调用方据此走"红字提示 + 回落默认值"
    /** 解析 [min, max] 区间内的整数；非法返回 null */
    public static Integer inRange(String text, int min, int max) {
        if (text == null) return null;
        try {
            int v = Integer.parseInt(text.trim());
            return (v < min || v > max) ? null : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }
    // @anchor: input_parse_range_end

    // @anchor: input_parse_bounds
    // 过滤上下界：留空或非法都等于"不设限"，因此下界回落 0、上界回落 MAX_VALUE
    /** 过滤下界：空或非法 → 0（等于不设下限） */
    public static int lowerBound(String text) {
        Integer v = inRange(text, Integer.MIN_VALUE, Integer.MAX_VALUE);
        return v == null ? 0 : Math.max(0, v);
    }

    /** 过滤上界：空或非法 → Integer.MAX_VALUE（等于不设上限） */
    public static int upperBound(String text) {
        Integer v = inRange(text, Integer.MIN_VALUE, Integer.MAX_VALUE);
        return v == null ? Integer.MAX_VALUE : v;
    }
    // @anchor: input_parse_bounds_end

    private InputParse() {
    }
}
