// @anchor: maze_theme_intro
// 视图层的视觉常量与等比换算：单元格尺寸分档表、叠画调色板、线宽/半径/字号/命中阈值，全部随 cellSize 缩放。
package com.mazesolver;

import javafx.scene.paint.Color;

/**
 * 迷宫视图的视觉主题：尺寸分档、配色与随单元格尺寸缩放的绘制度量。
 * 所有绘制与命中检测的魔法数字集中在此，改观感只改这一个文件。
 */
public final class MazeTheme {

    private MazeTheme() {
    }

    // @anchor: theme_cell_size
    // 单元格尺寸查表：边长分档 → 像素，档位越大格子越小；超出最高档用最小像素兜底
    /** 边长分档上界（升序）与对应单元格像素 */
    private static final int[] DIM_TIERS = {25, 40, 60, 80, 120, 200};
    private static final int[] TIER_CELL_PX = {30, 20, 14, 10, 8, 6};
    /** 超大迷宫的兜底单元格像素（保证任何尺寸都可绘制） */
    private static final int MIN_CELL_PX = 4;

    /** 根据行列数计算合适的单元格像素大小 */
    public static int cellSizeFor(int rows, int cols) {
        int maxDim = Math.max(rows, cols);
        for (int i = 0; i < DIM_TIERS.length; i++) {
            if (maxDim <= DIM_TIERS[i]) return TIER_CELL_PX[i];
        }
        return MIN_CELL_PX;
    }
    // @anchor: theme_cell_size_end

    // @anchor: theme_palette
    // 叠画调色板：半透明细线，按抽样顺序分桶着色，避免盖住墙壁与当前选中的主解法
    private static final Color[] OVERLAY_PALETTE = {
            Color.rgb(30, 136, 229, 0.50), Color.rgb(216, 67, 21, 0.50),
            Color.rgb(46, 125, 50, 0.50), Color.rgb(106, 27, 154, 0.50),
            Color.rgb(0, 131, 143, 0.50), Color.rgb(239, 108, 0, 0.50)
    };

    /** 叠画分桶数量（= 调色板颜色数） */
    public static int overlayBucketCount() {
        return OVERLAY_PALETTE.length;
    }

    /** 第 bucket 桶对应的颜色（越界按取模回绕） */
    public static Color overlayColor(int bucket) {
        return OVERLAY_PALETTE[Math.floorMod(bucket, OVERLAY_PALETTE.length)];
    }
    // @anchor: theme_palette_end

    // @anchor: theme_metrics
    // 随单元格尺寸缩放的绘制度量：线宽、得分点半径与字号、墙壁命中阈值
    /** 墙壁基础线宽 */
    public static double wallLineWidth() {
        return 2;
    }

    /** 悬停高亮线宽（粗于基础线宽，保证小格子也看得见） */
    public static double hoverLineWidth(int cellSize) {
        return Math.max(2, cellSize / 10.0);
    }

    /** 主解法线宽 */
    public static double pathLineWidth(int cellSize) {
        return Math.max(2, cellSize / 15.0);
    }

    /** 抽样叠画线宽（最细，只用于表达分布） */
    public static double overlayLineWidth(int cellSize) {
        return Math.max(1, cellSize / 20.0);
    }

    /** 得分点圆半径 */
    public static double scoreRadius(int cellSize) {
        return Math.max(2, cellSize * 0.15);
    }

    /** 格子够大才在得分点圆内写标记数字（太小则只保留圆点） */
    public static boolean showsScoreDigit(int cellSize) {
        return cellSize >= 14;
    }

    /** 标记数字的字号 */
    public static double scoreFontSize(int cellSize) {
        return Math.max(7, cellSize * (cellSize >= 26 ? 0.5 : 0.62));
    }

    /** 墙壁命中阈值：鼠标离墙线多近算命中 */
    public static double wallHitThreshold(int cellSize) {
        return Math.min(cellSize * 0.2, 6);
    }
    // @anchor: theme_metrics_end
}
