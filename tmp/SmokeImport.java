// @anchor: smoke_import_intro
// 临时校验脚本：专测"盘面导入"——maps/ 下的示例盘面能正常载入、以 '#' 开头的得分点行不被当注释、
// 内部墙双向归并、往返保存一致、非法文件仍被拒。
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public class SmokeImport {

    private static Class<?> modelC, solverC, ioC;
    private static int NORTH, EAST, SOUTH, WEST;
    private static int fails = 0;

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "[OK]   " : "[FAIL] ") + name);
        if (!ok) fails++;
    }

    /** 由工作目录向上找到含 pom.xml 的项目根（用于定位 maps/ 与 tmp/target） */
    private static File projectRoot() {
        File d = new File("").getAbsoluteFile();
        while (d != null && !new File(d, "pom.xml").isFile()) d = d.getParentFile();
        return d == null ? new File("").getAbsoluteFile() : d;
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

    /** 拼一行矩阵文本：逐字符拼接，避免在源码里出现连续点号 */
    private static String row(char... cells) {
        StringBuilder sb = new StringBuilder(cells.length + 1);
        for (char ch : cells) sb.append(ch);
        return sb.append('\n').toString();
    }

    public static void main(String[] args) throws Exception {
        File root = projectRoot();
        File classes = new File(root, "tmp/target/classes");
        check("classpath 可定位 tmp/target/classes", classes.isDirectory());
        if (!classes.isDirectory()) {
            System.out.println("SMOKE FAILED: 找不到编译产物");
            return;
        }

        URLClassLoader cl = new URLClassLoader(new URL[]{classes.toURI().toURL()},
                SmokeImport.class.getClassLoader());
        modelC = cl.loadClass("com.mazesolver.MazeModel");
        solverC = cl.loadClass("com.mazesolver.MazeSolver");
        ioC = cl.loadClass("com.mazesolver.MazeIO");
        NORTH = (Integer) modelC.getField("NORTH").get(null);
        EAST = (Integer) modelC.getField("EAST").get(null);
        SOUTH = (Integer) modelC.getField("SOUTH").get(null);
        WEST = (Integer) modelC.getField("WEST").get(null);

        // ---------- 1. 示例盘面：maps/maze_12x12.maze ----------
        File sample = new File(root, "maps/maze_12x12.maze");
        check("示例盘面存在: " + sample.getPath(), sample.isFile());
        if (!sample.isFile()) {
            System.out.println("SMOKE FAILED: 缺少示例盘面");
            return;
        }

        Object model = ioC.getMethod("loadBoard", File.class).invoke(null, sample);
        check("示例盘面载入成功 " + intOf(model, "getRows") + "x" + intOf(model, "getCols"),
                intOf(model, "getRows") == 12 && intOf(model, "getCols") == 12);
        check("起终点取自文件 (5,0) -> (5,11)",
                intOf(model, "getStartR") == 5 && intOf(model, "getStartC") == 0
                        && intOf(model, "getEndR") == 5 && intOf(model, "getEndC") == 11);

        // 该文件第 2 行得分点为 "#....#..#..."：以 '#' 开头，绝不能被当成注释整行丢掉
        check("以 '#' 开头的得分点行未被误判为注释",
                hasScore(model, 1, 0) && hasScore(model, 1, 5) && hasScore(model, 1, 8)
                        && !hasScore(model, 1, 1));

        int scoreCount = 0;
        for (int r = 0; r < 12; r++) {
            for (int c = 0; c < 12; c++) {
                if (hasScore(model, r, c)) scoreCount++;
            }
        }
        check("得分点计数与文件一致（" + scoreCount + " 个）", scoreCount == 8);

        // 文件里成对墙壁可能只写了一侧，载入时应归并为双向一致，否则"看得见墙却走得过去"
        boolean consistent = true;
        for (int r = 0; r < 12; r++) {
            for (int c = 0; c < 12; c++) {
                if (c + 1 < 12 && hasWall(model, r, c, EAST) != hasWall(model, r, c + 1, WEST)) consistent = false;
                if (r + 1 < 12 && hasWall(model, r, c, SOUTH) != hasWall(model, r + 1, c, NORTH)) consistent = false;
            }
        }
        check("内部墙已双向归并（渲染与求解一致）", consistent);

        List<?> solved = (List<?>) solverC.getMethod("solve").invoke(newSolver(model));
        check("示例盘面 BFS 可解（" + solved.size() + " 步）", !solved.isEmpty());

        Object sampleSolver = newSolver(model);
        List<?> paths = (List<?>) solverC.getMethod("findAllPaths", int.class).invoke(sampleSolver, 2000);
        check("示例盘面可枚举路径（" + paths.size() + " 条）", !paths.isEmpty());

        // ---------- 2. 往返：示例盘面存盘再载入，逐格一致 ----------
        File roundTrip = new File(root, "tmp/import_roundtrip.maze");
        ioC.getMethod("saveBoard", modelC, File.class).invoke(null, model, roundTrip);
        Object again = ioC.getMethod("loadBoard", File.class).invoke(null, roundTrip);
        boolean same = true;
        for (int r = 0; r < 12; r++) {
            for (int c = 0; c < 12; c++) {
                if (wallMask(model, r, c) != wallMask(again, r, c)) same = false;
                if (hasScore(model, r, c) != hasScore(again, r, c)) same = false;
            }
        }
        check("示例盘面往返保存/载入逐格一致", same);

        // ---------- 3. 注释与数据行的判定规则 ----------
        // 起终点放在 (0,1) 与 (2,1)，避开要检查的 (0,0) 与 (2,2)（起终点格会被清掉得分点）
        File withComment = new File(root, "tmp/import_comment.maze");
        StringBuilder sb = new StringBuilder();
        sb.append("# MazeSolver Board v1\n");
        sb.append("# 这一行是注释（# 后有空白）\n");
        sb.append("size 3 3\nstart 0 1\nend 2 1\nwalls\n");
        sb.append("# 段中间也允许出现注释\n");
        sb.append("5f5\n5f5\n5f5\n");
        sb.append("scores\n");
        sb.append(row('#', '.', '.'));   // 整行以 '#' 开头，但它是数据行
        sb.append(row('.', '.', '.'));
        sb.append(row('.', '.', '#'));
        Files.write(withComment.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        Object withCommentModel = ioC.getMethod("loadBoard", File.class).invoke(null, withComment);
        check("'# 注释' 行被跳过，'#..' 数据行被保留",
                hasScore(withCommentModel, 0, 0) && !hasScore(withCommentModel, 0, 1)
                        && hasScore(withCommentModel, 2, 2));

        // ---------- 4. 非法文件仍然被拒 ----------
        File bad = new File(root, "tmp/import_bad.maze");
        Files.write(bad.toPath(), "size 4 4\nwalls\nfff\n".getBytes(StandardCharsets.UTF_8));
        boolean rejected = false;
        try {
            ioC.getMethod("loadBoard", File.class).invoke(null, bad);
        } catch (InvocationTargetException e) {
            rejected = e.getCause() instanceof java.io.IOException;
        }
        check("行数不符的盘面仍被拒绝", rejected);

        System.out.println(fails == 0 ? "SMOKE OK" : ("SMOKE FAILED: " + fails));
    }
}
