// @anchor: filter_intro
// 数字串筛选表达式：把用户写的条件式解析成若干"对数字串求值"的判断，供路径列表按得分点标记序列过滤。
package com.mazesolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * 数字串筛选表达式。
 *
 * <p>语法（条件之间用 {@code &} 联立，全部满足才算命中）：
 * <ul>
 *   <li>{@code 3>7} —— 数字 3 在 7 的前面（{@code 3<7} 即 3 在 7 的后面）；</li>
 *   <li>{@code 3>7>1} —— 链式关系，等价于 "3 在 7 前 且 7 在 1 前"；</li>
 *   <li>{@code "37"} —— 数字串中包含子串 37；</li>
 *   <li>{@code 5} —— 数字串中包含数字 5；</li>
 *   <li>空白字符忽略；空表达式表示不筛选。</li>
 * </ul>
 *
 * <p>关系式的判定口径是"存在一对先后出现的数字"：只要串里有一个 3 出现在某个 7 之前即成立，
 * 不要求两者相邻，也不要求唯一。
 */
public final class PathSequenceFilter {

    // @anchor: filter_grammar
    // 表达式的词法约定集中在此：条件分隔符、先后关系符、子串引号与数字取值区间
    /** 条件之间的联立符号 */
    public static final char AND = '&';
    /** "在……之前" */
    public static final char BEFORE = '>';
    /** "在……之后" */
    public static final char AFTER = '<';
    /** 子串条件的引号 */
    public static final char QUOTE = '"';
    /** 界面上的语法示例（与解析规则保持同步） */
    public static final String SYNTAX_HINT =
            "3>7 = 3在7前；\"37\" = 含子串37；5 = 含数字5；用 & 并联";

    /** 原始表达式（回显用） */
    private final String expression;
    /** 已解析的条件列表（空表示不筛选） */
    private final List<Term> terms;

    private PathSequenceFilter(String expression, List<Term> terms) {
        this.expression = expression;
        this.terms = Collections.unmodifiableList(terms);
    }

    // @anchor: filter_grammar_end

    // @anchor: filter_parse
    // 解析：空表达式 = 不筛选；否则按 & 切分、逐个解析，任何一处不合法都抛 IllegalArgumentException 供界面提示
    /**
     * 解析表达式。
     * @param expression 用户输入；null 或空白视为"不筛选"
     * @return 可复用的筛选器
     * @throws IllegalArgumentException 表达式非法（消息可直接展示给用户）
     */
    public static PathSequenceFilter parse(String expression) {
        String raw = expression == null ? "" : expression.trim();
        List<Term> parsed = new ArrayList<>();
        if (raw.isEmpty()) return new PathSequenceFilter(raw, parsed);

        // 子串条件内部只允许数字，因此 & 只会出现在条件之间，直接切分即可
        String[] parts = raw.split(String.valueOf(AND), -1);
        for (String part : parts) {
            String cond = part.trim();
            if (cond.isEmpty()) {
                throw new IllegalArgumentException("条件不能为空：请检查是否多写了 '" + AND + "'");
            }
            parsed.add(parseTerm(cond));
        }
        return new PathSequenceFilter(raw, parsed);
    }

    /**
     * 解析单个条件：引号开头是子串条件，否则是"数字关系链 / 单数字"。
     * 状态机只有两态——期待数字（写入 digits）与期待关系符（写入 operators）交替推进，
     * 因此"以关系符结尾""两个数字间缺关系符"都能在解析期报错，而不是留到匹配时才出错。
     */
    private static Term parseTerm(String cond) {
        if (cond.charAt(0) == QUOTE) return parseTextTerm(cond);

        List<Character> digits = new ArrayList<>();
        List<Character> operators = new ArrayList<>();
        boolean afterDigit = false; // 上一个有效字符是否为数字
        for (int i = 0; i < cond.length(); i++) {
            char ch = cond.charAt(i);
            if (Character.isWhitespace(ch)) continue;
            if (afterDigit) {
                if (ch != BEFORE && ch != AFTER) {
                    throw new IllegalArgumentException("两个数字之间只能写 '" + BEFORE + "' 或 '" + AFTER
                            + "'，实际是 '" + ch + "'：" + cond);
                }
                operators.add(ch);
                afterDigit = false;
            } else {
                if (!isScoreDigit(ch)) {
                    throw new IllegalArgumentException("这里需要 " + MazeModel.MIN_SCORE_DIGIT + "~"
                            + MazeModel.MAX_SCORE_DIGIT + " 的数字，实际是 '" + ch + "'：" + cond);
                }
                digits.add(ch);
                afterDigit = true;
            }
        }
        if (!afterDigit) throw new IllegalArgumentException("条件不能以运算符结尾：" + cond);

        if (digits.size() == 1) {
            char d = digits.get(0);
            return new Term("含数字 " + d, s -> s.indexOf(d) >= 0);
        }
        return new Term(describeChain(digits, operators), buildChainTest(digits, operators));
    }


    /** 解析子串条件："37" → 包含 37 */
    private static Term parseTextTerm(String cond) {
        if (cond.length() < 2 || cond.charAt(cond.length() - 1) != QUOTE) {
            throw new IllegalArgumentException("引号未闭合：" + cond);
        }
        String text = cond.substring(1, cond.length() - 1);
        if (text.isEmpty()) throw new IllegalArgumentException("引号内不能为空：" + cond);
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (!isScoreDigit(ch)) {
                throw new IllegalArgumentException("引号内只能写 " + MazeModel.MIN_SCORE_DIGIT + "~"
                        + MazeModel.MAX_SCORE_DIGIT + " 的数字，实际含 '" + ch + "'：" + cond);
            }
        }
        return new Term("包含 \"" + text + "\"", s -> s.contains(text));
    }

    /** 链式关系的中文描述（顺序与输入一致） */
    private static String describeChain(List<Character> digits, List<Character> operators) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < operators.size(); i++) {
            if (i > 0) sb.append(" 且 ");
            sb.append(digits.get(i));
            sb.append(operators.get(i) == BEFORE ? " 在 " : " 位于 ");
            if (operators.get(i) == BEFORE) {
                sb.append(digits.get(i + 1)).append(" 之前");
            } else {
                sb.append(digits.get(i + 1)).append(" 之后");
            }
        }
        return sb.toString();
    }

    /** 链式关系的判定：每一对相邻数字各自成立即整体成立 */
    private static Predicate<String> buildChainTest(List<Character> digits, List<Character> operators) {
        return s -> {
            for (int i = 0; i < operators.size(); i++) {
                char a = digits.get(i);
                char b = digits.get(i + 1);
                if (operators.get(i) == BEFORE) {
                    // a 在 b 之前：存某个 a 的位置小于某个 b 的位置
                    if (s.indexOf(a) < 0 || s.lastIndexOf(b) < 0 || s.indexOf(a) >= s.lastIndexOf(b)) return false;
                } else {
                    // a 在 b 之后：存在某个 b 的位置小于某个 a 的位置
                    if (s.indexOf(b) < 0 || s.lastIndexOf(a) < 0 || s.lastIndexOf(a) <= s.indexOf(b)) return false;
                }
            }
            return true;
        };
    }

    /** 是否为合法的标记数字字符（区间取自模型，避免各处写字面量） */
    private static boolean isScoreDigit(char ch) {
        return ch >= ('0' + MazeModel.MIN_SCORE_DIGIT) && ch <= ('0' + MazeModel.MAX_SCORE_DIGIT);
    }


    // @anchor: filter_parse_end

    // @anchor: filter_match
    // 求值：全部条件都命中才算命中；不筛选（空表达式）时恒为 true
    /** 该数字串是否满足全部条件 */
    public boolean matches(String digits) {
        String s = digits == null ? "" : digits;
        for (Term term : terms) {
            if (!term.test.test(s)) return false;
        }
        return true;
    }

    /** 是否为空表达式（等于不筛选） */
    public boolean isEmpty() {
        return terms.isEmpty();
    }

    /** 原始表达式（界面回显用） */
    public String getExpression() {
        return expression;
    }

    /** 各条件的可读描述（界面提示"按什么条件筛的"时用） */
    public List<String> describe() {
        List<String> out = new ArrayList<>(terms.size());
        for (Term term : terms) out.add(term.text);
        return out;
    }

    /** 单个已解析条件：可读文本 + 求值函数 */
    private static final class Term {
        private final String text;
        private final Predicate<String> test;

        Term(String text, Predicate<String> test) {
            this.text = text;
            this.test = test;
        }
    }

    // @anchor: filter_match_end
}
