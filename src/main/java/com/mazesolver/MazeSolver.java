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

    // @anchor: solver_incremental_state
    // 增量枚举现场：搜索栈 / 访问标记 / 当前路径从局部变量提升为字段，随时可挂起与续跑；结果集合对外返回同一实例
    /** 累计枚举结果：对外返回同一实例（面板只持引用），作废由 resetEnumeration 清空 */
    private final List<List<Integer>> collectedPaths = new ArrayList<>();
    /** 搜索栈：帧 = {行, 列, 下一个待试方向下标}；null 表示尚未开始 */
    private Deque<int[]> searchStack;
    private boolean[][] searchVisited;
    private List<Integer> searchCurrent;
    /** 是否已初始化现场（首次续跑才需要从起点压栈） */
    private boolean searchInitialized = false;
    /** 搜索空间是否已穷尽（栈空）；穷尽后再调不会新增任何路径 */
    private boolean searchExhausted = false;
    /** 上次停在"刚收录终点、尚未回溯"处 → 续跑第一步必须先补一次回溯，否则会重复收录同一条路径 */
    private boolean resumeNeedsBacktrack = false;
    /** 已收录路径的步数总和：用于按实测平均步长估算内存 */
    private long collectedSteps = 0;
    /** 现场所属盘面的指纹（尺寸 / 起终点 / 逐格墙壁 / 逐格标记），不一致即自动作废从头开始 */
    private long boardFingerprint = 0L;
    /** 最近一次续跑是否被内存预算拦下 */
    private boolean lastEnumerationMemoryCapped = false;

    /** 每条路径的固定开销（ArrayList 头 + 元素数组头 + 外层引用，按压缩指针估算） */
    private static final int PATH_OVERHEAD_BYTES = 64;
    /** 每个方向编码占用的字节（压缩指针下的一个引用槽） */
    private static final int DIR_BYTES = 4;
    /** 枚举结果的堆预算：超过即不再加深，避免把界面拖进 OOM（真正的天花板是内存，不是搜索速度） */
    public static final long ENUM_MEMORY_BUDGET_BYTES = 256L * 1024 * 1024;
    // @anchor: solver_incremental_state_end

    // @anchor: solver_enum_limit
    // 枚举上限：输入框表达"目标总条数"（增量语义）；默认值与合法区间在此定义，"是否还有更多""是否被内存预算拦下"供界面提示
    /** 未指定上限时的默认枚举条数（避免全路径搜索把界面卡住） */
    public static final int DEFAULT_MAX_PATHS = 2000;
    /** 上限合法区间：界面校验直接引用，不在别处写字面量 */
    public static final int MIN_ENUM_LIMIT = 1;
    /** 硬天花板：结果常驻堆内存，几十万条已远超常规使用，再大就不是搜索慢而是内存不够 */
    public static final int MAX_ENUM_LIMIT = 200_000;
    /** 「+」按钮一次追加的目标条数：增量粒度，决定"每点一下多算多少" */
    public static final int ENUM_INCREMENT = 500;

    /** 最近一次枚举实际使用的目标总条数 */
    private int lastEnumerationLimit = DEFAULT_MAX_PATHS;
    /** 最近一次枚举后是否还有没枚举完的路径（true 表示结果可能不完整） */
    private boolean lastEnumerationTruncated = false;

    public int getLastEnumerationLimit() { return lastEnumerationLimit; }

    public boolean isLastEnumerationTruncated() { return lastEnumerationTruncated; }

    /** 最近一次枚举是否因内存预算而没能达到请求的目标条数 */
    public boolean isLastEnumerationMemoryCapped() { return lastEnumerationMemoryCapped; }
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
    // 枚举入口：语义统一为"续跑到目标总条数"——已有结果不丢弃、不从零重跑，同一盘面下第 k 条永远是同一条
    /**
     * 枚举到默认上限（增量语义：已枚举过就在已有结果基础上继续）。
     * @return 累计路径列表（与求解器内部同一实例）
     */
    public List<List<Integer>> findAllPaths() {
        return continueFindAllPaths(DEFAULT_MAX_PATHS);
    }

    /**
     * 枚举到目标总条数（增量语义）。目标小于已有条数时不缩水、不重跑，直接返回已有结果。
     * @param maxPaths 目标总条数，区间外按 MIN_ENUM_LIMIT / MAX_ENUM_LIMIT 收敛
     * @return 累计路径列表（与求解器内部同一实例）
     */
    public List<List<Integer>> findAllPaths(int maxPaths) {
        return continueFindAllPaths(maxPaths);
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

    // @anchor: solver_continue
    // 增量枚举核心：盘面指纹校验 → 按需初始化现场 / 补正回溯 → 显式栈 DFS 续跑，收录到目标总数或空间穷尽即停
    /**
     * 在已枚举结果的基础上继续枚举，直到累计达到 targetTotal 条或搜索空间穷尽。
     * 目标小于已有条数时不缩水、不重跑，直接返回已有结果；
     * 盘面指纹变化（改墙 / 改起终点 / 改标记）会自动作废旧现场并从起点重新枚举，
     * 绝不会把旧盘面的结果接到新盘面后面。
     * @param targetTotal 目标累计条数
     * @return 累计路径列表（与求解器内部同一实例）
     */
    public List<List<Integer>> continueFindAllPaths(int targetTotal) {
        int limit = Math.min(MAX_ENUM_LIMIT, Math.max(MIN_ENUM_LIMIT, targetTotal));
        ensureSearchSession();
        lastEnumerationLimit = limit;
        lastEnumerationTruncated = false;
        lastEnumerationMemoryCapped = false;

        // 已穷尽：再调也不会新增，直接返回累计结果
        if (searchExhausted) return collectedPaths;
        // 目标调小不缩水：已有条数够了就直取，绝不丢数据也不重跑
        if (collectedPaths.size() >= limit) {
            lastEnumerationTruncated = true;
            return collectedPaths;
        }

        // 内存才是真正的天花板：按实测平均步长把目标收敛到预算内
        int budgetTarget = memoryLimitedTarget(limit, collectedSteps, collectedPaths.size());
        if (budgetTarget < limit) {
            lastEnumerationMemoryCapped = true;
            limit = budgetTarget;
        }
        if (collectedPaths.size() >= limit) {
            lastEnumerationTruncated = true;
            return collectedPaths;
        }

        // 上轮停在"刚收录终点、尚未回溯"处：先补一次回溯，否则同一条路径会被再收一次
        if (resumeNeedsBacktrack) {
            backtrack();
            resumeNeedsBacktrack = false;
        }

        int endR = model.getEndR(), endC = model.getEndC();
        while (!searchStack.isEmpty()) {
            int[] frame = searchStack.peek();
            int r = frame[0], c = frame[1];

            // 到达终点：收录这条完整路径；达目标总数则挂起（保留栈顶，下次先回溯），否则回溯继续
            if (r == endR && c == endC) {
                collectedPaths.add(new ArrayList<>(searchCurrent));
                collectedSteps += searchCurrent.size();
                resumeNeedsBacktrack = true;
                if (collectedPaths.size() >= limit) break;
                backtrack();
                resumeNeedsBacktrack = false;
                continue;
            }
            // 四个方向都已试完：回溯
            if (frame[2] >= DIRS_ENCODED.length) {
                backtrack();
                continue;
            }

            int[] dir = DIRS_ENCODED[frame[2]++];
            int nr = r + dir[0];
            int nc = c + dir[1];
            int dirCode = dir[2];
            if (nr >= 0 && nr < rows && nc >= 0 && nc < cols
                    && !searchVisited[nr][nc]
                    && !model.hasWall(r, c, dirCodeToWall(dirCode))) {
                searchVisited[nr][nc] = true;
                searchCurrent.add(dirCode);
                searchStack.push(new int[]{nr, nc, 0});
            }
        }
        // 栈空即搜索空间穷尽；否则说明是"达目标总数挂起"，下次可继续
        if (searchStack.isEmpty()) {
            searchExhausted = true;
            resumeNeedsBacktrack = false;
        }
        lastEnumerationTruncated = !searchExhausted;
        return collectedPaths;
    }

    /** 现场初始化：指纹不一致时作废旧现场，从起点压入唯一栈帧 */
    private void ensureSearchSession() {
        long fingerprint = computeBoardFingerprint();
        if (searchInitialized && fingerprint == boardFingerprint) return;
        resetEnumeration();
        boardFingerprint = fingerprint;
        int startR = model.getStartR(), startC = model.getStartC();
        searchVisited = new boolean[rows][cols];
        searchCurrent = new ArrayList<>();
        searchStack = new ArrayDeque<>();
        searchVisited[startR][startC] = true;
        searchStack.push(new int[]{startR, startC, 0});
        searchInitialized = true;
    }

    /** 出栈回溯一步：撤销该格的访问标记，并弹出进入该格的方向编码 */
    private void backtrack() {
        int[] frame = searchStack.pop();
        searchVisited[frame[0]][frame[1]] = false;
        if (!searchCurrent.isEmpty()) searchCurrent.remove(searchCurrent.size() - 1);
    }

    /** 盘面指纹：尺寸 / 起终点 / 逐格墙壁掩码 / 逐格标记数字，任一处变化即视为"换了盘面" */
    private long computeBoardFingerprint() {
        long h = 1469598103934665603L; // FNV-1a 64 位偏移基数
        h = mixFingerprint(h, rows);
        h = mixFingerprint(h, cols);
        h = mixFingerprint(h, model.getStartR());
        h = mixFingerprint(h, model.getStartC());
        h = mixFingerprint(h, model.getEndR());
        h = mixFingerprint(h, model.getEndC());
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                h = mixFingerprint(h, model.getWallMask(r, c));
            }
        }
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                h = mixFingerprint(h, model.getScoreDigit(r, c));
            }
        }
        return h;
    }

    /** FNV-1a 混合一步：同输入必得同指纹，用于"现场是否还对应当前盘面" */
    private static long mixFingerprint(long h, long value) {
        return (h ^ value) * 1099511628211L;
    }

    /** 按实测平均步长把目标条数收敛到默认堆预算内 */
    static int memoryLimitedTarget(int requested, long totalSteps, int paths) {
        return memoryLimitedTarget(requested, totalSteps, paths, ENUM_MEMORY_BUDGET_BYTES);
    }

    /**
     * 按实测平均步长把目标条数收敛到给定预算内（条数 × 单条估算字节 ≤ 预算）。
     * @param requested 请求的目标总条数
     * @param totalSteps 已收录路径的步数总和
     * @param paths 已收录条数
     * @param budgetBytes 堆预算
     * @return 预算允许的目标条数（不小于 0，不大于 requested）
     */
    static int memoryLimitedTarget(int requested, long totalSteps, int paths, long budgetBytes) {
        if (requested <= 0 || budgetBytes <= 0) return 0;
        int avgSteps = paths <= 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, totalSteps / paths);
        long perPath = PATH_OVERHEAD_BYTES + (long) DIR_BYTES * avgSteps;
        long allowed = budgetBytes / Math.max(1L, perPath);
        return (int) Math.min((long) requested, allowed);
    }
    // @anchor: solver_continue_end

    // @anchor: solver_reset
    // 作废入口：盘面任何改动后都必须调用，否则旧现场会把旧盘面的结果接到新盘面后面
    /** 清空现场与累计结果（盘面变化、界面清空 / 重新生成 / 载入时调用） */
    public void resetEnumeration() {
        collectedPaths.clear();
        searchStack = null;
        searchVisited = null;
        searchCurrent = null;
        searchInitialized = false;
        searchExhausted = false;
        resumeNeedsBacktrack = false;
        collectedSteps = 0;
        boardFingerprint = 0L;
        lastEnumerationLimit = DEFAULT_MAX_PATHS;
        lastEnumerationTruncated = false;
        lastEnumerationMemoryCapped = false;
    }

    /** 是否还有未枚举的路径（已穷尽返回 false，「+」按钮据此提示"已穷尽"） */
    public boolean hasMorePaths() { return !searchExhausted; }

    /** 当前累计枚举结果（与 continueFindAllPaths 返回的是同一实例） */
    public List<List<Integer>> getCollectedPaths() { return collectedPaths; }

    /** 已收录路径占用的堆内存估算，供界面在继续加深前给出提示 */
    public long getEstimatedMemoryBytes() {
        int avgSteps = collectedPaths.isEmpty()
                ? 0 : (int) (collectedSteps / collectedPaths.size());
        return collectedPaths.size() * (PATH_OVERHEAD_BYTES + (long) DIR_BYTES * avgSteps);
    }
    // @anchor: solver_reset_end


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
