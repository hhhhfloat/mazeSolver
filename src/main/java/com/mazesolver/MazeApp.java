// @anchor: app_intro
// JavaFX 主界面：装配顶部控制面板与画布容器，协调生成器/求解器/视图/盘面文件，并持有编辑模式与枚举结果的唯一状态。
package com.mazesolver;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 迷宫求解器主入口 —— JavaFX 应用。
 * 界面结构交给 {@link MazeControlPanel} 与 {@link PathListPanel}，
 * 绘制与编辑交给 {@link MazeView} 一侧；这里只做装配、事件绑定与状态协调。
 */
public class MazeApp extends Application {

    // @anchor: app_output_path
    // 枚举结果自动输出位置：与盘面保存/导出对话框的默认目录保持一致，产物统一落在 tmp/
    /** 全路径枚举的输出文件（相对路径，相对运行目录） */
    private static final String PATH_OUTPUT_FILE = "tmp/all_paths.txt";
    // @anchor: app_output_path_end

    // @anchor: app_fields_shape
    // 尺寸状态：行列数是"当前迷宫尺寸"的唯一来源，输入框只在应用时读取；合法区间统一取自 MazeModel
    /** 首次启动的默认尺寸 */
    private static final int DEFAULT_ROWS = 20;
    private static final int DEFAULT_COLS = 20;

    /** 迷宫行列数（可动态调整） */
    private int rows = DEFAULT_ROWS;
    private int cols = DEFAULT_COLS;
    // @anchor: app_fields_shape_end

    // @anchor: app_fields_core
    // 核心协作者：模型、生成器、求解器、视图始终成组重建，避免尺寸不一致
    private MazeModel model;
    private MazeGenerator generator;
    private MazeSolver solver;
    private MazeView view;
    private Stage stage;
    // @anchor: app_fields_core_end

    // @anchor: app_fields_ui
    // 界面装配：顶部控制面板、右侧列表面板、根布局与中间可滚动画布容器
    private MazeControlPanel panel;
    private PathListPanel pathPanel;
    private BorderPane root;
    private ScrollPane scrollPane;
    private StackPane canvasHolder;
    // @anchor: app_fields_ui_end

    // @anchor: app_fields_result
    // 枚举结果：方向编码源数据；展示与过滤都在 PathListPanel 内完成，这里只持有引用
    private List<List<Integer>> allPaths;
    // @anchor: app_fields_result_end

    // @anchor: app_edit_mode_state
    // 编辑模式唯一状态：四个编辑开关互斥，editMode 是唯一真值来源，按钮选中态由 applyEditMode 反向同步
    /** 编辑模式：墙壁 / 得分点 / 设起点 / 设终点，同一时刻最多一个生效 */
    private enum EditMode { NONE, WALL, SCORE, START, END }

    /** 当前编辑模式 */
    private EditMode editMode = EditMode.NONE;
    // @anchor: app_edit_mode_state_end

    // @anchor: app_start
    // 应用入口：记下窗口引用后交给 buildUI 完成全部装配
    @Override
    public void start(Stage stage) {
        this.stage = stage;
        buildUI();
    }
    // @anchor: app_start_end

    // @anchor: app_build_ui
    // 装配顺序：核心对象 → 列表面板与顶部面板 → 画布滚动画布容器 → 绑定事件 → 挂画布 → 建窗口 → 首屏状态回显
    private void buildUI() {
        initCore();

        // 列表面板按需取当前求解器（换尺寸/载入盘面会重建求解器实例）
        pathPanel = new PathListPanel(() -> solver);
        panel = new MazeControlPanel(rows, cols);

        // 画布装进滚动容器：尺寸放大到超过窗口时出滚动条，而不是把窗口撑到屏幕外
        canvasHolder = new StackPane();
        canvasHolder.setPadding(new Insets(10));
        scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(false);
        scrollPane.setFitToHeight(false);
        scrollPane.setStyle("-fx-background: white; -fx-background-color: white; -fx-border-color: transparent;");
        scrollPane.setContent(canvasHolder);
        // 内容最小尺寸跟随视口：小迷宫居中显示，大迷宫才出现滚动条
        scrollPane.viewportBoundsProperty().addListener((obs, oldBounds, newBounds) -> {
            canvasHolder.setMinWidth(newBounds.getWidth());
            canvasHolder.setMinHeight(newBounds.getHeight());
        });

        root = new BorderPane();
        root.setTop(panel.getTopBar());
        root.setCenter(scrollPane);
        root.setRight(pathPanel);

        wireActions();
        mountCanvas();
        view.draw();

        Scene scene;
        if (stage.getScene() != null) {
            scene = stage.getScene();
            scene.setRoot(root);
        } else {
            scene = new Scene(root);
            stage.setScene(scene);
        }
        updateStageTitle();
        stage.setResizable(true);
        stage.show();

        // 首屏回显：起终点坐标、枚举与叠画状态都取自后端
        refreshEndpointHint();
        refreshEnumHint();
        refreshSampleHint();
    }
    // @anchor: app_build_ui_end

    // @anchor: app_init_core
    // 核心对象成组创建并首次生成迷宫；尺寸变更时同样按此顺序整体重建
    private void initCore() {
        model = new MazeModel(rows, cols);
        generator = new MazeGenerator(model);
        solver = new MazeSolver(model);
        view = new MazeView(model);
        generator.generate();
        view.draw();
    }
    // @anchor: app_init_core_end

    // @anchor: app_wire_actions
    // 事件绑定集中在一处：面板只建控件，动作与状态同步都在主界面，避免"控件建了没接线"
    private void wireActions() {
        // 尺寸栏：按钮与回车等价
        panel.getApplyShapeButton().setOnAction(e -> applyShapeChange());
        panel.getRowsField().setOnAction(e -> applyShapeChange());
        panel.getColsField().setOnAction(e -> applyShapeChange());

        // 动作栏
        panel.getGenerateButton().setOnAction(e -> onGenerate());
        panel.getSolveButton().setOnAction(e -> onSolve());
        panel.getResetButton().setOnAction(e -> onReset());
        panel.getClearButton().setOnAction(e -> onClear());

        // 文件操作
        panel.getSaveBoardButton().setOnAction(e -> saveBoardToFile());
        panel.getLoadBoardButton().setOnAction(e -> loadBoardFromFile());
        panel.getExportPathsButton().setOnAction(e -> exportPathsToFile());

        // 编辑模式开关与起终点复位
        panel.getEditWallButton().setOnAction(e -> toggleEditMode(EditMode.WALL));
        panel.getEditScoreButton().setOnAction(e -> toggleEditMode(EditMode.SCORE));
        panel.getEditStartButton().setOnAction(e -> toggleEditMode(EditMode.START));
        panel.getEditEndButton().setOnAction(e -> toggleEditMode(EditMode.END));
        panel.getResetEndpointsButton().setOnAction(e -> resetEndpointsAndRefresh());
        panel.getScoreDigitBox().setOnAction(e -> view.setScoreEditDigit(panel.getSelectedScoreDigit()));

        // 编辑（改墙 / 改起终点 / 改得分点）都会让既有解法失效 → 借视图回调清空列表并回显起终点坐标
        view.setOnEdited(() -> {
            clearPathList();
            refreshEndpointHint();
        });

        // 右侧列表：选中项回显到画布；叠画开关与条数变化交给主界面驱动视图
        pathPanel.setOnPathSelected(this::showPathOnCanvas);
        pathPanel.setOnOverlayToggle(enabled -> {
            if (enabled) {
                applySampleOverlay();
            } else {
                clearOverlayAndHint();
            }
        });
        pathPanel.setOnOverlayCountChanged(this::applySampleOverlay);
    }
    // @anchor: app_wire_actions_end

    // @anchor: app_generate_action
    // 生成：重新开一条随机迷宫，旧路径列表同时作废
    private void onGenerate() {
        generator.generate();
        view.setSolutionPath(null);
        view.draw();
        clearPathList();
    }
    // @anchor: app_generate_action_end

    // @anchor: app_solve_action
    // 求解：BFS 结果直接上屏；再按上限枚举全部路径，枚举结果自动写入 tmp/ 并填充右侧列表
    private void onSolve() {
        List<int[]> path = solver.solve();
        view.setSolutionPath(path);
        view.draw();

        // DFS 回溯枚举：上限取自输入框，收录到上限即停止，避免大迷宫搜索过久
        int limit = currentEnumLimit();
        allPaths = solver.findAllPaths(limit);

        // 枚举结果写入 tmp/：头部带尺寸 / 起终点 / 上限，便于日后对照
        File outFile = new File(PATH_OUTPUT_FILE);
        FileDialogs.ensureParentDir(outFile);
        boolean written = solver.savePathsToFile(allPaths, outFile);
        panel.setFileHint(written ? "枚举结果已写入 " + PATH_OUTPUT_FILE
                : "写入 " + PATH_OUTPUT_FILE + " 失败", !written);

        pathPanel.setPaths(allPaths);
        refreshEnumHint();

        // 枚举可能上千条，画布只叠画抽样；开关关闭时只回显状态，不描线
        if (pathPanel.isOverlayEnabled()) {
            applySampleOverlay();
        } else {
            refreshSampleHint();
        }
    }
    // @anchor: app_solve_action_end

    // @anchor: app_reset_action
    // 重置：只清除当前显示的解法与列表，迷宫本身保持不变
    private void onReset() {
        view.setSolutionPath(null);
        view.draw();
        clearPathList();
    }
    // @anchor: app_reset_action_end

    // @anchor: app_clear_action
    // 清空：去掉全部内部墙壁与得分点，等于回到"空旷画布"
    private void onClear() {
        model.clearInternalWalls();
        model.clearScorePoints();
        view.setSolutionPath(null);
        view.draw();
        clearPathList();
    }
    // @anchor: app_clear_action_end

    // @anchor: app_edit_mode_apply
    // 模式切换总入口：点已选中的开关视为退出；按钮选中态、视图编辑模式、生成/求解可用性集中同步
    /** 点击某个编辑开关：同一模式再点一次即退出编辑 */
    private void toggleEditMode(EditMode mode) {
        applyEditMode(editMode == mode ? EditMode.NONE : mode);
    }

    /** 应用编辑模式（换尺寸 / 载入盘面时传 NONE 复位） */
    private void applyEditMode(EditMode mode) {
        editMode = mode;

        panel.getEditWallButton().setSelected(mode == EditMode.WALL);
        panel.getEditScoreButton().setSelected(mode == EditMode.SCORE);
        panel.getEditStartButton().setSelected(mode == EditMode.START);
        panel.getEditEndButton().setSelected(mode == EditMode.END);

        view.setWallEditMode(mode == EditMode.WALL);
        view.setScoreEditMode(mode == EditMode.SCORE);
        view.setEndpointEditMode(mode == EditMode.START ? MazeView.ENDPOINT_START
                : mode == EditMode.END ? MazeView.ENDPOINT_END : MazeView.ENDPOINT_NONE);

        boolean editing = mode != EditMode.NONE;
        if (editing) {
            view.setSolutionPath(null);
            view.draw();
        }
        panel.getGenerateButton().setDisable(editing);
        panel.getSolveButton().setDisable(editing);
        // 提示语跟随模式：拾取起/终点时说明"点画布哪一格"
        refreshEndpointHint();
    }
    // @anchor: app_edit_mode_apply_end

    // @anchor: app_mount_canvas
    // 画布挂载：把（可能已重建的）画布放进中间容器；容器尺寸不变，因此无需重建 ScrollPane
    /** 将当前画布挂到中间容器，并把滚动位置复位到左上角 */
    private void mountCanvas() {
        canvasHolder.getChildren().setAll(view.getCanvas());
        scrollPane.setHvalue(0);
        scrollPane.setVvalue(0);
    }
    // @anchor: app_mount_canvas_end

    // @anchor: app_endpoint_hint
    // 起终点回显：坐标唯一来源是模型，页面只负责展示；编辑模式下顺带提示"接下来该点哪里"
    /** 刷新起终点状态标签（模型变更 / 模式切换 / 尺寸变更后调用） */
    private void refreshEndpointHint() {
        if (panel == null || model == null) return;
        String pos = "起点 (" + model.getStartR() + "," + model.getStartC() + ")"
                + "  →  终点 (" + model.getEndR() + "," + model.getEndC() + ")";
        String tip;
        switch (editMode) {
            case START:
                tip = "正在拾取起点：单击画布任意格（绿色高亮）";
                break;
            case END:
                tip = "正在拾取终点：单击画布任意格（红色高亮）";
                break;
            default:
                tip = "点「设起点」/「设终点」后单击画布任意格即可调整位置";
                break;
        }
        panel.setEndpointHint(pos + "　｜　" + tip);
    }
    // @anchor: app_endpoint_hint_end

    // @anchor: app_endpoint_reset
    // 起终点复位：坐标交回模型 resetEndpoints，视图与路径列表同步作废，避免页面留着失效解法
    /** 一键把起点 / 终点还原到左上角与右下角 */
    private void resetEndpointsAndRefresh() {
        model.resetEndpoints();
        view.setSolutionPath(null);
        view.draw();
        clearPathList();
        refreshEndpointHint();
    }
    // @anchor: app_endpoint_reset_end

    // @anchor: app_board_save
    // 保存盘面：尺寸/起终点/墙壁/得分点整盘落文件；对话框默认打开 tmp/ 并给出可读的默认文件名
    /** 保存当前盘面到用户选择的文件 */
    private void saveBoardToFile() {
        File file = FileDialogs.chooseSaveBoard(stage,
                "maze_" + rows + "x" + cols + MazeIO.BOARD_EXTENSION);
        if (file == null) return; // 用户取消
        FileDialogs.ensureParentDir(file);
        try {
            MazeIO.saveBoard(model, file);
            panel.setFileHint("盘面已保存：" + file.getName(), false);
        } catch (IOException ex) {
            panel.setFileHint("保存盘面失败：" + ex.getMessage(), true);
        }
    }
    // @anchor: app_board_save_end

    // @anchor: app_board_load
    // 载入盘面：文件内容整体替换当前盘面；解析失败只在状态栏提示，不破坏现有迷宫
    /** 从用户选择的文件载入盘面 */
    private void loadBoardFromFile() {
        File file = FileDialogs.chooseOpenBoard(stage);
        if (file == null) return;

        MazeModel loaded;
        try {
            loaded = MazeIO.loadBoard(file);
        } catch (IOException ex) {
            panel.setFileHint("载入盘面失败：" + ex.getMessage(), true);
            return;
        }
        applyLoadedBoard(loaded, file.getName());
    }
    // @anchor: app_board_load_end

    // @anchor: app_apply_board
    // 盘面替换：尺寸与内容都以载入的模型为准，rows/cols 缓存、画布与核心协作者必须一起重建
    /** 用载入的模型替换当前盘面（不重新生成） */
    private void applyLoadedBoard(MazeModel loaded, String sourceName) {
        clearPathList();
        view.setSolutionPath(null);

        rows = loaded.getRows();
        cols = loaded.getCols();
        model = loaded;
        generator = new MazeGenerator(model);
        solver = new MazeSolver(model);

        view.setModel(model);
        view.rebuildCanvas(rows, cols);
        mountCanvas();
        panel.setShapeTexts(rows, cols);

        applyEditMode(EditMode.NONE); // 编辑开关、视图模式、生成/求解可用性一并复位
        view.draw();
        updateStageTitle();
        panel.setShapeHint("当前 " + rows + "×" + cols + "（载入 " + sourceName + "）", false);
        refreshEndpointHint();
        panel.setFileHint("已载入盘面：" + sourceName, false);
    }
    // @anchor: app_apply_board_end

    // @anchor: app_export_paths
    // 导出解法：把当前枚举结果另存到用户选择的文件，格式与自动输出到 tmp/ 的完全一致
    /** 导出当前枚举结果到用户选择的文件 */
    private void exportPathsToFile() {
        if (allPaths == null || allPaths.isEmpty()) {
            panel.setFileHint("当前没有可导出的枚举结果，请先点「求解」", true);
            return;
        }
        File file = FileDialogs.chooseExportPaths(stage);
        if (file == null) return;
        FileDialogs.ensureParentDir(file);
        if (solver.savePathsToFile(allPaths, file)) {
            panel.setFileHint("枚举结果已导出：" + file.getName(), false);
        } else {
            panel.setFileHint("导出失败，请检查目标路径是否可写", true);
        }
    }
    // @anchor: app_export_paths_end

    // @anchor: app_enum_limit
    // 枚举上限：输入框是唯一入口，非法输入当场红字提示并回落到默认上限，绝不静默改变搜索规模
    /** 读取解数上限输入框的值；非法时提示并返回默认上限 */
    private int currentEnumLimit() {
        Integer limit = InputParse.inRange(panel.getMaxPathsField().getText(),
                MazeSolver.MIN_ENUM_LIMIT, MazeSolver.MAX_ENUM_LIMIT);
        if (limit == null) {
            panel.setEnumHint("解数上限需为 " + MazeSolver.MIN_ENUM_LIMIT + "~"
                    + MazeSolver.MAX_ENUM_LIMIT + " 的整数，本次按默认 "
                    + MazeSolver.DEFAULT_MAX_PATHS + " 处理", true);
            return MazeSolver.DEFAULT_MAX_PATHS;
        }
        panel.getMaxPathsField().setText(String.valueOf(limit));
        return limit;
    }
    // @anchor: app_enum_limit_end

    // @anchor: app_enum_hint
    // 枚举状态回显：条数 / 上限 / 是否达上限 / 输出文件，数值全部取自求解器，页面不另存副本
    /** 刷新枚举状态行（求解后、清空后调用） */
    private void refreshEnumHint() {
        if (panel == null) return;
        if (allPaths == null) {
            panel.setEnumHint("尚未枚举（点「求解」按上限枚举全部路径）", false);
            return;
        }
        boolean truncated = solver.isLastEnumerationTruncated();
        String msg = "已枚举 " + allPaths.size() + " 条（上限 "
                + solver.getLastEnumerationLimit() + "）　→　" + PATH_OUTPUT_FILE;
        if (truncated) {
            msg += "　｜　已达上限提前停止，结果可能不完整，可调大上限后重试";
        }
        panel.setEnumHint(msg, truncated);
    }
    // @anchor: app_enum_hint_end

    // @anchor: app_sample_overlay
    // 抽样叠画：枚举结果只抽若干条交给视图叠画；条数非法只提示并回落默认值，绝不改变枚举规模
    /** 按当前叠画条数抽样叠画枚举结果（抽样取点由求解器完成，线段去重在视图侧） */
    private void applySampleOverlay() {
        if (!pathPanel.hasPaths()) {
            view.clearOverlayPaths();
            view.draw();
            pathPanel.setSampleHint("当前没有可叠画的枚举结果，请先点「求解」", true);
            return;
        }

        Integer want = InputParse.inRange(pathPanel.getSampleCountText(),
                MazeSolver.MIN_SAMPLE_COUNT, MazeSolver.MAX_SAMPLE_COUNT);
        boolean fallback = want == null;
        int count = fallback ? MazeSolver.DEFAULT_SAMPLE_COUNT : want;
        if (fallback) {
            pathPanel.setSampleCountText(count);
        }

        List<Integer> picked = MazeSolver.sampleIndices(allPaths.size(), count);
        List<List<int[]>> sampledCoords = new ArrayList<>();
        for (int idx : picked) {
            sampledCoords.add(solver.dirCodesToCoordinates(allPaths.get(idx)));
        }
        view.setOverlayPaths(sampledCoords);
        view.draw();

        if (fallback) {
            pathPanel.setSampleHint("叠画条数需为 " + MazeSolver.MIN_SAMPLE_COUNT + "~"
                    + MazeSolver.MAX_SAMPLE_COUNT + " 的整数，已按默认 " + count + " 条叠画", true);
        } else {
            pathPanel.setSampleHint("已叠画抽样 " + view.getOverlayPathCount() + " / " + allPaths.size() + " 条路径", false);
        }
    }

    /** 关掉叠画开关：立即撤线并重绘 */
    private void clearOverlayAndHint() {
        view.clearOverlayPaths();
        view.draw();
        refreshSampleHint();
    }

    /** 抽样状态回显：枚举条数与已叠画条数都取自后端，页面不另存副本 */
    private void refreshSampleHint() {
        if (pathPanel == null) return;
        if (!pathPanel.hasPaths()) {
            pathPanel.setSampleHint("尚未枚举，求解后可叠画抽样路径", false);
            return;
        }
        if (pathPanel.isOverlayEnabled() && view.getOverlayPathCount() > 0) {
            pathPanel.setSampleHint("已叠画抽样 " + view.getOverlayPathCount() + " / " + allPaths.size() + " 条路径", false);
        } else {
            pathPanel.setSampleHint("枚举 " + allPaths.size() + " 条，抽样叠画已关闭", false);
        }
    }


    // @anchor: app_sample_overlay_end

    // @anchor: app_path_select
    // 列表选中：把某条枚举路径坐标化后作为主解法显示；真实下标由面板负责映射
    /** 在画布上显示指定路径（传入 allPaths 的真实下标） */
    private void showPathOnCanvas(int index) {
        if (allPaths == null || index < 0 || index >= allPaths.size()) return;
        view.setSolutionPath(solver.dirCodesToCoordinates(allPaths.get(index)));
        view.draw();
    }
    // @anchor: app_path_select_end

    // @anchor: app_shape_apply
    // 应用新行列数：按模型常量校验 → 复位编辑态与列表 → 重建模型/求解器/画布 → 重新生成；非法输入只提示不回滚
    /** 应用新的行列数，重建迷宫（仅重建模型与画布，不重建整个 UI） */
    private void applyShapeChange() {
        Integer newRows = InputParse.inRange(panel.getRowsField().getText(), MazeModel.MIN_DIM, MazeModel.MAX_DIM);
        Integer newCols = InputParse.inRange(panel.getColsField().getText(), MazeModel.MIN_DIM, MazeModel.MAX_DIM);
        if (newRows == null || newCols == null) {
            panel.setShapeHint("尺寸需为 " + MazeModel.MIN_DIM + "~" + MazeModel.MAX_DIM + " 的整数", true);
            return;
        }
        rows = newRows;
        cols = newCols;

        // 清空旧状态
        clearPathList();
        view.setSolutionPath(null);

        // 复位编辑模式：新模型的起终点回到角上，编辑态与按钮选中态必须一起归零
        applyEditMode(EditMode.NONE);

        // 重建模型、生成器、求解器（三者与尺寸强绑定）
        model = new MazeModel(rows, cols);
        generator = new MazeGenerator(model);
        solver = new MazeSolver(model);

        // 视图换模型 → 重算单元格尺寸并重建画布 → 重新挂载
        view.setModel(model);
        view.rebuildCanvas(rows, cols);
        mountCanvas();

        // 输入框回写规范化值并重新生成迷宫
        panel.setShapeTexts(rows, cols);
        generator.generate();
        view.draw();
        updateStageTitle();
        panel.setShapeHint("当前 " + rows + "×" + cols, false);
        // 新模型自带默认起终点（左上角 / 右下角），页面文字同步刷新
        refreshEndpointHint();
    }
    // @anchor: app_shape_apply_end

    // @anchor: app_update_title
    // 标题即时反映当前尺寸，便于确认尺寸应用是否生效
    /** 更新窗口标题 */
    private void updateStageTitle() {
        stage.setTitle("迷宫求解器 - " + rows + "×" + cols);
    }
    // @anchor: app_update_title_end

    // @anchor: app_clear_list
    // 列表清空等同"源数据作废"：allPaths、列表内容与画布上的抽样叠画一并作废，枚举/抽样状态行同时刷新
    /** 清空路径列表（同时撤掉画布上的抽样叠画并重绘） */
    private void clearPathList() {
        allPaths = null;
        if (pathPanel != null) pathPanel.clear();
        // 叠画是枚举结果的派生物，源数据作废后必须立刻从画布撤掉（否则残留旧解法的线条）
        if (view != null) {
            view.clearOverlayPaths();
            view.draw();
        }
        refreshEnumHint();
        refreshSampleHint();
    }
    // @anchor: app_clear_list_end

    // @anchor: app_main
    // 标准 JavaFX 启动入口
    public static void main(String[] args) {
        launch(args);
    }
    // @anchor: app_main_end
}
