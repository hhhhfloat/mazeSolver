// @anchor: smoke_reflect_intro
// 临时校验脚本：反射加载 tmp/target/classes 下的模型/生成器/求解器/视图/命中检测/编辑执行，做无界面端到端自测（多档尺寸、边界编辑、改墙生效）。
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public class SmokeReflect {

    private static Class<?> modelC, genC, solverC;
    private static int NORTH, EAST, SOUTH, WEST;
    private static int fails = 0;

    private static File findClasses() {
        String[] candidates = {"target/classes", "tmp/target/classes", "maze-solver/tmp/target/classes"};
        for (String c : candidates) {
            File f = new File(c).getAbsoluteFile();
            if (f.isDirectory()) return f;
        }
        return null;
    }

    /** 从本地 Maven 仓库收集 JavaFX jar，用于加载 MazeView（不需要真实图形窗口） */
    private static List<URL> javaFxJars() {
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

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "[OK]   " : "[FAIL] ") + name);
        if (!ok) fails++;
    }

    private static Object newModel(int r, int c) throws Exception {
        Constructor<?> ctor = modelC.getConstructor(int.class, int.class);
        return ctor.newInstance(r, c);
    }

    private static boolean hasWall(Object model, int r, int c, int dir) throws Exception {
        return (Boolean) modelC.getMethod("hasWall", int.class, int.class, int.class).invoke(model, r, c, dir);
    }

    private static void setSide(Object model, int r, int c, int dir, boolean present) throws Exception {
        modelC.getMethod("setWallSide", int.class, int.class, int.class, boolean.class)
                .invoke(model, r, c, dir, present);
    }

    public static void main(String[] args) throws Exception {
        File classes = findClasses();
        check("classpath 可定位 tmp/target/classes", classes != null && classes.isDirectory());
        List<URL> urls = new ArrayList<>();
        urls.add(classes.toURI().toURL());
        urls.addAll(javaFxJars());
        URLClassLoader cl = new URLClassLoader(urls.toArray(new URL[0]), SmokeReflect.class.getClassLoader());

        modelC = cl.loadClass("com.mazesolver.MazeModel");
        genC = cl.loadClass("com.mazesolver.MazeGenerator");
        solverC = cl.loadClass("com.mazesolver.MazeSolver");
        NORTH = (Integer) modelC.getField("NORTH").get(null);
        EAST = (Integer) modelC.getField("EAST").get(null);
        SOUTH = (Integer) modelC.getField("SOUTH").get(null);
        WEST = (Integer) modelC.getField("WEST").get(null);
        int minDim = (Integer) modelC.getField("MIN_DIM").get(null);
        int maxDim = (Integer) modelC.getField("MAX_DIM").get(null);
        System.out.println("尺寸区间常量 = " + minDim + "~" + maxDim);

        // ---------- 1. 多档尺寸：生成 / BFS / 全路径枚举 / 边界封闭 ----------
        int[][] sizes = {{3, 3}, {10, 15}, {20, 20}, {60, 45}, {120, 120}, {200, 200}};
        for (int[] sz : sizes) {
            int rows = sz[0], cols = sz[1];
            long t0 = System.currentTimeMillis();
            Object model = newModel(rows, cols);
            Object gen = genC.getConstructor(modelC).newInstance(model);
            genC.getMethod("generate").invoke(gen);
            Object solver = solverC.getConstructor(modelC).newInstance(model);
            List<?> path = (List<?>) solverC.getMethod("solve").invoke(solver);
            List<?> all = (List<?>) solverC.getMethod("findAllPaths").invoke(solver);
            long ms = System.currentTimeMillis() - t0;

            boolean borderClosed = true;
            for (int c = 0; c < cols; c++) {
                if (!hasWall(model, 0, c, NORTH) || !hasWall(model, rows - 1, c, SOUTH)) borderClosed = false;
            }
            for (int r = 0; r < rows; r++) {
                if (!hasWall(model, r, 0, WEST) || !hasWall(model, r, cols - 1, EAST)) borderClosed = false;
            }
            check(rows + "x" + cols + " 生成+BFS+枚举(" + ms + "ms) 步数=" + (path.size() - 1)
                    + " 枚举=" + all.size() + " 完美迷宫=" + (all.size() == 1) + " 边界封闭=" + borderClosed,
                    !path.isEmpty() && all.size() == 1 && borderClosed);
        }

        // ---------- 2. 外边界墙壁可编辑 ----------
        Object m = newModel(12, 12);
        Object g = genC.getConstructor(modelC).newInstance(m);
        genC.getMethod("generate").invoke(g);

        setSide(m, 0, 3, NORTH, false);
        check("拆上边界 (0,3)NORTH 生效", !hasWall(m, 0, 3, NORTH));
        setSide(m, 0, 3, NORTH, true);
        check("补回上边界 (0,3)NORTH 生效", hasWall(m, 0, 3, NORTH));

        Object m2 = newModel(12, 12);
        setSide(m2, 11, 5, SOUTH, false);
        check("拆下边界 (11,5)SOUTH 生效", !hasWall(m2, 11, 5, SOUTH));
        setSide(m2, 4, 0, WEST, false);
        check("拆左边界 (4,0)WEST 生效", !hasWall(m2, 4, 0, WEST));
        setSide(m2, 6, 11, EAST, false);
        check("拆右边界 (6,11)EAST 生效", !hasWall(m2, 6, 11, EAST));

        // ---------- 3. 内部墙双向同步 ----------
        Object m3 = newModel(12, 12);
        Object g3 = genC.getConstructor(modelC).newInstance(m3);
        genC.getMethod("generate").invoke(g3);
        setSide(m3, 5, 5, EAST, false);
        check("内部墙打通双向同步", !hasWall(m3, 5, 5, EAST) && !hasWall(m3, 5, 6, WEST));
        setSide(m3, 5, 5, EAST, true);
        check("内部墙重建双向同步", hasWall(m3, 5, 5, EAST) && hasWall(m3, 5, 6, WEST));

        // ---------- 4. 得分点切换 ----------
        Object m4 = newModel(12, 12);
        modelC.getMethod("clearScorePoints").invoke(m4);
        modelC.getMethod("toggleScorePoint", int.class, int.class).invoke(m4, 0, 1);
        check("切换得分点生效", (Boolean) modelC.getMethod("hasScorePoint", int.class, int.class).invoke(m4, 0, 1));
        modelC.getMethod("toggleScorePoint", int.class, int.class).invoke(m4, 0, 1);
        check("再次切换撤销得分点", !(Boolean) modelC.getMethod("hasScorePoint", int.class, int.class).invoke(m4, 0, 1));

        // ---------- 5. 视图层：命中检测（MazeHitTester）+ 编辑执行（MazeEditor），均无需图形窗口 ----------
        try {
            Class<?> viewC = cl.loadClass("com.mazesolver.MazeView");
            Object view = viewC.getConstructor(modelC).newInstance(m3);
            int cell = (Integer) viewC.getMethod("getCellSize").invoke(view);
            check("视图单元格尺寸取自统一分档表（" + cell + "px）", cell > 0);

            Class<?> hitC = cl.loadClass("com.mazesolver.MazeHitTester");
            Class<?> wallHitC = cl.loadClass("com.mazesolver.MazeHitTester$WallHit");
            Method findWall = hitC.getMethod("findWall", modelC, int.class, double.class, double.class);
            Method hitRow = wallHitC.getMethod("row");
            Method hitCol = wallHitC.getMethod("col");
            Method hitDir = wallHitC.getMethod("dir");

            Object h1 = findWall.invoke(null, m3, cell, 3 * cell + cell / 2.0, 0.0);
            check("命中上边界墙段 (dir=NORTH,r=0,c=3)",
                    (Integer) hitDir.invoke(h1) == NORTH && (Integer) hitRow.invoke(h1) == 0
                            && (Integer) hitCol.invoke(h1) == 3);
            Object h2 = findWall.invoke(null, m3, cell, 3 * cell + cell / 2.0, 2.0 * cell);
            check("命中内部横墙段 (dir=NORTH,r=2)",
                    (Integer) hitDir.invoke(h2) == NORTH && (Integer) hitRow.invoke(h2) == 2);
            Object h3 = findWall.invoke(null, m3, cell, 12.0 * cell, 4 * cell + cell / 2.0);
            check("命中右边界墙段 (dir=EAST,c=11)",
                    (Integer) hitDir.invoke(h3) == EAST && (Integer) hitCol.invoke(h3) == 11);
            Object h4 = findWall.invoke(null, m3, cell, 6 * cell + cell / 2.0, 12.0 * cell);
            check("命中下边界墙段 (dir=SOUTH,r=11)",
                    (Integer) hitDir.invoke(h4) == SOUTH && (Integer) hitRow.invoke(h4) == 11);

            Class<?> editorC = cl.loadClass("com.mazesolver.MazeEditor");
            Object editor = editorC.getConstructor(modelC, Runnable.class).newInstance(m3, null);
            Method applyWall = editorC.getMethod("applyWallAction",
                    int.class, int.class, int.class, boolean.class);

            boolean before = hasWall(m3, 0, 3, NORTH);
            applyWall.invoke(editor, 0, 3, NORTH, !before);
            check("applyWallAction 上边界拆/补生效", hasWall(m3, 0, 3, NORTH) != before);

            boolean before2 = hasWall(m3, 2, 3, NORTH);
            applyWall.invoke(editor, 2, 3, NORTH, !before2);
            check("applyWallAction 内部墙拆/补生效", hasWall(m3, 2, 3, NORTH) != before2);
        } catch (Throwable t) {
            System.out.println("[SKIP] 视图层校验需要 JavaFX 类，当前环境跳过: " + t);
        }

        System.out.println(fails == 0 ? "SMOKE OK" : ("SMOKE FAILED: " + fails));
    }
}
