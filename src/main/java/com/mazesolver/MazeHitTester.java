// @anchor: maze_hit_tester_intro
// 鼠标命中检测（无状态）：把画布像素坐标折算成"命中的墙壁段"或"命中的单元格"，供视图记录悬停目标。
package com.mazesolver;

/**
 * 像素坐标 → 迷宫元素的命中检测。只做几何换算、不保存任何状态，
 * 因此同一套判定既能被视图的鼠标事件复用，也能被无界面自测直接调用。
 */
public final class MazeHitTester {

    private MazeHitTester() {
    }

    // @anchor: hit_types
    // 命中结果：墙壁用"行列 + 方向"表达（dir 取 MazeModel 的四向常量），单元格只用行列；未命中统一用 null
    /** 命中的墙壁段：r/c 为规范侧所在格，dir 为该侧的墙方向 */
    public record WallHit(int row, int col, int dir) {
    }

    /** 命中的单元格 */
    public record CellHit(int row, int col) {
    }
    // @anchor: hit_types_end

    // @anchor: hit_find_wall
    // 墙壁命中：每段物理墙只认一个"规范侧"（北侧 / 西侧 / 末行南侧 / 末列东侧），内部墙与外边界墙都按此去重
    /** 检测鼠标是否悬停在某段墙壁上（含迷宫外边界）；未命中返回 null */
    public static WallHit findWall(MazeModel model, int cellSize, double mx, double my) {
        int rows = model.getRows();
        int cols = model.getCols();
        double threshold = MazeTheme.wallHitThreshold(cellSize);

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int x = c * cellSize;
                int y = r * cellSize;

                // 北侧：含首行上边界（内部墙只由此侧的 NORTH 表示，天然去重）
                if (my >= y - threshold && my <= y + threshold
                        && mx >= x + threshold && mx <= x + cellSize - threshold) {
                    return new WallHit(r, c, MazeModel.NORTH);
                }
                // 西侧：含首列左边界
                if (mx >= x - threshold && mx <= x + threshold
                        && my >= y + threshold && my <= y + cellSize - threshold) {
                    return new WallHit(r, c, MazeModel.WEST);
                }
                // 下边界：仅末行的 SOUTH
                if (r == rows - 1
                        && my >= y + cellSize - threshold && my <= y + cellSize + threshold
                        && mx >= x + threshold && mx <= x + cellSize - threshold) {
                    return new WallHit(r, c, MazeModel.SOUTH);
                }
                // 右边界：仅末列的 EAST
                if (c == cols - 1
                        && mx >= x + cellSize - threshold && mx <= x + cellSize + threshold
                        && my >= y + threshold && my <= y + cellSize - threshold) {
                    return new WallHit(r, c, MazeModel.EAST);
                }
            }
        }
        return null;
    }
    // @anchor: hit_find_wall_end

    // @anchor: hit_find_cell
    // 单元格命中：像素坐标直接整除单元格尺寸得到行列，越界返回 null
    /** 检测鼠标所在单元格；落在画布外返回 null */
    public static CellHit findCell(MazeModel model, int cellSize, double mx, double my) {
        int c = (int) (mx / cellSize);
        int r = (int) (my / cellSize);
        if (r < 0 || r >= model.getRows() || c < 0 || c >= model.getCols()) return null;
        return new CellHit(r, c);
    }
    // @anchor: hit_find_cell_end
}
