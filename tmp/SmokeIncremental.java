// @anchor: smoke_incremental_intro
// 临时校验脚本：验证"分多次续跑"与"一次跑满"结果逐条一致、无重复无遗漏、盘面变化自动从头、目标调小不缩水、内存预算闸门生效。
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SmokeIncremental {

    private static Class<?> modelC, genC, solverC;
    private static int NORTH, SOUTH, WEST, EAST;
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

    private static Object newGenerator(Object model) throws Exception {
        return genC.getConstructor(modelC).newInstance(model);
    }

    private static List<?> cont(Object solver, int target) throws Exception {
        return (List<?>) solverC.getMethod("continueFindAllPaths", int.class).invoke(solver, target);
    }

    private static List<?> find(Object solver, int target) throws Exception {
        return (List<?>) solverC.getMethod("findAllPaths", int.class).invoke(solver, target);
    }

    private static boolean solverBool(Object solver, String getter) throws Exception {
        return (Boolean) solverC.getMethod(getter).invoke(solver);
    }

    private static int solverInt(Object solver, String getter) throws Exception {
        return (Integer) solverC.getMethod(getter).invoke(solver);
    }

    private static int modelInt(Object model, String getter) throws Exception {
        return (Integer) modelC.getMethod(getter).invoke(model);
    }

    private static boolean hasWall(Object model, int r, int c, int dir) throws Exception {
        return (Boolean) modelC.getMethod("hasWall", int.class, int.class, int.class)
                .invoke(model, r, c, dir);
    }

    /** 路径编码 → 字符串：用于逐条比对（方向编码序列完全一致才算同一条） */
    private static String pathKey(Object path) {
        return String.valueOf(path);
    }

    /** 批量取路径的字符串指纹 */
    private static List<String> keys(List<?> paths) {
        List<String> out = new ArrayList<>();
        for (Object p : paths) out.add(pathKey(p));
        return out;
    }

    /** 校验一条路径：从起点出发，每步不越界、不穿墙、不重复经过同一格，且最终落在终点 */
    private static boolean validPath(Object model, Object path) throws Exception {
        int rows = modelInt(model, "getRows"), cols = modelInt(model, "getCols");
        int r = modelInt(model, "getStartR"), c = modelInt(model, "getStartC");
        int endR = modelInt(model, "getEndR"), endC = modelInt(model, "getEndC");
        boolean[][] seen = new boolean[rows][cols];
        seen[r][c] = true;
        for (Object o : (List<?>) path) {
            int code = (Integer) o;
            int dir, dr = 0, dc = 0;
            switch (code) {
                case 0: dir = NORTH; dr = -1; break;
                case 1: dir = SOUTH; dr = 1; break;
                case 2: dir = WEST; dc = -1; break;
                case 3: dir = EAST; dc = 1; break;
                default: return false;
            }
            if (hasWall(model, r, c, dir)) return false;
            int nr = r + dr, nc = c + dc;
            if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) return false;
            if (seen[nr][nc]) return false;
            seen[nr][nc] = true;
            r = nr;
            c = nc;
        }
        return r == endR && c == endC;
    }

    private static int memoryCap(int requested, long totalSteps, int paths, long budget) throws Exception {
        Method m = solverC.getDeclaredMethod("memoryLimitedTarget",
                int.class, long.class, int.class, long.class);
        m.setAccessible(true);
        return (Integer) m.invoke(null, requested, totalSteps, paths, budget);
    }

    public static void main(String[] args) throws Exception {
        File classes = findClasses();
        check("classpath 可定位 tmp/target/classes", classes != null && classes.isDirectory());
        if (classes == null) {
            System.out.println("SMOKE FAILED: 找不到编译产物");
            return;
        }

        URLClassLoader cl = new URLClassLoader(new URL[]{classes.toURI().toURL()},
                SmokeIncremental.class.getClassLoader());
        modelC = cl.loadClass("com.mazesolver.MazeModel");
        genC = cl.loadClass("com.mazesolver.MazeGenerator");
        solverC = cl.loadClass("com.mazesolver.MazeSolver");
        NORTH = (Integer) modelC.getField("NORTH").get(null);
        SOUTH = (Integer) modelC.getField("SOUTH").get(null);
        WEST = (Integer) modelC.getField("WEST").get(null);
        EAST = (Integer) modelC.getField("EAST").get(null);

        // ---------- 1. 多连通盘面：分 3 段续跑 vs 一次跑满 ----------
        Object open = newModel(9, 9);
        modelC.getMethod("clearInternalWalls").invoke(open);
        Object refSolver = newSolver(open);
        List<?> ref = find(refSolver, 300);
        List<String> refKeys = keys(ref);
        check("一次性跑满 300 条（实际 " + ref.size() + "）", ref.size() == 300);

        Object segSolver = newSolver(open);
        List<?> seg1 = cont(segSolver, 50);
        check("第 1 段：目标 50 条，实际 " + seg1.size(), seg1.size() == 50);
        List<?> seg2 = cont(segSolver, 120);
        check("第 2 段：目标 120 条，实际 " + seg2.size(), seg2.size() == 120);
        List<?> seg3 = cont(segSolver, 300);
        check("第 3 段：目标 300 条，实际 " + seg3.size(), seg3.size() == 300);
        check("续跑各段返回同一实例（面板持引用即可）", seg1 == seg2 && seg2 == seg3
                && seg3 == solverC.getMethod("getCollectedPaths").invoke(segSolver));

        List<String> segKeys = keys(seg3);
        check("分 3 段续跑与一次跑满逐条完全一致", segKeys.equals(refKeys));
        Set<String> unique = new HashSet<>(segKeys);
        check("续跑结果无重复（" + unique.size() + " / " + segKeys.size() + "）",
                unique.size() == segKeys.size());
        boolean allValid = true;
        for (Object p : seg3) {
            if (!validPath(open, p)) allValid = false;
        }
        check("续跑结果每条都是合法路径（不越界/不穿墙/不重格/落在终点）", allValid);
        check("续跑后仍标记有更多路径", solverBool(segSolver, "hasMorePaths")
                && solverBool(segSolver, "isLastEnumerationTruncated"));

        // ---------- 2. 目标调小：不缩水、不重跑 ----------
        List<?> smaller = cont(segSolver, 10);
        check("目标调小到 10 条：结果不缩水（仍 " + smaller.size() + " 条）", smaller.size() == 300);
        check("目标调小后仍与已有结果同实例", smaller == seg3);
        check("目标调小时记录的上限为目标值 10", solverInt(segSolver, "getLastEnumerationLimit") == 10);

        // ---------- 3. 完美迷宫：穷尽后不再新增 ----------
        Object perfect = newModel(12, 15);
        genC.getMethod("generate").invoke(newGenerator(perfect));
        Object perfectSolver = newSolver(perfect);
        List<?> one = find(perfectSolver, 2000);
        check("完美迷宫：枚举 1 条且未标记截断", one.size() == 1
                && !solverBool(perfectSolver, "isLastEnumerationTruncated"));
        check("完美迷宫：已穷尽（hasMorePaths=false）", !solverBool(perfectSolver, "hasMorePaths"));
        List<?> again = cont(perfectSolver, 500);
        check("穷尽后再调不新增（仍 " + again.size() + " 条）", again.size() == 1);
        check("穷尽后再次调用仍不标记截断", !solverBool(perfectSolver, "isLastEnumerationTruncated"));

        // ---------- 4. 盘面变化：自动作废旧现场，从头开始而不是接在后面 ----------
        Object mut = newModel(9, 9);
        modelC.getMethod("clearInternalWalls").invoke(mut);
        Object mutSolver = newSolver(mut);
        cont(mutSolver, 60);
        // 改一道内部墙（(0,0) 东侧到 (0,1)）：旧现场必须作废
        modelC.getMethod("setWallSide", int.class, int.class, int.class, boolean.class)
                .invoke(mut, 0, 0, EAST, true);
        List<?> afterWall = cont(mutSolver, 200);
        List<String> freshKeys = keys(find(newSolver(mut), 200));
        check("改墙后续跑 200 条 = 新盘面从头枚举的 200 条", keys(afterWall).equals(freshKeys));
        check("改墙后续跑条数为 200（旧结果已作废，未接在后面）", afterWall.size() == 200);

        // 改起点同样触发从头
        Object mut2 = newModel(9, 9);
        modelC.getMethod("clearInternalWalls").invoke(mut2);
        Object mut2Solver = newSolver(mut2);
        cont(mut2Solver, 40);
        modelC.getMethod("setStart", int.class, int.class).invoke(mut2, 1, 1);
        List<?> afterStart = cont(mut2Solver, 120);
        check("改起点后续跑 120 条 = 新盘面从头枚举的 120 条",
                keys(afterStart).equals(keys(find(newSolver(mut2), 120))));

        // ---------- 5. 显式作废：resetEnumeration ----------
        Object resetSolver = newSolver(mut);
        find(resetSolver, 80);
        solverC.getMethod("resetEnumeration").invoke(resetSolver);
        List<?> afterReset = find(resetSolver, 80);
        check("resetEnumeration 后从头枚举 80 条且与首次一致",
                keys(afterReset).equals(keys(find(newSolver(mut), 80))));

        // ---------- 6. 内存预算闸门 ----------
        int capA = memoryCap(100_000, 10_000L, 100, 1_000_000L);           // 平均 100 步 → 464 字节/条
        int expectedA = (int) (1_000_000L / (64L + 4L * 100L));
        check("内存预算：按实测平均步长收敛（" + capA + " == " + expectedA + "）", capA == expectedA);
        int capB = memoryCap(100_000, 10_000L, 100, 1L << 40);              // 预算极大 → 不限制
        check("内存预算充足时不收敛到请求值以下（" + capB + "）", capB == 100_000);
        int capC = memoryCap(100_000, 0L, 0, 1_000_000L);                   // 尚未枚举 → 按 0 步估（64 字节/条）
        check("尚未枚举时按最小单条开销估算（" + capC + "）", capC == 1_000_000L / 64L);
        int capD = memoryCap(100_000, 5_000L, 100, 0L);
        check("预算为 0 时目标收敛到 0", capD == 0);
        long est = (Long) solverC.getMethod("getEstimatedMemoryBytes").invoke(segSolver);
        check("已收录 300 条的内存估算为正（" + est + " 字节）", est > 0);
        check("常规预算下不会被内存闸门拦下", !solverBool(segSolver, "isLastEnumerationMemoryCapped"));

        // ---------- 7. 兼容旧口径 ----------
        Object compatSolver = newSolver(perfect);
        find(compatSolver, 2000);
        List<?> compatAgain = find(compatSolver, 3);
        check("完美迷宫 findAllPaths(2000) → findAllPaths(3)：仍 1 条且未截断",
                compatAgain.size() == 1 && !solverBool(compatSolver, "isLastEnumerationTruncated"));

        System.out.println(fails == 0 ? "SMOKE OK" : ("SMOKE FAILED: " + fails));
    }
}
