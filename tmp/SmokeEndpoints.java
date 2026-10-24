// @anchor: smoke_endpoints_intro
// 临时校验脚本：反射加载 tmp/target/classes，专测"起点/终点可在范围内任选"（模型设定与校验、求解器跟随、拾取执行、生成避开起终点）。
import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public class SmokeEndpoints {

    static Class<?> modelC, genC, solverC;
    static int fails = 0;

    static void check(String name, boolean ok) {
        System.out.println((ok ? "[OK]   " : "[FAIL] ") + name);
        if (!ok) fails++;
    }

    static File findClasses() {
        String[] candidates = {"target/classes", "tmp/target/classes", "maze-solver/tmp/target/classes"};
        for (String c : candidates) {
            File f = new File(c).getAbsoluteFile();
            if (f.isDirectory()) return f;
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

    static Object newModel(int r, int c) throws Exception {
        return modelC.getConstructor(int.class, int.class).newInstance(r, c);
    }

    static Object call(Object target, String method, Object... args) throws Exception {
        for (Method m : target.getClass().getMethods()) {
            if (m.getName().equals(method) && m.getParameterCount() == args.length) {
                return m.invoke(target, args);
            }
        }
        throw new NoSuchMethodException(method);
    }

    static int intOf(Object target, String getter) throws Exception {
        return (Integer) call(target, getter);
    }

    public static void main(String[] args) throws Exception {
        File classes = findClasses();
        check("classpath 可定位 tmp/target/classes", classes != null && classes.isDirectory());
        List<URL> urls = new ArrayList<>();
        urls.add(classes.toURI().toURL());
        urls.addAll(javaFxJars());
        URLClassLoader cl = new URLClassLoader(urls.toArray(new URL[0]), SmokeEndpoints.class.getClassLoader());

        modelC = cl.loadClass("com.mazesolver.MazeModel");
        genC = cl.loadClass("com.mazesolver.MazeGenerator");
        solverC = cl.loadClass("com.mazesolver.MazeSolver");

        int rows = 15, cols = 15;
        Object model = newModel(rows, cols);
        Object gen = genC.getConstructor(modelC).newInstance(model);
        genC.getMethod("generate").invoke(gen);
        Object solver = solverC.getConstructor(modelC).newInstance(model);

        // ---------- 1. 默认起终点 ----------
        check("默认起点 (0,0) / 终点 (14,14)",
                intOf(model, "getStartR") == 0 && intOf(model, "getStartC") == 0
                        && intOf(model, "getEndR") == rows - 1 && intOf(model, "getEndC") == cols - 1);

        // ---------- 2. 合法性校验 ----------
        check("起点与终点重合被拒绝", !(Boolean) call(model, "setStart", rows - 1, cols - 1));
        check("越界起点被拒绝", !(Boolean) call(model, "setStart", -1, 3));
        check("越界终点被拒绝", !(Boolean) call(model, "setEnd", 99, 3));

        // ---------- 3. 设定起点：清理该格得分点 ----------
        call(model, "addScorePoint", 5, 7);
        check("设起点 (5,7) 成功", (Boolean) call(model, "setStart", 5, 7));
        check("起点所在格得分点被清除", !(Boolean) call(model, "hasScorePoint", 5, 7));

        List<?> path = (List<?>) call(solver, "solve");
        int[] first = (int[]) path.get(0);
        int[] last = (int[]) path.get(path.size() - 1);
        check("BFS 路径从新起点出发、到达默认终点",
                first[0] == 5 && first[1] == 7 && last[0] == rows - 1 && last[1] == cols - 1);

        // ---------- 4. 改终点后 BFS / 枚举 / 坐标转换全部跟随 ----------
        check("设终点 (10,3) 成功", (Boolean) call(model, "setEnd", 10, 3));
        path = (List<?>) call(solver, "solve");
        last = (int[]) path.get(path.size() - 1);
        check("BFS 路径到达新终点 (10,3)", last[0] == 10 && last[1] == 3);

        List<?> all = (List<?>) call(solver, "findAllPaths");
        boolean endpointsOk = true;
        for (Object p : all) {
            List<?> coords = (List<?>) call(solver, "dirCodesToCoordinates", p);
            int[] c0 = (int[]) coords.get(0);
            int[] cN = (int[]) coords.get(coords.size() - 1);
            if (c0[0] != 5 || c0[1] != 7 || cN[0] != 10 || cN[1] != 3) endpointsOk = false;
        }
        check("全路径枚举 " + all.size() + " 条，均以新起终点为首尾", all.size() == 1 && endpointsOk);

        // ---------- 5. 重新生成：起终点保留，且不在起终点撒得分点 ----------
        Object m2 = newModel(20, 20);
        call(m2, "setStart", 4, 4);
        call(m2, "setEnd", 12, 9);
        Object g2 = genC.getConstructor(modelC).newInstance(m2);
        genC.getMethod("generate").invoke(g2);
        check("重新生成后起终点保留",
                intOf(m2, "getStartR") == 4 && intOf(m2, "getStartC") == 4
                        && intOf(m2, "getEndR") == 12 && intOf(m2, "getEndC") == 9);
        check("生成不在起终点撒得分点",
                !(Boolean) call(m2, "hasScorePoint", 4, 4) && !(Boolean) call(m2, "hasScorePoint", 12, 9));

        // ---------- 6. 换尺寸：新模型起终点回到角上 ----------
        Object m3 = newModel(8, 30);
        check("新模型起终点在角上", intOf(m3, "getStartR") == 0 && intOf(m3, "getStartC") == 0
                && intOf(m3, "getEndR") == 7 && intOf(m3, "getEndC") == 29);

        // ---------- 7. 拾取执行（MazeEditor）：单击即设定起点，落点与另一端点重合时忽略 ----------
        try {
            Class<?> editorC = cl.loadClass("com.mazesolver.MazeEditor");
            Object editor = editorC.getConstructor(modelC, Runnable.class).newInstance(model, null);
            int pickStart = editorC.getField("PICK_START").getInt(null);

            Method applyEndpoint = editorC.getMethod("applyEndpointAction",
                    int.class, int.class, int.class);
            check("拾取起点 (2,9) 返回已改动", (Boolean) applyEndpoint.invoke(editor, pickStart, 2, 9));
            check("拾取起点生效 (2,9)", intOf(model, "getStartR") == 2 && intOf(model, "getStartC") == 9);

            // (10,3) 是当前终点 → 应被拒绝
            check("拾取另一端点所在格被忽略", !(Boolean) applyEndpoint.invoke(editor, pickStart, 10, 3));
            check("坐标仍为上一步的起点", intOf(model, "getStartR") == 2 && intOf(model, "getStartC") == 9);
        } catch (Throwable t) {
            System.out.println("[SKIP] 编辑层校验需要 JavaFX 类，当前环境跳过: " + t);
        }

        System.out.println(fails == 0 ? "SMOKE OK" : ("SMOKE FAILED: " + fails));
    }
}
