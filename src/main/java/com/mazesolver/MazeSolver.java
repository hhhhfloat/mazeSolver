// @anchor: solver_intro
// 迷宫求解器：BFS 求最短路，按上限 DFS 回溯枚举全部简单路径，并提供抽样取条、得分统计、标记数字串与路径文件输出。
package com.mazesolver;

import java.util.*;

/**
 * 迷宫求解器：使用 BFS 广度优先搜索找到最短路径，
 * 同时支持 DFS 回溯列举所有路径（可设条数上限，避免搜索耗时失控），
 * 并对枚举结果做确定性抽样（供前端叠画，条数有硬上限）。
 * 每条路径还会算出"经过得分点标记数字按顺序拼成的串"，供界面按数字串条件筛选。
 * 起点与终点一律取自 MazeModel（默认左上角/右下角，可由用户改到范围内任意格）。
 */
public class MazeSolver {


    // @anchor: solver_fields
    // 两套等价方向表：DIRECTIONS 用于墙壁位掩码判定，DIRS_ENCODED 附带对外方向编码 0~3
    private static final int[][] DIRECTIONS = {
        {-1,  0, MazeModel.NORTH},
        { 1,  0, MazeModel.SOUTH},
        { 0, -1, MazeModel.WEST },
        { 0,  1, MazeModel.EAST }
    };

    /** 四方向定义（用于 findAllPaths 方向编码）：
     *  0=NORTH(上), 1=SOUTH(下), 2=WEST(左), 3=EAST(右) */
    private static final int[][] DIRS_ENCODED = {
        {-1,  0, 0},  // NORTH
        { 1,  0, 1},  // SOUTH
        { 0, -1, 2},  // WEST
        { 0,  1, 3}   // EAST
    };

    private final MazeModel model;
    private final int rows;
    private final int cols;
    // @anchor: solver_fields_end

    // @anchor: solver_enum_limit
    // 枚举上限：maxPaths 是唯一闸门；默认值与合法区间在此定义，"是否被截断"供界面提示
    /** 未指定上限时的默认枚举条数（避免全路径搜索把界面卡住） */
    public static final int DEFAULT_MAX_PATHS = 2000;
    /** 上限合法区间：界面校验直接引用，不在别处写字面量 */
    public static final int MIN_ENUM_LIMIT = 1;
    public static final int MAX_ENUM_LIMIT = 1_000_000;

    /** 最近一次枚举实际使用的上限 */
    private int lastEnumerationLimit = DEFAULT_MAX_PATHS;
    /** 最近一次枚举是否因达到上限而提前结束（true 表示结果可能不完整） */
    private boolean lastEnumerationTruncated = false;

    public int getLastEnumerationLimit() { return lastEnumerationLimit; }

    public boolean isLastEnumerationTruncated() { return lastEnumerationTruncated; }
    // @anchor: solver_enum_limit_end


    public MazeSolver(MazeModel model) {
        this.model = model;
        this.rows = model.getRows();
        this.cols = model.getCols();
    }

    // @anchor: solver_bfs
    // BFS 最短路：从模型起点逐层扩展，命中模型终点即用 prev 前驱表回溯重建坐标序列
    /**
     * BFS 求解迷宫（起点/终点取自模型）。
     * @return 从起点到终点的路径坐标列表，若无路径则返回空列表。
     */
    public List<int[]> solve() {
        int startR = model.getStartR(), startC = model.getStartC();
        int endR = model.getEndR(), endC = model.getEndC();
        if (startR == endR && startC == endC) {
            List<int[]> single = new ArrayList<>();
            single.add(new int[]{startR, startC});
            return single;
        }

        Queue<int[]> queue = new LinkedList<>();
        boolean[][] visited = new boolean[rows][cols];
        // prev[r][c] = {前驱行, 前驱列}
        int[][][] prev = new int[rows][cols][2];

        queue.add(new int[]{startR, startC});
        visited[startR][startC] = true;
        prev[startR][startC][0] = -1;
        prev[startR][startC][1] = -1;

        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            int r = cur[0], c = cur[1];

            // 到达终点
            if (r == endR && c == endC) {
                return reconstructPath(prev, endR, endC);
            }

            for (int[] dir : DIRECTIONS) {
                int nr = r + dir[0];
                int nc = c + dir[1];
                if (nr >= 0 && nr < rows && nc >= 0 && nc < cols
                        && !visited[nr][nc]
                        && !model.hasWall(r, c, dir[2])) {
                    visited[nr][nc] = true;
                    prev[nr][nc][0] = r;
                    prev[nr][nc][1] = c;
                    queue.add(new int[]{nr, nc});
                }
            }
        }

        return Collections.emptyList();
    }

    /** 从 prev 数组由终点反向回溯到起点，再翻转成正序 */
    private List<int[]> reconstructPath(int[][][] prev, int endR, int endC) {
        List<int[]> path = new ArrayList<>();
        int r = endR, c = endC;
        while (r != -1 && c != -1) {
            path.add(new int[]{r, c});
            int pr = prev[r][c][0];
            int pc = prev[r][c][1];
            r = pr;
            c = pc;
        }
        Collections.reverse(path);
        return path;
    }

    // @anchor: solver_bfs_end

    // @anchor: solver_all_paths
    // DFS 回溯枚举所有不重复经过同一格的路径（显式栈，深度不受调用栈限制），对外用 0~3 方向编码表达；起终点取自模型
    /**
     * DFS 回溯找出所有从起点到终点的路径（不经过重复位置），使用默认上限。
     * @return 所有路径的列表，每条路径是方向编码 (0/1/2/3) 的列表
     */
    public List<List<Integer>> findAllPaths() {
        return findAllPaths(DEFAULT_MAX_PATHS);
    }

    /**
     * DFS 回溯枚举路径：收录到 maxPaths 条即停止，避免全路径搜索耗时失控。
     * 是否因达上限提前结束记录在 isLastEnumerationTruncated()（达上限时结果可能不完整）。
     * @param maxPaths 本次枚举的条数上限，小于 MIN_ENUM_LIMIT 时按 MIN_ENUM_LIMIT 处理
     * @return 最多 maxPaths 条路径，每条是方向编码 (0/1/2/3) 的列表
     */
    public List<List<Integer>> findAllPaths(int maxPaths) {
        int limit = Math.max(MIN_ENUM_LIMIT, maxPaths);
        lastEnumerationLimit = limit;
        lastEnumerationTruncated = false;

        int startR = model.getStartR(), startC = model.getStartC();
        int endR = model.getEndR(), endC = model.getEndC();

        List<List<Integer>> allPaths = new ArrayList<>();
        boolean[][] visited = new boolean[rows][cols];
        List<Integer> currentPath = new ArrayList<>();
        Deque<int[]> stack = new ArrayDeque<>(); // 帧：{行, 列, 下一个待试方向下标}

        visited[startR][startC] = true;
        stack.push(new int[]{startR, startC, 0});

        while (!stack.isEmpty()) {
            int[] frame = stack.peek();
            int r = frame[0], c = frame[1];

            // 到达终点：收录这条完整路径；达上限则直接结束搜索，否则回溯继续
            if (r == endR && c == endC) {
                allPaths.add(new ArrayList<>(currentPath));
                if (allPaths.size() >= limit) {
                    lastEnumerationTruncated = true;
                    break;
                }
                backtrack(stack, visited, currentPath);
                continue;
            }
            // 四个方向都已试完：回溯
            if (frame[2] >= DIRS_ENCODED.length) {
                backtrack(stack, visited, currentPath);
                continue;
            }

            int[] dir = DIRS_ENCODED[frame[2]++];
            int nr = r + dir[0];
            int nc = c + dir[1];
            int dirCode = dir[2];
            if (nr >= 0 && nr < rows && nc >= 0 && nc < cols
                    && !visited[nr][nc]
                    && !model.hasWall(r, c, dirCodeToWall(dirCode))) {
                visited[nr][nc] = true;
                currentPath.add(dirCode);
                stack.push(new int[]{nr, nc, 0});
            }
        }
        return allPaths;
    }

    /** 出栈回溯一步：撤销该格的访问标记，并弹出进入该格的方向编码 */
    private void backtrack(Deque<int[]> stack, boolean[][] visited, List<Integer> currentPath) {
        int[] frame = stack.pop();
        visited[frame[0]][frame[1]] = false;
        if (!currentPath.isEmpty()) currentPath.remove(currentPath.size() - 1);
    }

    /** 将方向编码 (0/1/2/3) 转换为 MazeModel 墙壁常量 */
    private int dirCodeToWall(int dirCode) {
        switch (dirCode) {
            case 0: return MazeModel.NORTH;
            case 1: return MazeModel.SOUTH;
            case 2: return MazeModel.WEST;
            case 3: return MazeModel.EAST;
            default: return MazeModel.NORTH;
        }
    }

    // @anchor: solver_all_paths_end

    // @anchor: solver_sample
    // 抽样取条：枚举结果可能成千上万条，前端只叠画其中若干条；等间隔确定性取点（不用随机），重绘时不会跳动
    /** 默认叠画条数：求解后前端默认叠画这么多条路径 */
    public static final int DEFAULT_SAMPLE_COUNT = 30;
    /** 叠画条数下限 */
    public static final int MIN_SAMPLE_COUNT = 1;
    /** 叠画条数上限：超过此数值前端描线会明显拖慢重绘，故硬性封顶（请求再大也只抽这么多） */
    public static final int MAX_SAMPLE_COUNT = 200;

    /**
     * 从 [0, total) 中等间隔抽取至多 count 个下标：首尾两端必取、无重复、升序。
     * 期望条数先收敛到 [MIN_SAMPLE_COUNT, MAX_SAMPLE_COUNT]，因此请求再多也不会超过上限；
     * 收敛后仍不少于 total 时返回全部下标；total 或 count 非正时返回空列表。
     * 抽样是为了"看得见分布"，所以宁可牺牲随机性也要保证每次重绘结果一致。
     * @param total 候选总数（本次枚举到的路径条数）
     * @param count 期望抽样条数
     * @return 抽样得到的下标列表
     */
    public static List<Integer> sampleIndices(int total, int count) {
        List<Integer> picked = new ArrayList<>();
        if (total <= 0 || count <= 0) return picked;

        int want = Math.max(MIN_SAMPLE_COUNT, Math.min(MAX_SAMPLE_COUNT, count));
        if (want >= total) {
            for (int i = 0; i < total; i++) picked.add(i);
            return picked;
        }
        if (want <= 1) {
            picked.add(0); // 只要一条就取枚举出的第一条
            return picked;
        }
        int last = -1;
        for (int i = 0; i < want; i++) {
            int idx = (int) Math.round((double) i * (total - 1) / (want - 1));
            if (idx <= last) continue; // 理论上不会发生，兜底防止重复下标
            picked.add(idx);
            last = idx;
        }
        return picked;
    }

    // @anchor: solver_sample_end


    // @anchor: solver_dir_to_coords
    // 方向编码 → 坐标序列（从模型起点起算），供视图绘制与列表选中时的路径切换
    /**
     * 将方向编码路径转换为坐标列表，便于视图渲染。
     * @param dirCodes 方向编码列表 (0=NORTH, 1=SOUTH, 2=WEST, 3=EAST)
     * @return 从模型起点到模型终点的坐标列表
     */
    public List<int[]> dirCodesToCoordinates(List<Integer> dirCodes) {
        List<int[]> coords = new ArrayList<>();
        int r = model.getStartR(), c = model.getStartC();
        coords.add(new int[]{r, c});
        for (int dirCode : dirCodes) {
            switch (dirCode) {
                case 0: r--; break; // NORTH
                case 1: r++; break; // SOUTH
                case 2: c--; break; // WEST
                case 3: c++; break; // EAST
            }
            coords.add(new int[]{r, c});
        }
        return coords;
    }

    // @anchor: solver_dir_to_coords_end

    // @anchor: solver_path_stats
    // 一次遍历同时得出分数与数字串：列表、结果文件、筛选共用同一份结果，避免同一条路径被走多遍
    /** 一条路径的统计结果：经过的得分点个数 + 按经过顺序拼成的标记数字串 */
    public static final class PathStats {
        private final int score;
        private final String digits;

        PathStats(int score, String digits) {
            this.score = score;
            this.digits = digits;
        }

        /** 经过的得分点个数（同一格只计一次） */
        public int getScore() { return score; }

        /** 标记数字串：按经过顺序拼接，同一格只记一次；没有经过得分点则为空串 */
        public String getDigits() { return digits; }
    }

    /**
     * 分析一条路径（方向编码）：从模型起点走一遍，统计得分点个数并拼接标记数字串。
     * 起点所在格的标记同样计入；越界即停止（正常路径不会越界）。
     */
    public PathStats analyzePath(List<Integer> dirCodes) {
        boolean[][] visited = new boolean[rows][cols];
        StringBuilder digits = new StringBuilder();
        int r = model.getStartR(), c = model.getStartC();
        int score = 0;

        int startDigit = model.getScoreDigit(r, c);
        if (startDigit != MazeModel.SCORE_NONE) {
            visited[r][c] = true;
            score++;
            digits.append((char) ('0' + startDigit));
        }

        if (dirCodes != null) {
            for (int dirCode : dirCodes) {
                switch (dirCode) {
                    case 0: r--; break; // NORTH
                    case 1: r++; break; // SOUTH
                    case 2: c--; break; // WEST
                    case 3: c++; break; // EAST
                    default: break;
                }
                if (r < 0 || r >= rows || c < 0 || c >= cols) break;
                int digit = model.getScoreDigit(r, c);
                if (digit != MazeModel.SCORE_NONE && !visited[r][c]) {
                    visited[r][c] = true;
                    score++;
                    digits.append((char) ('0' + digit));
                }
            }
        }
        return new PathStats(score, digits.toString());
    }

    /** 标记数字串（按经过顺序）：供列表展示与数字串筛选 */
    public String computePathDigits(List<Integer> dirCodes) {
        return analyzePath(dirCodes).getDigits();
    }

    // @anchor: solver_path_stats_end


    // @anchor: solver_path_score
    // 得分统计：复用 analyzePath 的单次遍历结果，保证"分数"与"数字串"永远出自同一口径
    /** 计算一条路径（方向编码）经过的得分点总数，同一格只计一次 */
    public int computePathScore(List<Integer> dirCodes) {
        return analyzePath(dirCodes).getScore();
    }

    // @anchor: solver_path_score_end

    // @anchor: solver_save_paths
    // 文件输出：头部记录本次枚举上下文（尺寸/起终点/上限/是否达上限/条数），其后每行一条路径含分数与数字串
    /** 将枚举结果写入文件（字符串路径版本，供自动输出到 tmp/ 使用） */
    public void savePathsToFile(List<List<Integer>> allPaths, String filepath) {
        savePathsToFile(allPaths, new java.io.File(filepath));
    }

    /**
     * 将所有路径保存到文件。每行格式："Path N (S步, P分, 序列 D): 方向编码"，
     * 其中 D 是这条路径按经过顺序得到的标记数字串（无得分点时为 "-"）。
     * @param allPaths 所有路径列表
     * @param file 目标文件（父目录需已存在）
     * @return 写入成功返回 true
     */
    public boolean savePathsToFile(List<List<Integer>> allPaths, java.io.File file) {
        try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.OutputStreamWriter(
                new java.io.FileOutputStream(file), java.nio.charset.StandardCharsets.UTF_8))) {
            pw.println("# MazeSolver Paths v1");
            pw.println("# 方向编码：0=NORTH 1=SOUTH 2=WEST 3=EAST");
            pw.println("# 每行：Path N (S步, P分, 序列 D): 方向编码；D 为经过得分点按顺序得到的标记数字串");
            pw.println("# size " + rows + " " + cols);
            pw.println("# start " + model.getStartR() + " " + model.getStartC());
            pw.println("# end " + model.getEndR() + " " + model.getEndC());
            pw.println("# limit " + lastEnumerationLimit);
            pw.println("# truncated " + lastEnumerationTruncated);
            pw.println("# count " + allPaths.size());
            pw.println();
            for (int i = 0; i < allPaths.size(); i++) {
                List<Integer> path = allPaths.get(i);
                PathStats stats = analyzePath(path);
                String digits = stats.getDigits().isEmpty() ? "-" : stats.getDigits();
                pw.print("Path " + (i + 1) + " (" + path.size() + "步, "
                        + stats.getScore() + "分, 序列 " + digits + "): ");
                for (int j = 0; j < path.size(); j++) {
                    pw.print(path.get(j));
                    if (j < path.size() - 1) pw.print(" ");
                }
                pw.println();
            }
            return true;
        } catch (java.io.IOException e) {
            System.err.println("保存路径文件失败: " + e.getMessage());
            return false;
        }
    }

    // @anchor: solver_save_paths_end
}
