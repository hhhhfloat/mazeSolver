// @anchor: maze_control_panel_intro
// 顶部控制面板：尺寸栏 / 动作栏 / 编辑模式栏 / 枚举上限栏 / 状态栏五行控件的创建与布局，只搭结构、不绑业务动作。
package com.mazesolver;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * 顶部五行控制区。这里只负责"有哪些控件、怎么排"，
 * 事件绑定与状态同步由 {@link MazeApp} 统一完成——保证"控件建了就必须挂进布局"这条约定只在一处成立。
 */
public final class MazeControlPanel {

    // @anchor: panel_score_digits
    // "标记数字"下拉的取值：区间统一取自模型常量，页面不再写死 1~9
    /** 可选标记数字（MIN_SCORE_DIGIT~MAX_SCORE_DIGIT） */
    private static final List<Integer> SCORE_DIGIT_OPTIONS = buildScoreDigitOptions();

    private static List<Integer> buildScoreDigitOptions() {
        List<Integer> options = new ArrayList<>();
        for (int d = MazeModel.MIN_SCORE_DIGIT; d <= MazeModel.MAX_SCORE_DIGIT; d++) {
            options.add(d);
        }
        return options;
    }
    // @anchor: panel_score_digits_end

    // @anchor: panel_fields
    // 控件引用：尺寸 / 动作 / 编辑开关与标记数字 / 枚举上限 / 三个状态标签——主界面据此绑定事件与回写状态
    private final TextField tfRows;
    private final TextField tfCols;
    private final Label lblShapeHint;

    private final Button btnGenerate;
    private final Button btnSolve;
    private final Button btnReset;
    private final Button btnClear;
    private final Button btnSaveBoard;
    private final Button btnLoadBoard;
    private final Button btnExportPaths;

    private final ToggleButton btnEditWall;
    private final ToggleButton btnEditScore;
    private final ToggleButton btnEditStart;
    private final ToggleButton btnEditEnd;
    private final Button btnResetEndpoints;
    private final ComboBox<Integer> cbScoreDigit;

    private final TextField tfMaxPaths;
    private final Label lblEnumHint;
    private final Label lblEndpointHint;
    private final Label lblFileHint;

    /** 顶部五行容器 */
    private final VBox topBar;
    /** 尺寸"应用"按钮（与其它控件一同创建，主界面借此绑定尺寸变更动作） */
    private final Button btnApplyShape;


    // @anchor: panel_fields_end

    // @anchor: panel_constructor
    // 构造即建好全部控件与布局；初始尺寸写入输入框，状态标签给出"当前 N×M"
    public MazeControlPanel(int rows, int cols) {
        tfRows = new TextField(String.valueOf(rows));
        tfRows.setPrefWidth(56);
        tfCols = new TextField(String.valueOf(cols));
        tfCols.setPrefWidth(56);
        lblShapeHint = new Label();

        btnApplyShape = new Button("应用");
        btnApplyShape.setStyle("-fx-font-weight: bold; -fx-background-color: #4CAF50; -fx-text-fill: white;");

        btnGenerate = new Button("生成迷宫");
        btnSolve = new Button("求解");
        btnReset = new Button("重置");
        btnClear = new Button("清空");
        btnSaveBoard = new Button("保存盘面");
        btnLoadBoard = new Button("载入盘面");
        btnExportPaths = new Button("导出解法");

        btnEditWall = new ToggleButton("编辑墙壁");
        btnEditScore = new ToggleButton("编辑得分点");
        btnEditStart = new ToggleButton("设起点");
        btnEditEnd = new ToggleButton("设终点");
        btnResetEndpoints = new Button("复位起终点");
        cbScoreDigit = new ComboBox<>(FXCollections.observableArrayList(SCORE_DIGIT_OPTIONS));
        cbScoreDigit.getSelectionModel().select(Integer.valueOf(MazeModel.DEFAULT_SCORE_DIGIT));
        cbScoreDigit.setPrefWidth(64);

        tfMaxPaths = new TextField(String.valueOf(MazeSolver.DEFAULT_MAX_PATHS));
        tfMaxPaths.setPrefWidth(72);
        lblEnumHint = new Label();
        lblEndpointHint = new Label();
        lblFileHint = new Label();

        topBar = buildTopBar(rows, cols);
    }

    // @anchor: panel_constructor_end

    // @anchor: panel_top_layout
    // 顶部五行：尺寸栏 / 动作栏 / 编辑模式栏 / 枚举上限栏 / 状态栏——每种后端能力都在页面上有入口
    private VBox buildTopBar(int rows, int cols) {
        // 尺寸栏：两个输入框 + 应用按钮 + 状态提示，校验与重建都在主界面的 applyShapeChange 内完成
        Label lblRows = new Label("行数:");
        Label lblCols = new Label("列数:");
        UiHint.show(lblShapeHint, "当前 " + rows + "×" + cols, false, UiHint.NORMAL);
        HBox shapeBar = new HBox(8, lblRows, tfRows, lblCols, tfCols, btnApplyShape, lblShapeHint);
        shapeBar.setPadding(new Insets(10, 12, 2, 12));
        shapeBar.setAlignment(Pos.CENTER);

        // 动作栏：与编辑模式无关的全局操作（生成 / 求解 / 重置 / 清空 + 三个文件操作）
        HBox actionBar = new HBox(12, btnGenerate, btnSolve, btnReset, btnClear,
                btnSaveBoard, btnLoadBoard, btnExportPaths);
        actionBar.setPadding(new Insets(4, 12, 0, 12));
        actionBar.setAlignment(Pos.CENTER);

        // 编辑模式栏：四个互斥开关 + 起终点复位 + "标记数字"下拉
        Label lblScoreDigit = new Label("标记数字:");
        lblScoreDigit.setStyle("-fx-font-size: 11px;");
        HBox editBar = new HBox(10, btnEditWall, btnEditScore, btnEditStart, btnEditEnd,
                btnResetEndpoints, lblScoreDigit, cbScoreDigit);
        editBar.setPadding(new Insets(4, 12, 0, 12));
        editBar.setAlignment(Pos.CENTER);

        // 枚举上限栏：上限就是"最多搜多久"的旋钮，条数与输出位置紧随其后回显
        Label lblMaxPaths = new Label("解数上限:");
        Label lblUnit = new Label("条");
        HBox enumBar = new HBox(8, lblMaxPaths, tfMaxPaths, lblUnit, lblEnumHint);
        enumBar.setPadding(new Insets(4, 12, 0, 12));
        enumBar.setAlignment(Pos.CENTER);

        // 状态栏：起终点坐标（取自模型）与文件操作提示
        lblEndpointHint.setStyle(UiHint.styleOf(false, "#444"));
        lblFileHint.setStyle(UiHint.styleOf(false, "#444"));
        HBox statusBar = new HBox(18, lblEndpointHint, lblFileHint);
        statusBar.setPadding(new Insets(4, 12, 8, 12));
        statusBar.setAlignment(Pos.CENTER);

        VBox bar = new VBox(shapeBar, actionBar, editBar, enumBar, statusBar);
        bar.setStyle("-fx-background-color: #f0f0f0;");
        return bar;
    }


    // @anchor: panel_top_layout_end

    // @anchor: panel_apply_shape_button
    // "应用"按钮与其他控件一同在构造器中创建，主界面通过这里取到它绑定尺寸变更动作
    public Button getApplyShapeButton() {
        return btnApplyShape;
    }


    // @anchor: panel_apply_shape_button_end

    // @anchor: panel_accessors
    // 控件访问器：主界面绑定事件与回写状态都走这里，面板不暴露内部布局结构
    public VBox getTopBar() {
        return topBar;
    }

    public TextField getRowsField() {
        return tfRows;
    }

    public TextField getColsField() {
        return tfCols;
    }

    public Button getGenerateButton() {
        return btnGenerate;
    }

    public Button getSolveButton() {
        return btnSolve;
    }

    public Button getResetButton() {
        return btnReset;
    }

    public Button getClearButton() {
        return btnClear;
    }

    public Button getSaveBoardButton() {
        return btnSaveBoard;
    }

    public Button getLoadBoardButton() {
        return btnLoadBoard;
    }

    public Button getExportPathsButton() {
        return btnExportPaths;
    }

    public ToggleButton getEditWallButton() {
        return btnEditWall;
    }

    public ToggleButton getEditScoreButton() {
        return btnEditScore;
    }

    public ToggleButton getEditStartButton() {
        return btnEditStart;
    }

    public ToggleButton getEditEndButton() {
        return btnEditEnd;
    }

    public Button getResetEndpointsButton() {
        return btnResetEndpoints;
    }

    public ComboBox<Integer> getScoreDigitBox() {
        return cbScoreDigit;
    }

    public TextField getMaxPathsField() {
        return tfMaxPaths;
    }

    /** 当前选中的标记数字（未选时用模型默认值） */
    public int getSelectedScoreDigit() {
        Integer picked = cbScoreDigit.getValue();
        return picked == null ? MazeModel.DEFAULT_SCORE_DIGIT : picked;
    }
    // @anchor: panel_accessors_end

    // @anchor: panel_hints
    // 状态回显：尺寸 / 枚举 / 起终点 / 文件四条提示，配色统一走 UiHint，页面不缓存业务状态
    /** 尺寸提示（error=true 红字） */
    public void setShapeHint(String msg, boolean error) {
        UiHint.show(lblShapeHint, msg, error, UiHint.NORMAL);
    }

    /** 枚举状态（error=true 红字，用于"达上限"与"输入非法"） */
    public void setEnumHint(String msg, boolean error) {
        UiHint.show(lblEnumHint, msg, error, UiHint.NORMAL);
    }

    /** 起终点坐标与拾取提示 */
    public void setEndpointHint(String msg) {
        UiHint.show(lblEndpointHint, msg, false, "#444");
    }

    /** 文件操作提示（保存 / 载入 / 导出的结果与失败原因） */
    public void setFileHint(String msg, boolean error) {
        UiHint.show(lblFileHint, msg, error, UiHint.OK);
    }

    /** 回写尺寸输入框（应用尺寸、载入盘面后同步显示） */
    public void setShapeTexts(int rows, int cols) {
        tfRows.setText(String.valueOf(rows));
        tfCols.setText(String.valueOf(cols));
    }
    // @anchor: panel_hints_end
}
