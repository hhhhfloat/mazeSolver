// @anchor: maze_renderer_intro
// 纯绘制：把"一帧的绘制输入"（解法路径、叠画、悬停目标、编辑模式）按固定层次画到画布上，不持有任何交互状态。
package com.mazesolver;

import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.List;

/**
 * 迷宫渲染器：只依赖 {@link Frame} 里的数据与模型，不关心鼠标、不关心编辑状态如何变化。
 * 绘制层次固定为"迷宫 → 得分点 → 抽样叠画 → 悬停高亮 → 主解法"。
 */
public final class MazeRenderer {

    // @anchor: render_frame
    // 一帧的绘制输入：解法路径、叠画线段、悬停目标、编辑模式与当前标记数字；由视图在每次重绘前填入
    /** 单帧绘制状态（可变的小型快照，重绘前由视图写入） */
    public static final class Frame {
        public List<int[]> solutionPath;
        public MazeOverlay overlay;

        public int hoverRow = -1, hoverCol = -1;
        public int hoverWallRow = -1, hoverWallCol = -1, hoverWallDir = -1;

        public boolean wallEditMode;
        public boolean scoreEditMode;
        public int pickMode = MazeEditor.PICK_NONE;
        public int scoreEditorDigit = MazeModel.DEFAULT_SCORE_DIGIT;
    }
    // @anchor: render_frame_end

    // @anchor: render_draw
    // 绘制入口：清屏后按固定层次叠画，顺序决定"谁盖住谁"
    /** 绘制整个迷宫 */
    public void draw(GraphicsContext gc, MazeModel model, int cellSize, Frame frame) {
        gc.clearRect(0, 0, model.getCols() * cellSize + 1, model.getRows() * cellSize + 1);
        drawMaze(gc, model, cellSize);
        drawScorePoints(gc, model, cellSize);
        drawOverlay(gc, cellSize, frame);
        drawHover(gc, model, cellSize, frame);
        drawPath(gc, cellSize, frame);
    }
    // @anchor: render_draw_end

    // @anchor: render_maze
    // 底图：白底 + 绿起红终（坐标取自模型，可在任意格）+ 逐格描边四向墙壁
    /** 绘制迷宫墙壁和起终点 */
    private void drawMaze(GraphicsContext gc, MazeModel model, int cellSize) {
        int rows = model.getRows();
        int cols = model.getCols();

        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, cols * cellSize + 1, rows * cellSize + 1);

        // 起点（绿色）与终点（红色）
        gc.setFill(Color.LIMEGREEN);
        gc.fillRect(model.getStartC() * cellSize + 1, model.getStartR() * cellSize + 1,
                cellSize - 1, cellSize - 1);
        gc.setFill(Color.ORANGERED);
        gc.fillRect(model.getEndC() * cellSize + 1, model.getEndR() * cellSize + 1,
                cellSize - 1, cellSize - 1);

        // 墙壁
        gc.setStroke(Color.BLACK);
        gc.setLineWidth(MazeTheme.wallLineWidth());
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int x = c * cellSize;
                int y = r * cellSize;
                if (model.hasWall(r, c, MazeModel.NORTH)) {
                    gc.strokeLine(x, y, x + cellSize, y);
                }
                if (model.hasWall(r, c, MazeModel.SOUTH)) {
                    gc.strokeLine(x, y + cellSize, x + cellSize, y + cellSize);
                }
                if (model.hasWall(r, c, MazeModel.WEST)) {
                    gc.strokeLine(x, y, x, y + cellSize);
                }
                if (model.hasWall(r, c, MazeModel.EAST)) {
                    gc.strokeLine(x + cellSize, y, x + cellSize, y + cellSize);
                }
            }
        }
    }
    // @anchor: render_maze_end

    // @anchor: render_scores
    // 得分点：金色实心圆 + 圆内的标记数字，半径与字号随单元格尺寸缩放（格子太小时只画圆不写字）
    /** 绘制得分点及其标记数字 */
    private void drawScorePoints(GraphicsContext gc, MazeModel model, int cellSize) {
        int rows = model.getRows();
        int cols = model.getCols();
        double radius = MazeTheme.scoreRadius(cellSize);
        // 数字是路径筛选的依据，因此在格子够大时务必画出来；太小则只保留圆点
        boolean drawDigit = MazeTheme.showsScoreDigit(cellSize);
        if (drawDigit) {
            gc.setFont(Font.font("System", FontWeight.BOLD, MazeTheme.scoreFontSize(cellSize)));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setTextBaseline(VPos.CENTER);
        }

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int digit = model.getScoreDigit(r, c);
                if (digit == MazeModel.SCORE_NONE) continue;
                double cx = c * cellSize + cellSize / 2.0;
                double cy = r * cellSize + cellSize / 2.0;
                gc.setFill(Color.GOLD);
                gc.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
                if (drawDigit) {
                    gc.setFill(Color.rgb(60, 40, 0));
                    gc.fillText(String.valueOf(digit), cx, cy);
                }
            }
        }
        if (drawDigit) {
            gc.setTextAlign(TextAlignment.LEFT);
            gc.setTextBaseline(VPos.BASELINE);
        }
    }
    // @anchor: render_scores_end

    // @anchor: render_hover
    // 悬停高亮：按当前编辑模式三选一，墙壁用橙色线段、得分点用金/红方框、起终点用绿/红方框
    /** 绘制悬停高亮（无编辑模式时不画） */
    private void drawHover(GraphicsContext gc, MazeModel model, int cellSize, Frame frame) {
        if (frame.wallEditMode) {
            drawWallHover(gc, cellSize, frame);
        } else if (frame.scoreEditMode) {
            drawCellHover(gc, model, cellSize, frame);
        } else if (frame.pickMode != MazeEditor.PICK_NONE) {
            drawEndpointHover(gc, model, cellSize, frame);
        }
    }

    /** 墙壁悬停高亮（橙色） */
    private void drawWallHover(GraphicsContext gc, int cellSize, Frame frame) {
        int r = frame.hoverWallRow, c = frame.hoverWallCol, dir = frame.hoverWallDir;
        if (r < 0 || c < 0 || dir < 0) return;

        int x = c * cellSize;
        int y = r * cellSize;
        gc.setStroke(Color.ORANGE);
        gc.setLineWidth(MazeTheme.hoverLineWidth(cellSize));
        double margin = Math.max(2, cellSize * 0.07);
        switch (dir) {
            case MazeModel.NORTH:
                gc.strokeLine(x + margin, y, x + cellSize - margin, y);
                break;
            case MazeModel.SOUTH:
                gc.strokeLine(x + margin, y + cellSize, x + cellSize - margin, y + cellSize);
                break;
            case MazeModel.WEST:
                gc.strokeLine(x, y + margin, x, y + cellSize - margin);
                break;
            case MazeModel.EAST:
                gc.strokeLine(x + cellSize, y + margin, x + cellSize, y + cellSize - margin);
                break;
            default:
                break;
        }
        gc.setLineWidth(MazeTheme.wallLineWidth());
    }

    /** 单元格悬停高亮：将落子成当前标记数字时用金色，将清除已有标记时用红色；起终点所在格不可标记 */
    private void drawCellHover(GraphicsContext gc, MazeModel model, int cellSize, Frame frame) {
        int r = frame.hoverRow, c = frame.hoverCol;
        if (r < 0 || c < 0) return;
        if (model.isStart(r, c) || model.isEnd(r, c)) return;

        int x = c * cellSize;
        int y = r * cellSize;
        boolean willClear = model.getScoreDigit(r, c) == frame.scoreEditorDigit;
        gc.setFill(willClear ? Color.rgb(255, 99, 71, 0.35) : Color.rgb(255, 215, 0, 0.4));
        gc.fillRect(x + 1, y + 1, cellSize - 2, cellSize - 2);
    }

    /** 起终点拾取高亮：设起点为绿框、设终点为红框；目标格是另一端点时不高亮（不可选） */
    private void drawEndpointHover(GraphicsContext gc, MazeModel model, int cellSize, Frame frame) {
        int r = frame.hoverRow, c = frame.hoverCol;
        if (r < 0 || c < 0) return;

        boolean pickingStart = frame.pickMode == MazeEditor.PICK_START;
        if (pickingStart ? model.isEnd(r, c) : model.isStart(r, c)) return;

        int x = c * cellSize;
        int y = r * cellSize;
        gc.setFill(pickingStart ? Color.rgb(50, 205, 50, 0.45) : Color.rgb(255, 69, 0, 0.45));
        gc.fillRect(x + 1, y + 1, cellSize - 2, cellSize - 2);
        gc.setStroke(pickingStart ? Color.LIMEGREEN : Color.ORANGERED);
        gc.setLineWidth(MazeTheme.hoverLineWidth(cellSize));
        gc.strokeRect(x + 1, y + 1, cellSize - 2, cellSize - 2);
        gc.setLineWidth(MazeTheme.wallLineWidth());
    }
    // @anchor: render_hover_end

    // @anchor: render_overlay
    // 抽样叠画：只做描线——线段已去重、坐标已预折算，重绘开销与枚举总条数无关
    /** 绘制抽样叠画（位于墙壁与得分点之上、悬停高亮与主解法之下） */
    private void drawOverlay(GraphicsContext gc, int cellSize, Frame frame) {
        MazeOverlay overlay = frame.overlay;
        if (overlay == null || overlay.isEmpty()) return;
        List<List<double[]>> buckets = overlay.getBuckets();
        if (buckets == null) return;

        gc.setLineWidth(MazeTheme.overlayLineWidth(cellSize));
        for (int i = 0; i < buckets.size(); i++) {
            List<double[]> segments = buckets.get(i);
            if (segments.isEmpty()) continue;
            gc.setStroke(MazeTheme.overlayColor(i));
            for (double[] s : segments) {
                gc.strokeLine(s[0], s[1], s[2], s[3]);
            }
        }
        gc.setLineWidth(MazeTheme.wallLineWidth());
    }
    // @anchor: render_overlay_end

    // @anchor: render_path
    // 主解法：蓝色折线连接各格中心，线宽随单元格尺寸自适应，画在最上层
    /** 绘制当前解法路径 */
    private void drawPath(GraphicsContext gc, int cellSize, Frame frame) {
        List<int[]> path = frame.solutionPath;
        if (path == null || path.isEmpty()) return;

        gc.setStroke(Color.DODGERBLUE);
        gc.setLineWidth(MazeTheme.pathLineWidth(cellSize));
        for (int i = 0; i < path.size() - 1; i++) {
            int[] from = path.get(i);
            int[] to = path.get(i + 1);
            gc.strokeLine(from[1] * cellSize + cellSize / 2.0, from[0] * cellSize + cellSize / 2.0,
                    to[1] * cellSize + cellSize / 2.0, to[0] * cellSize + cellSize / 2.0);
        }
        gc.setLineWidth(MazeTheme.wallLineWidth());
    }
    // @anchor: render_path_end
}
