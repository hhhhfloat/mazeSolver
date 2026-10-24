// @anchor: view_intro
// 迷宫视图：Canvas 生命周期 + 编辑模式开关 + 鼠标事件，把绘制交给 MazeRenderer、命中交给 MazeHitTester、编辑交给 MazeEditor。
package com.mazesolver;

import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;

import java.util.List;

/**
 * 迷宫视图：视图层的协调者。自己只持有"画布 / 单元格尺寸 / 悬停与模式状态"，
 * 绘制、命中检测、编辑执行分别委托给 {@link MazeRenderer}、{@link MazeHitTester}、{@link MazeEditor}。
 */
public class MazeView {

    // @anchor: view_pick_mode
    // 起终点拾取模式常量：真值在 MazeEditor，这里保留视图侧别名，避免调用方依赖编辑实现类
    /** 不拾取起终点 */
    public static final int ENDPOINT_NONE = MazeEditor.PICK_NONE;
    /** 下次点击的格子设为起点 */
    public static final int ENDPOINT_START = MazeEditor.PICK_START;
    /** 下次点击的格子设为终点 */
    public static final int ENDPOINT_END = MazeEditor.PICK_END;
    // @anchor: view_pick_mode_end

    // @anchor: view_state_fields
    // 视图状态：模型 / 画布与画笔 / 单元格尺寸 / 三个协作者 / 悬停目标 / 编辑模式与当前标记数字
    private MazeModel model;
    private Canvas canvas;
    private GraphicsContext gc;

    /** 单元格像素大小（由 MazeTheme 的分档表算出） */
    private int cellSize;

    /** 协作者：渲染器（含单帧绘制状态）、叠画数据、编辑执行体 */
    private final MazeRenderer renderer = new MazeRenderer();
    private final MazeRenderer.Frame frame = new MazeRenderer.Frame();
    private final MazeOverlay overlay = new MazeOverlay();
    private final MazeEditor editor;

    /** 编辑（墙壁 / 起终点 / 得分点）被修改后的回调，供主界面清理已失效的路径列表并回显状态 */
    private Runnable onEdited;

    /** 当前鼠标悬浮的单元格与墙壁段（命中检测结果，同时供渲染与按下定位复用） */
    private int hoverR = -1, hoverC = -1;
    private int hoverWallR = -1, hoverWallC = -1, hoverWallDir = -1;

    /** 编辑模式：墙壁 / 得分点 / 起终点拾取（三者互斥，由主界面保证同一时刻只开一个） */
    private boolean wallEditMode = false;
    private boolean scoreEditMode = false;
    /** 当前编辑用的标记数字：按下时落子用此数字，同一格再点同数字即清除 */
    private int scoreEditorDigit = MazeModel.DEFAULT_SCORE_DIGIT;
    /** 当前起终点拾取模式 */
    private int endpointPickMode = ENDPOINT_NONE;

    /** 鼠标是否处于按下状态（拖拽连续编辑的前提） */
    private boolean mouseDown = false;
    // @anchor: view_state_fields_end

    // @anchor: view_constructor
    // 构造即按模型尺寸算好单元格尺寸并建画布，保证首帧可直接绘制
    public MazeView(MazeModel model) {
        this.model = model;
        this.editor = new MazeEditor(model, this::notifyEdited);
        rebuildCanvas(model.getRows(), model.getCols());
    }

    /** 编辑生效后的统一处理：作废当前解法、通知主界面、重绘 */
    private void notifyEdited() {
        frame.solutionPath = null;
        if (onEdited != null) onEdited.run();
        draw();
    }
    // @anchor: view_constructor_end

    // @anchor: view_rebuild_canvas
    // 形状变更时重建画布：尺寸变化后必须重新绑定鼠标事件并作废旧叠画坐标
    /** 当行列数变化时重建 Canvas（单元格尺寸随之变化，旧的叠画坐标作废） */
    public void rebuildCanvas(int rows, int cols) {
        cellSize = MazeTheme.cellSizeFor(rows, cols);
        overlay.clear();
        canvas = new Canvas(cols * cellSize + 1, rows * cellSize + 1);
        gc = canvas.getGraphicsContext2D();
        setupMouseEvents();
    }
    // @anchor: view_rebuild_canvas_end

    // @anchor: view_accessors
    // 模型 / 路径 / 回调注入：供主界面在重建模型、切换列表选中项、编辑后清理列表时调用
    /** 更新模型引用（换尺寸 / 载入盘面时）；编辑执行体同步换模型并清空长按锁定 */
    public void setModel(MazeModel newModel) {
        this.model = newModel;
        editor.setModel(newModel);
    }

    public Canvas getCanvas() {
        return canvas;
    }

    public int getCellSize() {
        return cellSize;
    }

    /** 设置解法路径（null 表示不显示） */
    public void setSolutionPath(List<int[]> path) {
        frame.solutionPath = path;
    }

    /** 设置编辑回调（墙壁 / 起终点 / 得分点任一处变更后触发） */
    public void setOnEdited(Runnable callback) {
        this.onEdited = callback;
    }
    // @anchor: view_accessors_end

    // @anchor: view_overlay_api
    // 叠画接口：路径的坐标化与线段去重都在 MazeOverlay 内完成，视图只转发
    /** 设置抽样叠画的路径（每条为格子坐标序列）；传 null 或空即关闭叠画 */
    public void setOverlayPaths(List<List<int[]>> paths) {
        overlay.setPaths(model, cellSize, paths);
    }

    /** 关闭抽样叠画（枚举结果作废 / 进入编辑 / 画布重算尺寸时调用） */
    public void clearOverlayPaths() {
        overlay.clear();
    }

    /** 当前正在叠画的抽样路径条数 */
    public int getOverlayPathCount() {
        return overlay.getPathCount();
    }

    /** 去重后的叠画线段总数（绘制开销的直接来源，供自测与状态回显） */
    public int getOverlaySegmentCount() {
        return overlay.getSegmentCount();
    }
    // @anchor: view_overlay_api_end

    // @anchor: view_set_edit_mode
    // 编辑模式开关：退出编辑时清空长按锁定；任一编辑模式开启时光标即为十字
    /** 设置墙壁编辑模式 */
    public void setWallEditMode(boolean editing) {
        this.wallEditMode = editing;
        if (!editing) resetLongPressState();
        updateCursor();
    }

    /** 设置得分点编辑模式 */
    public void setScoreEditMode(boolean editing) {
        this.scoreEditMode = editing;
        if (!editing) resetLongPressState();
        updateCursor();
    }

    /** 设置起终点拾取模式（ENDPOINT_NONE / ENDPOINT_START / ENDPOINT_END） */
    public void setEndpointEditMode(int mode) {
        this.endpointPickMode = mode;
        if (mode == ENDPOINT_NONE) resetLongPressState();
        updateCursor();
    }

    /** 设置编辑得分点时使用的标记数字；区间外的值回落到默认标记 */
    public void setScoreEditDigit(int digit) {
        if (digit >= MazeModel.MIN_SCORE_DIGIT && digit <= MazeModel.MAX_SCORE_DIGIT) {
            scoreEditorDigit = digit;
        } else {
            scoreEditorDigit = MazeModel.DEFAULT_SCORE_DIGIT;
        }
        if (scoreEditMode) draw();
    }

    /** 当前编辑用的标记数字 */
    public int getScoreEditDigit() {
        return scoreEditorDigit;
    }

    /** 光标：任一编辑模式开启时为十字，否则恢复箭头 */
    private void updateCursor() {
        if (wallEditMode || scoreEditMode || endpointPickMode != ENDPOINT_NONE) {
            canvas.setCursor(Cursor.CROSSHAIR);
        } else {
            canvas.setCursor(Cursor.DEFAULT);
        }
    }

    /** 复位长按状态（模式切换、松开鼠标、移出画布时调用） */
    private void resetLongPressState() {
        mouseDown = false;
        editor.reset();
    }
    // @anchor: view_set_edit_mode_end

    // @anchor: view_draw
    // 重绘：先把视图状态同步进单帧绘制快照，再交给渲染器按固定层次绘制
    /** 绘制整个迷宫 */
    public void draw() {
        frame.overlay = overlay;
        frame.hoverRow = hoverR;
        frame.hoverCol = hoverC;
        frame.hoverWallRow = hoverWallR;
        frame.hoverWallCol = hoverWallC;
        frame.hoverWallDir = hoverWallDir;
        frame.wallEditMode = wallEditMode;
        frame.scoreEditMode = scoreEditMode;
        frame.pickMode = endpointPickMode;
        frame.scoreEditorDigit = scoreEditorDigit;
        renderer.draw(gc, model, cellSize, frame);
    }
    // @anchor: view_draw_end

    // @anchor: view_mouse_events
    // 鼠标交互：移动只刷新悬停；按下锁定方向并交给编辑分发，拖拽沿锁定方向连续生效；起终点只在按下时拾取一次
    /** 注册鼠标事件 */
    private void setupMouseEvents() {
        canvas.setOnMouseMoved(e -> {
            if (wallEditMode) {
                findWall(e.getX(), e.getY());
                draw();
            } else if (scoreEditMode || endpointPickMode != ENDPOINT_NONE) {
                findCell(e.getX(), e.getY());
                draw();
            }
        });

        canvas.setOnMousePressed(e -> {
            mouseDown = true;
            if (wallEditMode) {
                findWall(e.getX(), e.getY());
                dispatchWallEdit(true);
            } else if (scoreEditMode) {
                findCell(e.getX(), e.getY());
                dispatchScoreEdit(true);
            } else if (endpointPickMode != ENDPOINT_NONE) {
                findCell(e.getX(), e.getY());
                dispatchEndpointPick();
            }
        });

        canvas.setOnMouseDragged(e -> {
            if (!mouseDown) return;
            if (wallEditMode) {
                findWall(e.getX(), e.getY());
                dispatchWallEdit(false);
                draw();
            } else if (scoreEditMode) {
                findCell(e.getX(), e.getY());
                dispatchScoreEdit(false);
                draw();
            }
        });

        canvas.setOnMouseReleased(e -> resetLongPressState());

        canvas.setOnMouseExited(e -> {
            clearHover();
            resetLongPressState();
            draw();
        });
    }


    // @anchor: view_mouse_events_end

    // @anchor: view_hit
    // 命中检测薄壳：结果写进视图的悬停字段，几何判定本身在 MazeHitTester 里（含外边界四侧）
    /** 检测鼠标是否悬停在某段墙壁上，并记录悬停目标 */
    private void findWall(double mx, double my) {
        MazeHitTester.WallHit hit = MazeHitTester.findWall(model, cellSize, mx, my);
        if (hit == null) {
            hoverWallR = -1;
            hoverWallC = -1;
            hoverWallDir = -1;
            return;
        }
        hoverWallR = hit.row();
        hoverWallC = hit.col();
        hoverWallDir = hit.dir();
    }

    /** 检测鼠标所在单元格，并记录悬停目标 */
    private void findCell(double mx, double my) {
        MazeHitTester.CellHit hit = MazeHitTester.findCell(model, cellSize, mx, my);
        hoverR = hit == null ? -1 : hit.row();
        hoverC = hit == null ? -1 : hit.col();
    }

    /** 当前悬停的墙壁段（未命中返回 null） */
    private MazeHitTester.WallHit currentWallHit() {
        if (hoverWallR < 0 || hoverWallC < 0 || hoverWallDir < 0) return null;
        return new MazeHitTester.WallHit(hoverWallR, hoverWallC, hoverWallDir);
    }

    /** 清空全部悬停目标 */
    private void clearHover() {
        hoverR = -1;
        hoverC = -1;
        hoverWallR = -1;
        hoverWallC = -1;
        hoverWallDir = -1;
    }
    // @anchor: view_hit_end

    // @anchor: view_edit_dispatch
    // 编辑分发：把"当前悬停目标 + 当前模式"翻译成一次 MazeEditor 调用，按下与拖拽共用同一入口；
    // 具体落子规则（加/拆墙、落/清标记、起终点拾取）都在 MazeEditor 内，视图不再各写一份
    /** 墙壁编辑分发（pressed=true 表示按下，负责锁定方向） */
    private void dispatchWallEdit(boolean pressed) {
        MazeHitTester.WallHit hit = currentWallHit();
        if (pressed) {
            editor.beginWall(hit);
        } else {
            editor.dragWall(hit);
        }
    }

    /** 得分点编辑分发（pressed=true 表示按下，负责锁定落子数字） */
    private void dispatchScoreEdit(boolean pressed) {
        if (hoverR < 0 || hoverC < 0) return;
        if (pressed) {
            editor.beginScore(hoverR, hoverC, scoreEditorDigit);
        } else {
            editor.dragScore(hoverR, hoverC);
        }
    }

    /** 起终点拾取分发：只在按下时执行一次 */
    private void dispatchEndpointPick() {
        if (hoverR < 0 || hoverC < 0) return;
        editor.applyEndpointAction(endpointPickMode, hoverR, hoverC);
    }


    // @anchor: view_edit_dispatch_end
}
