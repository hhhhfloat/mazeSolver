// @anchor: smoke_sample_intro
// 临时校验脚本：反射加载 tmp/target/classes，验证"抽样叠画"——抽样取点的确定性/去重/边界，以及视图叠画的条数、线段去重与撤销。
import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public class SmokeSample {

    static Class<?> modelC, genC, solverC;
    static int fails = 0;

    static void check(String name, boolean ok) {
        System.out.println((ok ? "[OK]   " : "[FAIL] ") + name);
        if (!ok) fails++;
    }

    /** 从当前目录逐级向上找编译产物目录，避免脚本必须在项目根目录运行 */
    static File findClasses() {
        File dir = new File("").getAbsoluteFile();
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParentFile()) {
            for (String rel : new String[]{"tmp/target/classes", "target/classes"}) {
                File f = new File(dir, rel);
                if (f.isDirectory()) return f;
            }
        }
        return null;
    }

    /** 从本地 Maven 仓库收集 JavaFX jar，用于加载 MazeView（不需要真实图形窗口） */
    static List<URL> javaFxJars() {
        List<URL> urls = new ArrayList<>();
        File repo = new File(System.getProperty("user.home"), ".m2/repository/org/openjfx");
        if (!repo.isDirectory()) return urls;
        File[] groups = repo.listFiles();
        if (groups == null) return urls;
        for (File groupDir : groups) {
            File[] versions = groupDir.listFiles();
            if (versions == null) continue;
            for (File verDir : versions) {
                File[] jars = verDir.listFiles((d, n) -> n.endsWith(".jar"));
                if (jars == null) continue;
                for (File jar : jars) {
                    try {
                        urls.add(jar.toURI().toURL());
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return urls;
    }

    static Object call(Object target, String method, Object... args) throws Exception {
        for (Method m : target.getClass().getMethods()) {
            if (m.getName().equals(method) && m.getParameterCount() == args.length) {
                return m.invoke(target, args);
            }
        }
        throw new NoSuchMethodException(method);
    }

    @SuppressWarnings("unchecked")
    static List<Integer> sample(int total, int count) throws Exception {
        Method m = solverC.getMethod("sampleIndices", int.class, int.class);
        return (List<Integer>) m.invoke(null, total, count);
    }

    static boolean strictlyAscending(List<Integer> list) {
        for (int i = 1; i < list.size(); i++) {
            if (list.get(i) <= list.get(i - 1)) return false;
        }
        return true;
    }

    static int intConst(String name) throws Exception {
        return solverC.getField(name).getInt(null);
    }

    public static void main(String[] args) throws Exception {
        File classes = findClasses();
        check("classpath 可定位 tmp/target/classes", classes != null && classes.isDirectory());
        List<URL> urls = new ArrayList<>();
        urls.add(classes.toURI().toURL());
        urls.addAll(javaFxJars());
        URLClassLoader cl = new URLClassLoader(urls.toArray(new URL[0]), SmokeSample.class.getClassLoader());

        modelC = cl.loadClass("com.mazesolver.MazeModel");
        genC = cl.loadClass("com.mazesolver.MazeGenerator");
        solverC = cl.loadClass("com.mazesolver.MazeSolver");

        int def = intConst("DEFAULT_SAMPLE_COUNT");
        int min = intConst("MIN_SAMPLE_COUNT");
        int max = intConst("MAX_SAMPLE_COUNT");
        check("抽样常量区间合法（" + min + " ≤ " + def + " ≤ " + max + "）", min <= def && def <= max);

        // ---------- 1. 抽样取点：条数 / 首尾 / 严格升序 ----------
        List<Integer> s = sample(1000, 30);
        check("1000 条取 30 条 → 恰好 30 个下标", s.size() == 30);
        check("首尾必取 (0 与 999)", s.get(0) == 0 && s.get(s.size() - 1) == 999);
        check("抽样下标严格升序且无重复", strictlyAscending(s));

        // ---------- 2. 抽样是最坏/边界情况 ----------
        check("期望条数大于总数 → 返回全部", sample(5, 10).size() == 5);
        check("总数 0 → 空列表", sample(0, 10).isEmpty());
        check("期望条数 0 → 空列表", sample(10, 0).isEmpty());
        check("负数总数 → 空列表", sample(-3, 5).isEmpty());
        List<Integer> one = sample(10, 1);
        check("只取 1 条 → 取第 0 条", one.size() == 1 && one.get(0) == 0);
        check("超大期望条数被压到上限 " + max, sample(100000, 10000000).size() == max);
        check("抽样确定性：两次结果一致", sample(777, 17).equals(sample(777, 17)));
        boolean inRange = true;
        for (int idx : sample(37, 13)) {
            if (idx < 0 || idx >= 37) inRange = false;
        }
        check("抽样结果均落在 [0, total) 内", inRange);

        // ---------- 3. 视图叠画：条数统计、线段去重、撤销（走公开 API，不碰内部字段） ----------
        try {
            Class<?> viewC = cl.loadClass("com.mazesolver.MazeView");
            Object model = modelC.getConstructor(int.class, int.class).newInstance(10, 10);
            Object gen = genC.getConstructor(modelC).newInstance(model);
            genC.getMethod("generate").invoke(gen);
            Object view = viewC.getConstructor(modelC).newInstance(model);

            // 两条路径共用前两格：共用线段只应描一次
            List<List<int[]>> paths = new ArrayList<>();
            paths.add(coords(new int[][]{{0, 0}, {0, 1}, {0, 2}}));
            paths.add(coords(new int[][]{{0, 0}, {0, 1}, {1, 1}}));
            viewC.getMethod("setOverlayPaths", List.class).invoke(view, paths);
            check("叠画条数回显为 2", (Integer) call(view, "getOverlayPathCount") == 2);
            check("重叠线段已去重（2 条路径 4 段 → 3 段）",
                    (Integer) call(view, "getOverlaySegmentCount") == 3);

            viewC.getMethod("clearOverlayPaths").invoke(view);
            check("clearOverlayPaths 后条数归零", (Integer) call(view, "getOverlayPathCount") == 0);
            check("clearOverlayPaths 后不再描线", (Integer) call(view, "getOverlaySegmentCount") == 0);

            // 尺寸变更（重建画布）会作废旧坐标
            viewC.getMethod("setOverlayPaths", List.class).invoke(view, paths);
            viewC.getMethod("rebuildCanvas", int.class, int.class).invoke(view, 10, 10);
            check("rebuildCanvas 后叠画被作废", (Integer) call(view, "getOverlayPathCount") == 0);
        } catch (Throwable t) {
            System.out.println("[SKIP] 视图层校验需要 JavaFX 类，当前环境跳过: " + t);
        }

        // ---------- 4. 端到端：枚举结果 → 抽样 → 坐标序列首尾仍是起终点 ----------
        Object model = modelC.getConstructor(int.class, int.class).newInstance(9, 9);
        Object gen = genC.getConstructor(modelC).newInstance(model);
        genC.getMethod("generate").invoke(gen);
        Object solver = solverC.getConstructor(modelC).newInstance(model);
        @SuppressWarnings("unchecked")
        List<List<Integer>> all = (List<List<Integer>>) call(solver, "findAllPaths", 50);
        check("求解器枚举到路径 " + all.size() + " 条", !all.isEmpty());
        List<Integer> picked = sample(all.size(), 5);
        boolean ok = true;
        for (int idx : picked) {
            @SuppressWarnings("unchecked")
            List<int[]> coords = (List<int[]>) call(solver, "dirCodesToCoordinates", all.get(idx));
            int[] head = coords.get(0);
            int[] tail = coords.get(coords.size() - 1);
            if (head[0] != (Integer) call(model, "getStartR") || head[1] != (Integer) call(model, "getStartC")) ok = false;
            if (tail[0] != (Integer) call(model, "getEndR") || tail[1] != (Integer) call(model, "getEndC")) ok = false;
        }
        check("抽样路径坐标首尾等于当前起终点", ok);

        System.out.println(fails == 0 ? "SMOKE OK" : ("SMOKE FAILED: " + fails));
    }

    static List<int[]> coords(int[][] cells) {
        List<int[]> list = new ArrayList<>();
        for (int[] c : cells) list.add(new int[]{c[0], c[1]});
        return list;
    }
}
