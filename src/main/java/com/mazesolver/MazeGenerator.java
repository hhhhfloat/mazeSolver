// @anchor: generator_intro
// 迷宫生成器：DFS 递归回溯生成完美迷宫（任意两点恰有一条通路），并按密度撒布得分点。
package com.mazesolver;

import java.util.*;

/**
 * 迷宫生成器：使用 DFS 递归回溯算法生成完美迷宫。
 * 完美迷宫保证任意两点之间恰好有一条路径。
 */
public class MazeGenerator {

    // @anchor: generator_fields
    // 方向表带墙壁位掩码便于直接拆墙；得分点密度按总格数比例取 7%
    private static final int[][] DIRECTIONS = {
        {-1,  0, MazeModel.NORTH},
        { 1,  0, MazeModel.SOUTH},
        { 0, -1, MazeModel.WEST },
        { 0,  1, MazeModel.EAST }
    };

    /** 得分点密度：约占总单元格数的 7%（不含起点终点） */
    private static final double SCORE_DENSITY = 0.07;

    private final MazeModel model;
    private final int rows;
    private final int cols;
    private final Random random;
    // @anchor: generator_fields_end

    public MazeGenerator(MazeModel model) {
        this.model = model;
        this.rows = model.getRows();
        this.cols = model.getCols();
        this.random = new Random();
    }

    // @anchor: generator_generate
    // 生成入口：重置为全墙 → 从 (0,0) 起 DFS 打通 → 清空并按密度重新撒布得分点（避开模型起终点，带尝试次数上限）
    /** 生成新的迷宫（打洞起点与用户设定的起终点无关） */
    public void generate() {
        model.resetWalls();
        boolean[][] visited = new boolean[rows][cols];
        dfs(0, 0, visited);

        // 随机放置得分点（避开起点和终点），按迷宫大小比例计算数量
        model.clearScorePoints();
        int totalCells = rows * cols;
        int targetCount = Math.max(1, (int)(totalCells * SCORE_DENSITY));
        // 确保不超过可放置单元格数（减去起点和终点）
        int maxPlaceable = totalCells - 2;
        if (targetCount > maxPlaceable) {
            targetCount = maxPlaceable;
        }
        int placed = 0;
        int maxAttempts = targetCount * 10;
        int attempts = 0;
        while (placed < targetCount && attempts < maxAttempts) {
            int r = random.nextInt(rows);
            int c = random.nextInt(cols);
            // 避开当前起点与终点（位置可由用户自定义）
            if (model.isStart(r, c) || model.isEnd(r, c)) {
                attempts++;
                continue;
            }
            if (!model.hasScorePoint(r, c)) {
                model.addScorePoint(r, c);
                placed++;
            }
            attempts++;
        }
    }

    // @anchor: generator_generate_end

    // @anchor: generator_dfs
    // 随机回溯（显式栈）：帧内保存"当前格 + 已打乱方向表 + 下一待试下标"，深度不受调用栈限制
    /** 随机 DFS 打通所有单元格（迭代实现，大尺寸迷宫也不会栈溢出） */
    private void dfs(int startR, int startC, boolean[][] visited) {
        Deque<Frame> stack = new ArrayDeque<>();
        visited[startR][startC] = true;
        stack.push(new Frame(startR, startC, shuffledDirections()));

        while (!stack.isEmpty()) {
            Frame cur = stack.peek();

            // 在帧内继续尝试尚未试过的方向
            int nextR = -1, nextC = -1;
            while (cur.next < cur.dirs.size()) {
                int[] dir = cur.dirs.get(cur.next++);
                int nr = cur.r + dir[0];
                int nc = cur.c + dir[1];
                if (nr >= 0 && nr < rows && nc >= 0 && nc < cols && !visited[nr][nc]) {
                    nextR = nr;
                    nextC = nc;
                    break;
                }
            }

            if (nextR < 0) {
                stack.pop(); // 四周已无未访问邻居 → 回溯
                continue;
            }
            model.removeWallBetween(cur.r, cur.c, nextR, nextC);
            visited[nextR][nextC] = true;
            stack.push(new Frame(nextR, nextC, shuffledDirections()));
        }
    }

    /** 每格独立打乱的方向表副本，保证生成结果随机 */
    private List<int[]> shuffledDirections() {
        List<int[]> shuffled = new ArrayList<>(Arrays.asList(DIRECTIONS));
        Collections.shuffle(shuffled, random);
        return shuffled;
    }

    /** 显式栈帧：单元格坐标 + 该格的方向尝试顺序 + 下一待试下标 */
    private static final class Frame {
        final int r, c;
        final List<int[]> dirs;
        int next;

        Frame(int r, int c, List<int[]> dirs) {
            this.r = r;
            this.c = c;
            this.dirs = dirs;
        }
    }

    // @anchor: generator_dfs_end
}
