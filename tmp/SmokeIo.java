// @anchor: smoke_io_intro
// 临时校验脚本：验证盘面保存/载入往返一致、非法文件被拒、枚举上限闸门与结果文件的上下文头部。
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public class SmokeIo {

    private static Class<?> modelC, genC, solverC, ioC;
    private static int NORTH, SOUTH;
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

    private static boolean hasWall(Object model, int r, int c, int dir) throws Exception {
        return (Boolean) modelC.getMethod("hasWall", int.class, int.class, int.class).invoke(model, r, c, dir);
    }

    private static int wallMask(Object model, int r, int c) throws Exception {
        return (Integer) modelC.getMethod("getWallMask", int.class, int.class).invoke(model, r, c);
    }

    private static boolean hasScore(Object model, int r, int c) throws Exception {
        return (Boolean) modelC.getMethod("hasScorePoint", int.class, int.class).invoke(model, r, c);
    }

    private static int intOf(Object model, String getter) throws Exception {
        return (Integer) modelC.getMethod(getter).invoke(model);
    }

    /** 生成一行 len 个"无得分点"标记（避免在源码里写字面量点号串） */
    private static String emptyRow(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append('.');
        return sb.toString();
    }

    /** 统计结果文件里 "Path N" 的行数 */
    private static int countPathLines(String text) {
        int n = 0;
        for (String line : text.split("\n")) {
            if (line.startsWith("Path ")) n++;
        }
        return n;
    }

    public static void main(String[] args) throws Exception {
        File classes = findClasses();
        check("classpath 可定位 tmp/target/classes", classes != null && classes.isDirectory());
        if (classes == null) {
            System.out.println("SMOKE FAILED: 找不到编译产物");
            return;
        }
        File workDir = classes.getParentFile().getParentFile(); // 项目下的 tmp/

        URLClassLoader cl = new URLClassLoader(new URL[]{classes.toURI().toURL()},
                SmokeIo.class.getClassLoader());
        modelC = cl.loadClass("com.mazesolver.MazeModel");
        genC = cl.loadClass("com.mazesolver.MazeGenerator");
        solverC = cl.loadClass("com.mazesolver.MazeSolver");
        ioC = cl.loadClass("com.mazesolver.MazeIO");
        NORTH = (Integer) modelC.getField("NORTH").get(null);
        SOUTH = (Integer) modelC.getField("SOUTH").get(null);

        // ---------- 1. 盘面往返：改动后存盘 → 载入 → 逐格比较 ----------
        Object model = newModel(12, 15);
        Object gen = genC.getConstructor(modelC).newInstance(model);
        genC.getMethod("generate").invoke(gen);

        // 改外边界（缺口）、改内部墙、改得分点、改起终点
        modelC.getMethod("setWallSide", int.class, int.class, int.class, boolean.class)
                .invoke(model, 0, 4, NORTH, false);
        modelC.getMethod("setWallSide", int.class, int.class, int.class, boolean.class)
                .invoke(model, 11, 7, SOUTH, false);
        modelC.getMethod("setWallSide", int.class, int.class, int.class, boolean.class)
                .invoke(model, 5, 5, (Integer) modelC.getField("EAST").get(null), true);
        modelC.getMethod("clearScorePoints").invoke(model);
        modelC.getMethod("addScorePoint", int.class, int.class).invoke(model, 2, 3);
        modelC.getMethod("addScorePoint", int.class, int.class).invoke(model, 9, 9);
        modelC.getMethod("setStart", int.class, int.class).invoke(model, 3, 4);
        modelC.getMethod("setEnd", int.class, int.class).invoke(model, 8, 11);

        File board = new File(workDir, "io_smoke_board.maze");
        ioC.getMethod("saveBoard", modelC, File.class).invoke(null, model, board);
        check("盘面文件已生成（" + board.length() + " 字节）", board.isFile() && board.length() > 0);

        Object loaded = ioC.getMethod("loadBoard", File.class).invoke(null, board);
        int rows = intOf(model, "getRows"), cols = intOf(model, "getCols");
        check("往返：尺寸一致 " + intOf(loaded, "getRows") + "x" + intOf(loaded, "getCols"),
                intOf(loaded, "getRows") == rows && intOf(loaded, "getCols") == cols);

        boolean sameWalls = true;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (wallMask(model, r, c) != wallMask(loaded, r, c)) sameWalls = false;
            }
        }
        check("往返：全部墙壁掩码一致", sameWalls);

        boolean sameScores = true;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (hasScore(model, r, c) != hasScore(loaded, r, c)) sameScores = false;
            }
        }
        check("往返：得分点一致", sameScores);
        check("往返：起终点一致 (" + intOf(loaded, "getStartR") + "," + intOf(loaded, "getStartC")
                        + ")->(" + intOf(loaded, "getEndR") + "," + intOf(loaded, "getEndC") + ")",
                intOf(loaded, "getStartR") == 3 && intOf(loaded, "getStartC") == 4
                        && intOf(loaded, "getEndR") == 8 && intOf(loaded, "getEndC") == 11);
        check("往返：外边界缺口被保留", !hasWall(loaded, 0, 4, NORTH) && !hasWall(loaded, 11, 7, SOUTH));

        Object solver1 = newSolver(model);
        Object solver2 = newSolver(loaded);
        int steps1 = ((List<?>) solverC.getMethod("solve").invoke(solver1)).size();
        int steps2 = ((List<?>) solverC.getMethod("solve").invoke(solver2)).size();
        check("往返：BFS 步数一致（" + steps1 + "）", steps1 == steps2);

        // ---------- 2. 非法盘面被拒 ----------
        File bad = new File(workDir, "io_smoke_bad.maze");
        String empty = emptyRow(2);
        Files.write(bad.toPath(), ("size 2 2\nwalls\nff\nff\nscores\n" + empty + "\n" + empty + "\n")
                .getBytes(StandardCharsets.UTF_8));
        boolean rejected = false;
        try {
            ioC.getMethod("loadBoard", File.class).invoke(null, bad);
        } catch (InvocationTargetException e) {
            rejected = e.getCause() instanceof java.io.IOException;
        }
        check("非法尺寸（2x2，低于下限）被拒绝且抛出 IOException", rejected);

        File broken = new File(workDir, "io_smoke_bad2.maze");
        Files.write(broken.toPath(), "size 5 5\nwalls\nfff\n".getBytes(StandardCharsets.UTF_8));
        boolean rejected2 = false;
        try {
            ioC.getMethod("loadBoard", File.class).invoke(null, broken);
        } catch (InvocationTargetException e) {
            rejected2 = e.getCause() instanceof java.io.IOException;
        }
        check("墙壁行数不符被拒绝", rejected2);

        // ---------- 3. 枚举上限闸门 ----------
        Object open = newModel(9, 9);
        modelC.getMethod("clearInternalWalls").invoke(open);
        Object openSolver = newSolver(open);
        List<?> limited = (List<?>) solverC.getMethod("findAllPaths", int.class).invoke(openSolver, 50);
        boolean truncatedFlag = (Boolean) solverC.getMethod("isLastEnumerationTruncated").invoke(openSolver);
        int usedLimit = (Integer) solverC.getMethod("getLastEnumerationLimit").invoke(openSolver);
        check("空旷 9x9 上限 50：条数=" + limited.size() + " 截断=" + truncatedFlag,
                limited.size() == 50 && truncatedFlag && usedLimit == 50);

        Object perfect = newModel(12, 15);
        Object g2 = genC.getConstructor(modelC).newInstance(perfect);
        genC.getMethod("generate").invoke(g2);
        Object perfectSolver = newSolver(perfect);
        List<?> all = (List<?>) solverC.getMethod("findAllPaths", int.class).invoke(perfectSolver, 2000);
        boolean perfectTruncated = (Boolean) solverC.getMethod("isLastEnumerationTruncated").invoke(perfectSolver);
        check("完美迷宫：枚举 1 条且未被判定截断", all.size() == 1 && !perfectTruncated);

        // 上限极大时（远超实际条数）也不应误标截断
        List<?> all2 = (List<?>) solverC.getMethod("findAllPaths", int.class).invoke(perfectSolver, 3);
        check("完美迷宫上限 3：1 条且未截断", all2.size() == 1
                && !(Boolean) solverC.getMethod("isLastEnumerationTruncated").invoke(perfectSolver));

        // @anchor: smoke_io_result_file
        // 结果文件：上下文头部 + 每行一条路径（行内顺序为 步数、分数、序列）
        // ---------- 4. 结果文件：上下文头部 + 每行一条路径 ----------
        File out = new File(workDir, "io_smoke_paths.txt");
        Object ok = solverC.getMethod("savePathsToFile", List.class, File.class)
                .invoke(openSolver, limited, out);
        String text = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
        check("结果文件写入成功", Boolean.TRUE.equals(ok) && out.isFile());
        check("结果文件含尺寸/起终点/上限/截断/条数头部",
                text.contains("# size 9 9") && text.contains("# start 0 0") && text.contains("# end 8 8")
                        && text.contains("# limit 50") && text.contains("# truncated true")
                        && text.contains("# count 50"));
        check("结果文件每行一条路径（" + countPathLines(text) + " 行）", countPathLines(text) == 50);
        check("结果文件行内含步数/得分/序列", text.contains("步, ") && text.contains("分, 序列 "));

        // 载入用的盘面文件也应是纯文本（含头部标记）
        String boardText = new String(Files.readAllBytes(board.toPath()), StandardCharsets.UTF_8);
        check("盘面文件含格式标记与 walls/scores 段",
                boardText.contains("MazeSolver Board v1") && boardText.contains("walls")
                        && boardText.contains("scores"));

        // @anchor: smoke_io_result_file_end
        System.out.println(fails == 0 ? "SMOKE OK" : ("SMOKE FAILED: " + fails));
    }
}
