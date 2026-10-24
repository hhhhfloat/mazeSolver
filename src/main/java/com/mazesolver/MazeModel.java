// @anchor: model_intro
// 迷宫数据模型：4 位掩码存墙壁 + 整数矩阵存得分点标记数字（0=无，1~9=标记），纯数据类，不含算法与 UI。
package com.mazesolver;

/**
 * 迷宫数据模型：使用位掩码存储每个单元格的四向墙壁。
 * 墙壁方向：NORTH(1) EAST(2) SOUTH(4) WEST(8)
 * 得分点用一位数字标记（可重复），路径经过时按顺序拼成数字串供筛选。
 */
public class MazeModel {

    // @anchor: model_wall_const
    // 四向墙壁位掩码（全墙壁 = 15）、迷宫边长合法区间与墙壁矩阵
    public static final int NORTH = 1;
    public static final int EAST  = 2;
    public static final int SOUTH = 4;
    public static final int WEST  = 8;

    /** 迷宫边长合法区间：UI 校验、视图分档都以这里为唯一来源，避免各处硬编码 */
    public static final int MIN_DIM = 3;
    public static final int MAX_DIM = 200;

    private final int rows;
    private final int cols;
    private int[][] walls;

    // @anchor: model_wall_const_end

    // @anchor: model_constructor
    // 行列在构造期固定；构造完成即处于"全墙壁 + 得分点就绪 + 起终点在左上/右下角"的初始态
    public MazeModel(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
        this.walls = new int[rows][cols];
        resetWalls();
        initScorePoints();
        resetEndpoints();
    }

    // @anchor: model_constructor_end

    // @anchor: model_reset_walls
    // 重置为全墙壁（未打通）状态
    /** 重置所有墙壁（全部存在） */
    public void resetWalls() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                walls[r][c] = NORTH | EAST | SOUTH | WEST;
            }
        }
    }
    // @anchor: model_reset_walls_end

    public int getRows() { return rows; }
    public int getCols() { return cols; }

    // @anchor: model_wall_query
    // 单格单方向的墙壁查询与增删；越界视为有墙，调用方无需额外判空
    /** 检查指定方向是否有墙壁 */
    public boolean hasWall(int r, int c, int direction) {
        if (r < 0 || r >= rows || c < 0 || c >= cols) return true;
        return (walls[r][c] & direction) != 0;
    }

    /** 移除指定方向的一面墙壁 */
    public void removeWall(int r, int c, int direction) {
        walls[r][c] &= ~direction;
    }

    /** 添加指定方向的一面墙壁 */
    public void addWall(int r, int c, int direction) {
        walls[r][c] |= direction;
    }

    /** 读取某格的完整墙壁掩码（越界视为四面有墙） */
    public int getWallMask(int r, int c) {
        if (r < 0 || r >= rows || c < 0 || c >= cols) return NORTH | EAST | SOUTH | WEST;
        return walls[r][c];
    }

    /** 整体覆盖某格的墙壁掩码（盘面载入用，不做双向同步，一致性由整盘内容保证） */
    public void setWallMask(int r, int c, int mask) {
        if (r < 0 || r >= rows || c < 0 || c >= cols) return;
        walls[r][c] = mask & (NORTH | EAST | SOUTH | WEST);
    }

    // @anchor: model_wall_query_end

    // @anchor: model_clear_internal_walls
    // 只拆相邻格之间的墙壁，外边界保持封闭
    /** 清除所有内部墙壁（相邻单元格之间），保留外边界 */
    public void clearInternalWalls() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                // 移除与下方单元格之间的墙壁（SOUTH，非最后一行）
                if (r < rows - 1) {
                    removeWall(r, c, SOUTH);
                    removeWall(r + 1, c, NORTH);
                }
                // 移除与右侧单元格之间的墙壁（EAST，非最后一列）
                if (c < cols - 1) {
                    removeWall(r, c, EAST);
                    removeWall(r, c + 1, WEST);
                }
            }
        }
    }
    // @anchor: model_clear_internal_walls_end

    // @anchor: model_wall_between
    // 相邻两格之间的墙壁操作：打通 / 加墙 / 切换，一律双向同步；外边界不会被打开
    /** 打通两个相邻单元格之间的墙壁（双向） */
    public void removeWallBetween(int r1, int c1, int r2, int c2) {
        int dr = r2 - r1;
        int dc = c2 - c1;
        if (dr == -1) {
            removeWall(r1, c1, NORTH);
            removeWall(r2, c2, SOUTH);
        } else if (dr == 1) {
            removeWall(r1, c1, SOUTH);
            removeWall(r2, c2, NORTH);
        } else if (dc == -1) {
            removeWall(r1, c1, WEST);
            removeWall(r2, c2, EAST);
        } else if (dc == 1) {
            removeWall(r1, c1, EAST);
            removeWall(r2, c2, WEST);
        }
    }

    /** 在两个相邻单元格之间添加墙壁（双向） */
    public void addWallBetween(int r1, int c1, int r2, int c2) {
        int dr = r2 - r1;
        int dc = c2 - c1;
        if (dr == -1) {
            addWall(r1, c1, NORTH);
            addWall(r2, c2, SOUTH);
        } else if (dr == 1) {
            addWall(r1, c1, SOUTH);
            addWall(r2, c2, NORTH);
        } else if (dc == -1) {
            addWall(r1, c1, WEST);
            addWall(r2, c2, EAST);
        } else if (dc == 1) {
            addWall(r1, c1, EAST);
            addWall(r2, c2, WEST);
        }
    }

    /** 切换两个相邻单元格之间的墙壁状态（有则移除，无则添加）。
     *  仅处理内部墙壁，不处理外边界。 */
    public void toggleWallBetween(int r1, int c1, int r2, int c2) {
        int dr = r2 - r1;
        int dc = c2 - c1;
        int dir1, dir2;
        if (dr == -1) {
            dir1 = NORTH; dir2 = SOUTH;
        } else if (dr == 1) {
            dir1 = SOUTH; dir2 = NORTH;
        } else if (dc == -1) {
            dir1 = WEST;  dir2 = EAST;
        } else if (dc == 1) {
            dir1 = EAST;  dir2 = WEST;
        } else {
            return;
        }
        if (hasWall(r1, c1, dir1)) {
            removeWall(r1, c1, dir1);
            removeWall(r2, c2, dir2);
        } else {
            addWall(r1, c1, dir1);
            addWall(r2, c2, dir2);
        }
    }
    // @anchor: model_wall_between_end

    // @anchor: model_border_side
    // 单侧墙壁操作：内部侧双向同步（等价 addWallBetween），外边界侧没有相邻格可同步，只改本格一面
    /** 设置某一侧墙壁有无：内部侧双向同步，外边界侧仅改本格 */
    public void setWallSide(int r, int c, int dir, boolean present) {
        if (r < 0 || r >= rows || c < 0 || c >= cols) return;

        int nr = r, nc = c;
        if (dir == NORTH)      nr = r - 1;
        else if (dir == SOUTH) nr = r + 1;
        else if (dir == WEST)  nc = c - 1;
        else if (dir == EAST)  nc = c + 1;

        boolean hasNeighbor = nr >= 0 && nr < rows && nc >= 0 && nc < cols;
        if (!hasNeighbor) {
            // 外边界：只有本格这一侧，不存在配对格
            if (present) addWall(r, c, dir);
            else         removeWall(r, c, dir);
            return;
        }
        if (present) addWallBetween(r, c, nr, nc);
        else         removeWallBetween(r, c, nr, nc);
    }
    // @anchor: model_border_side_end


    // @anchor: model_endpoints
    // 起终点坐标：默认左上角与右下角，可改到范围内任意格；不允许重合，所在格不保留得分点
    private int startR, startC, endR, endC;

    /** 起点行 */
    public int getStartR() { return startR; }
    /** 起点列 */
    public int getStartC() { return startC; }
    /** 终点行 */
    public int getEndR() { return endR; }
    /** 终点列 */
    public int getEndC() { return endC; }

    /** 该格是否为起点 */
    public boolean isStart(int r, int c) { return r == startR && c == startC; }

    /** 该格是否为终点 */
    public boolean isEnd(int r, int c) { return r == endR && c == endC; }

    /** 设置起点：越界或与终点重合返回 false；目标格得分点一并清除，保证起终点不参与计分 */
    public boolean setStart(int r, int c) {
        if (!inBounds(r, c) || isEnd(r, c)) return false;
        startR = r;
        startC = c;
        scoreDigits[r][c] = SCORE_NONE;
        return true;
    }

    /** 设置终点：规则与 setStart 对称 */
    public boolean setEnd(int r, int c) {
        if (!inBounds(r, c) || isStart(r, c)) return false;
        endR = r;
        endC = c;
        scoreDigits[r][c] = SCORE_NONE;
        return true;
    }

    /** 起终点复位到左上角 / 右下角（换尺寸后随新模型自动生效） */
    public void resetEndpoints() {
        startR = 0;
        startC = 0;
        endR = rows - 1;
        endC = cols - 1;
    }

    /** 行列是否在界内 */
    private boolean inBounds(int r, int c) {
        return r >= 0 && r < rows && c >= 0 && c < cols;
    }

    /** 一次性设定起终点（盘面载入用）：越界或两点重合返回 false，且不改变现有状态 */
    public boolean setEndpoints(int sr, int sc, int er, int ec) {
        if (!inBounds(sr, sc) || !inBounds(er, ec)) return false;
        if (sr == er && sc == ec) return false;
        startR = sr; startC = sc;
        endR = er;   endC = ec;
        scoreDigits[sr][sc] = SCORE_NONE;
        scoreDigits[er][ec] = SCORE_NONE;
        return true;
    }

    // @anchor: model_endpoints_end


    // @anchor: model_score_points
    // 得分点即"标记数字"：0 = 无得分点，1~9 = 该得分点的标记（不同得分点可重复）；起终点恒不参与计分
    /** 无得分点 */
    public static final int SCORE_NONE = 0;
    /** 标记数字的合法区间：一位十进制，上限 9 */
    public static final int MIN_SCORE_DIGIT = 1;
    public static final int MAX_SCORE_DIGIT = 9;
    /** 新建得分点用的默认标记；盘面里的 '#' 与旧布尔接口都还原成它 */
    public static final int DEFAULT_SCORE_DIGIT = 1;

    /** 每格的标记数字，SCORE_NONE 表示无得分点 */
    private int[][] scoreDigits;

    /** 初始化标记矩阵 */
    private void initScorePoints() {
        scoreDigits = new int[rows][cols];
    }

    /** 某格的标记数字（越界与空格都返回 SCORE_NONE） */
    public int getScoreDigit(int r, int c) {
        if (r < 0 || r >= rows || c < 0 || c >= cols) return SCORE_NONE;
        return scoreDigits[r][c];
    }

    /** 检查单元格是否有得分点 */
    public boolean hasScorePoint(int r, int c) {
        return getScoreDigit(r, c) != SCORE_NONE;
    }

    /** 直接设定标记数字（盘面载入用：不加"起终点不可标记"守卫，起终点由载入流程最后落定并清格）；返回是否真的改动 */
    public boolean setScoreDigit(int r, int c, int digit) {
        if (r < 0 || r >= rows || c < 0 || c >= cols) return false;
        int v = normalizeDigit(digit);
        if (scoreDigits[r][c] == v) return false;
        scoreDigits[r][c] = v;
        return true;
    }

    /** 编辑用：起终点所在格永不接受标记（返回 false 表示没有改动），其余等同 setScoreDigit */
    public boolean applyScoreDigit(int r, int c, int digit) {
        if (isStart(r, c) || isEnd(r, c)) return false;
        return setScoreDigit(r, c, digit);
    }

    /** 在指定单元格添加得分点（使用默认标记数字） */
    public void addScorePoint(int r, int c) {
        setScoreDigit(r, c, DEFAULT_SCORE_DIGIT);
    }

    /** 切换指定单元格的得分点状态（有则清除，无则按默认标记添加） */
    public void toggleScorePoint(int r, int c) {
        setScoreDigit(r, c, hasScorePoint(r, c) ? SCORE_NONE : DEFAULT_SCORE_DIGIT);
    }

    /** 清除所有得分点 */
    public void clearScorePoints() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                scoreDigits[r][c] = SCORE_NONE;
            }
        }
    }

    /** 直接设置某格得分点有无（旧接口兼容：有则用默认标记） */
    public void setScorePoint(int r, int c, boolean present) {
        setScoreDigit(r, c, present ? DEFAULT_SCORE_DIGIT : SCORE_NONE);
    }

    /** 标记数字归一化：区间外的值一律视为"无得分点" */
    private static int normalizeDigit(int digit) {
        return (digit < MIN_SCORE_DIGIT || digit > MAX_SCORE_DIGIT) ? SCORE_NONE : digit;
    }

    // @anchor: model_score_points_end
}
