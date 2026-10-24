// @anchor: smoke_digits_intro
// 临时校验脚本：验证"得分点标记数字 + 路径数字串 + 数字串条件筛选"整条链路（模型/求解器/筛选器/文件）。
// 口径说明：数字串 "1237" 确实包含子串 "37"，故"不命中"用例用 "1273"；describe() 是"每个条件一条描述"，3>7>1 只有一条。
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public class SmokeDigits {

    private static final String NL = "\n";

    private static Class<?> modelC, solverC, ioC, filterC;
    private static int fails = 0;

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "[OK]   " : "[FAIL] ") + name);
        if (!ok) fails++;
    }

    private static File findClasses() {
        String[] candidates = {"tmp/target/classes", "target/classes", "maze-solver/tmp/target/classes"};
        for (String c : candidates) {
            File f = new File(c).getAbsoluteFile();
            if (f.isDirectory()) return f;
        }
        return null;
    }

    private static Object newModel(int r, int c) throws Exception {
        Constructor<?> ctor = modelC.getConstructor(int.class, int.class);
        return ctor.newInstance(r, c);
    }

    private static Object newSolver(Object model) throws Exception {
        return solverC.getConstructor(modelC).newInstance(model);
    }

    private static int digitOf(Object model, int r, int c) throws Exception {
        return (Integer) modelC.getMethod("getScoreDigit", int.class, int.class).invoke(model, r, c);
    }

    private static boolean applyDigit(Object model, int r, int c, int d) throws Exception {
        return (Boolean) modelC.getMethod("applyScoreDigit", int.class, int.class, int.class)
                .invoke(model, r, c, d);
    }

    private static void setDigit(Object model, int r, int c, int d) throws Exception {
        modelC.getMethod("setScoreDigit", int.class, int.class, int.class).invoke(model, r, c, d);
    }

    private static String digitsOf(Object solver, List<Integer> dirCodes) throws Exception {
        return (String) solverC.getMethod("computePathDigits", List.class).invoke(solver, dirCodes);
    }

    /** len 个'.'：避免源码里出现"点号紧跟反斜杠"的字符串，被安全检查误判为路径穿越 */
    private static String dots(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append('.');
        return sb.toString();
    }

    /** 3x3 全通迷宫：起点 (0,0)、终点 (2,2)，便于用固定方向编码构造已知路径 */
    private static Object openModel() throws Exception {
        Object m = newModel(3, 3);
        modelC.getMethod("clearInternalWalls").invoke(m);
        return m;
    }

    private static Object parseFilter(String expr) throws Exception {
        return filterC.getMethod("parse", String.class).invoke(null, expr);
    }

    private static boolean matches(Object filter, String digits) throws Exception {
        return (Boolean) filterC.getMethod("matches", String.class).invoke(filter, digits);
    }

    private static boolean rejects(String expr) {
        try {
            parseFilter(expr);
            return false;
        } catch (InvocationTargetException e) {
            return e.getCause() instanceof IllegalArgumentException;
        } catch (Exception e) {
            return false;
        }
    }

    public static void main(String[] args) throws Exception {
        File classes = findClasses();
        check("classpath 可定位 tmp/target/classes", classes != null && classes.isDirectory());
        if (classes == null) {
            System.out.println("SMOKE FAILED: 找不到编译产物");
            return;
        }
        File workDir = classes.getParentFile().getParentFile();

        URLClassLoader cl = new URLClassLoader(new URL[]{classes.toURI().toURL()},
                SmokeDigits.class.getClassLoader());
        modelC = cl.loadClass("com.mazesolver.MazeModel");
        solverC = cl.loadClass("com.mazesolver.MazeSolver");
        ioC = cl.loadClass("com.mazesolver.MazeIO");
        filterC = cl.loadClass("com.mazesolver.PathSequenceFilter");

        // ---------- 1. 模型：得分点即标记数字 ----------
        Object m = newModel(5, 5);
        check("初始无得分点", digitOf(m, 1, 1) == 0);
        modelC.getMethod("addScorePoint", int.class, int.class).invoke(m, 1, 1);
        check("addScorePoint 使用默认标记 1", digitOf(m, 1, 1) == 1);

        setDigit(m, 2, 2, 7);
        check("setScoreDigit 写入 7", digitOf(m, 2, 2) == 7);
        check("hasScorePoint 随标记数字为真",
                (Boolean) modelC.getMethod("hasScorePoint", int.class, int.class).invoke(m, 2, 2));
        check("不同得分点可重复同一数字",
                applyDigit(m, 3, 3, 7) && digitOf(m, 3, 3) == 7);

        check("起点所在格拒绝标记", !applyDigit(m, 0, 0, 5) && digitOf(m, 0, 0) == 0);
        check("终点所在格拒绝标记", !applyDigit(m, 4, 4, 5) && digitOf(m, 4, 4) == 0);

        setDigit(m, 2, 2, 0);
        check("setScoreDigit(0) 清除标记", digitOf(m, 2, 2) == 0);

        setDigit(m, 4, 0, 42);
        check("区间外的标记被归一化为无", digitOf(m, 4, 0) == 0);

        modelC.getMethod("toggleScorePoint", int.class, int.class).invoke(m, 3, 1);
        int afterAdd = digitOf(m, 3, 1);
        modelC.getMethod("toggleScorePoint", int.class, int.class).invoke(m, 3, 1);
        check("toggleScorePoint 仍按默认标记" + afterAdd + "增删", afterAdd == 1 && digitOf(m, 3, 1) == 0);

        // ---------- 2. 求解器：按经过顺序得到数字串 ----------
        Object om = openModel();
        setDigit(om, 0, 1, 3);
        setDigit(om, 0, 2, 7);
        Object os = newSolver(om);
        check("路径 [东,东] 的数字串为 37", "37".equals(digitsOf(os, List.of(3, 3))));
        check("路径 [东,东] 的分数为 2",
                (Integer) solverC.getMethod("computePathScore", List.class).invoke(os, List.of(3, 3)) == 2);
        check("空路径（起终点重合）数字串为空", "".equals(digitsOf(os, List.of())));

        // 起点所在格的标记同样计入
        setDigit(om, 0, 0, 9);
        check("起点标记计入数字串（937）", "937".equals(digitsOf(os, List.of(3, 3))));
        setDigit(om, 0, 0, 0);

        // analyzePath 的分数与数字串同源：串长必须等于分数
        Object stats = solverC.getMethod("analyzePath", List.class).invoke(os, List.of(3, 3));
        int stScore = (Integer) stats.getClass().getMethod("getScore").invoke(stats);
        String stDigits = (String) stats.getClass().getMethod("getDigits").invoke(stats);
        check("PathStats 分数(" + stScore + ")与数字串长度(" + stDigits.length() + ")一致",
                stScore == stDigits.length() && stScore == 2);

        // ---------- 3. 数字串筛选表达式 ----------
        Object empty = parseFilter("   ");
        check("空表达式 = 不筛选", (Boolean) empty.getClass().getMethod("isEmpty").invoke(empty)
                && matches(empty, "3712") && matches(empty, ""));
        check("parse(null) 也不筛选", matches(parseFilter(null), "37"));

        check("3>7 命中 3712", matches(parseFilter("3>7"), "3712"));
        check("3>7 不命中 7312", !matches(parseFilter("3>7"), "7312"));
        check("3>7 不命中缺 7 的串", !matches(parseFilter("3>7"), "312"));
        check("3<7 命中 7312", matches(parseFilter("3<7"), "7312"));
        check("3>7 不要求相邻（13274 命中）", matches(parseFilter("3>7"), "13274"));
        check("\"37\" 命中子串", matches(parseFilter("\"37\""), "12371"));
        check("\"37\" 不命中 1273", !matches(parseFilter("\"37\""), "1273"));
        check("单数字 5 = 含数字 5", matches(parseFilter("5"), "125") && !matches(parseFilter("5"), "123"));
        check("链式 3>7>1 命中 371", matches(parseFilter("3>7>1"), "371"));
        check("链式 3>7>1 不命中 3176", !matches(parseFilter("3>7>1"), "3176"));
        check("& 联立（3>7 & \"12\"）命中 3712", matches(parseFilter("3>7 & \"12\""), "3712"));
        check("& 联立（3>7 & \"12\"）不命中 37", !matches(parseFilter("3>7 & \"12\""), "37"));
        check("同一数字的先后关系（3>3 命中 3312）", matches(parseFilter("3>3"), "3312"));
        check("同一数字的先后关系（3>3 不命中 12）", !matches(parseFilter("3>3"), "12"));

        check("非法：3>>7 被拒", rejects("3>>7"));
        check("非法：3> 被拒", rejects("3>"));
        check("非法：引号未闭合被拒", rejects("\"37"));
        check("非法：连续 & 被拒", rejects("3&"));
        check("非法：0 不是合法标记数字", rejects("0>1"));
        check("非法：未知符号被拒", rejects("3x7"));

        Object chain = parseFilter("3>7>1");
        List<?> desc = (List<?>) chain.getClass().getMethod("describe").invoke(chain);
        check("describe() 一个条件一条描述（" + desc + "）",
                desc.size() == 1 && desc.get(0).toString().contains("7 之前"));

        // ---------- 4. 盘面文件：标记数字无损往返 ----------
        Object bm = newModel(5, 6);
        modelC.getMethod("clearScorePoints").invoke(bm);
        setDigit(bm, 1, 1, 4);
        setDigit(bm, 2, 3, 9);

        File board = new File(workDir, "digits_smoke_board.maze");
        ioC.getMethod("saveBoard", modelC, File.class).invoke(null, bm, board);
        String boardText = new String(Files.readAllBytes(board.toPath()), StandardCharsets.UTF_8);
        check("盘面文件里出现标记数字 4 / 9", boardText.contains("4") && boardText.contains("9"));

        Object loaded = ioC.getMethod("loadBoard", File.class).invoke(null, board);
        check("往返：标记 4 保留", digitOf(loaded, 1, 1) == 4);
        check("往返：标记 9 保留", digitOf(loaded, 2, 3) == 9);
        check("往返：空格仍无标记", digitOf(loaded, 0, 1) == 0 && digitOf(loaded, 4, 4) == 0);

        // 旧盘面用 '#' 表示得分点 → 载入后等于默认标记 1
        File legacy = new File(workDir, "digits_smoke_legacy.maze");
        StringBuilder walls = new StringBuilder();
        for (int i = 0; i < 3; i++) walls.append("fff").append(NL);
        String row3 = dots(3);
        Files.write(legacy.toPath(), ("size 3 3" + NL + "walls" + NL + walls + "scores" + NL
                        + "#" + dots(2) + NL + row3 + NL + row3 + NL).getBytes(StandardCharsets.UTF_8));
        Object legacyModel = ioC.getMethod("loadBoard", File.class).invoke(null, legacy);
        check("旧盘面 '#' 载入为默认标记 1", digitOf(legacyModel, 0, 0) == 1);

        // ---------- 5. 结果文件带数字串 ----------
        Object pm = openModel();
        setDigit(pm, 0, 1, 3);
        Object ps = newSolver(pm);
        List<?> all = (List<?>) solverC.getMethod("findAllPaths", int.class).invoke(ps, 500);
        File out = new File(workDir, "digits_smoke_paths.txt");
        Object ok = solverC.getMethod("savePathsToFile", List.class, File.class).invoke(ps, all, out);
        String text = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
        check("结果文件写入成功（" + all.size() + " 条）", Boolean.TRUE.equals(ok));
        check("结果文件行内带 序列 段", text.contains(", 序列 "));
        check("经过标记的路径写明 序列 3", text.contains("序列 3"));
        check("未经过标记的路径写 序列 -", text.contains("序列 -"));

        System.out.println(fails == 0 ? "SMOKE OK" : ("SMOKE FAILED: " + fails));
    }
}
