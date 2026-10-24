// @anchor: path_list_panel_intro
// 右侧路径列表面板：过滤条件（步数/分数/序列表达式）、排序方式、叠画设置、列表与统计行，自包含全部列表数据流。
package com.mazesolver;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * 右侧"所有解法"面板。职责：把枚举结果按过滤条件与排序方式渲染成列表，并回显统计信息。
 * 面板不持有求解器本身（求解器会随尺寸变化重建），而是通过 {@link Supplier} 取当前实例；
 * 选中项、叠画开关、叠画条数变化通过回调交给主界面处理（那里才有视图）。
 */
public final class PathListPanel extends VBox {

    // @anchor: list_sort_mode
    // 排序方式文案（同时就是下拉框的取值）：只改展示顺序，源数据顺序与"路径 N"编号都不动
    /** 默认顺序（按枚举顺序 = 真实下标升序） */
    public static final String SORT_DEFAULT = "默认顺序";
    /** 按步数升序 */
    public static final String SORT_STEPS_ASC = "步数升序";
    /** 按步数降序 */
    public static final String SORT_STEPS_DESC = "步数降序";
    // @anchor: list_sort_mode_end

    // @anchor: list_state
    // 面板状态：源数据引用、过滤索引映射、控件引用与三个对外回调
    /** 取当前求解器（尺寸变化后主界面会换新实例，因此按需取而不是构造时固定） */
    private final Supplier<MazeSolver> solverSupplier;

    /** 枚举结果源数据（方向编码），null 表示尚未枚举 */
    private List<List<Integer>> allPaths;
    /** 过滤后的真实下标（回指 allPaths）；null 表示未过滤 */
    private List<Integer> filteredPathIndices;

    private final ObservableList<String> pathListItems = FXCollections.observableArrayList();
    private ListView<String> pathListView;
    private TextField tfStepMin;
    private TextField tfStepMax;
    private TextField tfScoreMin;
    private TextField tfScoreMax;
    private TextField tfDigitFilter;
    private ComboBox<String> cbSortMode;
    private TextField tfSampleCount;
    private ToggleButton btnOverlaySample;
    private Label lblSampleHint;
    private Label lblScoreStats;

    /** 选中某条路径（传入的是 allPaths 的真实下标，已做过滤映射） */
    private IntConsumer onPathSelected = idx -> { };
    /** 叠画开关变化 */
    private Consumer<Boolean> onOverlayToggle = on -> { };
    /** 叠画条数变化（输入框回车） */
    private Runnable onOverlayCountChanged = () -> { };
    // @anchor: list_state_end

    // @anchor: list_constructor
    // 构造即完成布局：标题 / 过滤区 / 排序 / 叠画设置 / 列表 / 统计行，列表高度随窗口伸缩
    public PathListPanel(Supplier<MazeSolver> solverSupplier) {
        super(6);
        this.solverSupplier = solverSupplier;
        setPadding(new Insets(12));
        setStyle("-fx-background-color: #f8f8f8; -fx-border-color: #ddd; -fx-border-width: 0 0 0 1;");
        buildContent();
    }

    private void buildContent() {
        Label lblPaths = new Label("所有解法");
        lblPaths.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        lblScoreStats = new Label("");
        lblScoreStats.setStyle(UiHint.styleOf(false, UiHint.SUB));
        lblScoreStats.setWrapText(true);

        VBox sampleBox = buildSampleBox();
        createListView();

        getChildren().addAll(lblPaths, buildFilterBox(), buildSortBox(), buildFilterButtons(),
                sampleBox, pathListView, lblScoreStats);
        VBox.setVgrow(pathListView, Priority.ALWAYS);
    }


    // @anchor: list_constructor_end

    // @anchor: list_filter_ui
    // 过滤条件输入区：步数范围 + 分数范围 + 序列条件，全部允许留空；表达式语法提示紧随其后
    private VBox buildFilterBox() {
        Label lblStepFilter = new Label("步数范围:");
        lblStepFilter.setStyle("-fx-font-size: 11px;");
        tfStepMin = new TextField();
        tfStepMin.setPrefWidth(48);
        tfStepMin.setPromptText("最小");
        tfStepMax = new TextField();
        tfStepMax.setPrefWidth(48);
        tfStepMax.setPromptText("最大");
        HBox stepFilterBox = new HBox(3, lblStepFilter, tfStepMin, new Label("-"), tfStepMax);
        stepFilterBox.setAlignment(Pos.CENTER_LEFT);

        Label lblScoreFilter = new Label("分数范围:");
        lblScoreFilter.setStyle("-fx-font-size: 11px;");
        tfScoreMin = new TextField();
        tfScoreMin.setPrefWidth(48);
        tfScoreMin.setPromptText("最小");
        tfScoreMax = new TextField();
        tfScoreMax.setPrefWidth(48);
        tfScoreMax.setPromptText("最大");
        HBox scoreFilterBox = new HBox(3, lblScoreFilter, tfScoreMin, new Label("-"), tfScoreMax);
        scoreFilterBox.setAlignment(Pos.CENTER_LEFT);

        // 数字串条件：按"经过得分点的标记数字序列"筛选，语法由 PathSequenceFilter 定义
        Label lblDigitFilter = new Label("序列条件:");
        lblDigitFilter.setStyle("-fx-font-size: 11px;");
        tfDigitFilter = new TextField();
        tfDigitFilter.setPrefWidth(180);
        tfDigitFilter.setPromptText("3>7 & \"37\"");
        HBox digitFilterBox = new HBox(4, lblDigitFilter, tfDigitFilter);
        digitFilterBox.setAlignment(Pos.CENTER_LEFT);

        Label lblDigitHint = new Label(PathSequenceFilter.SYNTAX_HINT);
        lblDigitHint.setStyle("-fx-font-size: 10px; -fx-text-fill: #888;");
        lblDigitHint.setWrapText(true);

        // 数字串条件支持回车直接搜索，省得每次都去点按钮
        tfDigitFilter.setOnAction(e -> applyFilterAndPopulate());

        VBox box = new VBox(6, stepFilterBox, scoreFilterBox, digitFilterBox, lblDigitHint);
        return box;
    }

    /** 排序区：筛选决定"留哪些"，排序决定"按什么顺序看" */
    private HBox buildSortBox() {
        Label lblSort = new Label("排序:");
        lblSort.setStyle("-fx-font-size: 11px;");
        cbSortMode = new ComboBox<>(FXCollections.observableArrayList(
                SORT_DEFAULT, SORT_STEPS_ASC, SORT_STEPS_DESC));
        cbSortMode.getSelectionModel().selectFirst();
        cbSortMode.setPrefWidth(96);
        cbSortMode.setStyle("-fx-font-size: 11px;");
        cbSortMode.setOnAction(e -> {
            if (allPaths == null || allPaths.isEmpty()) return;
            if (filteredPathIndices == null) {
                populatePathList();
            } else {
                applyFilterAndPopulate();
            }
        });
        HBox box = new HBox(4, lblSort, cbSortMode);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    /** 搜索 / 清除：两个按钮共用同一套"过滤 + 重建列表"流程 */
    private HBox buildFilterButtons() {
        Button btnSearch = new Button("搜索");
        btnSearch.setPrefWidth(70);
        btnSearch.setStyle("-fx-font-size: 11px;");
        btnSearch.setOnAction(e -> applyFilterAndPopulate());

        Button btnClearFilter = new Button("清除");
        btnClearFilter.setPrefWidth(70);
        btnClearFilter.setStyle("-fx-font-size: 11px;");
        btnClearFilter.setOnAction(e -> {
            tfStepMin.clear();
            tfStepMax.clear();
            tfScoreMin.clear();
            tfScoreMax.clear();
            tfDigitFilter.clear();
            applyFilterAndPopulate();
        });

        HBox box = new HBox(4, btnSearch, btnClearFilter);
        box.setAlignment(Pos.CENTER);
        return box;
    }
    // @anchor: list_filter_ui_end

    // @anchor: list_sample_ui
    // 叠画设置区：条数输入 + 开关 + 状态行；面板只报告"用户想要什么"，实际叠画由主界面驱动视图
    private VBox buildSampleBox() {
        Label lblSampleCount = new Label("叠画条数:");
        lblSampleCount.setStyle("-fx-font-size: 11px;");
        tfSampleCount = new TextField(String.valueOf(MazeSolver.DEFAULT_SAMPLE_COUNT));
        tfSampleCount.setPrefWidth(52);
        btnOverlaySample = new ToggleButton("叠画抽样");
        btnOverlaySample.setSelected(true);
        btnOverlaySample.setStyle("-fx-font-size: 11px;");
        HBox sampleBox = new HBox(4, lblSampleCount, tfSampleCount, btnOverlaySample);
        sampleBox.setAlignment(Pos.CENTER_LEFT);

        lblSampleHint = new Label();
        lblSampleHint.setStyle(UiHint.styleOf(false, UiHint.NORMAL));
        lblSampleHint.setWrapText(true);

        btnOverlaySample.setOnAction(e -> onOverlayToggle.accept(btnOverlaySample.isSelected()));
        tfSampleCount.setOnAction(e -> {
            btnOverlaySample.setSelected(true);
            onOverlayCountChanged.run();
        });

        return new VBox(4, sampleBox, lblSampleHint);
    }
    // @anchor: list_sample_ui_end

    // @anchor: list_view
    // 列表本体与统计行：选中项先经 filter 映射回真实下标再回调，展示行与映射表永远同序
    private ListView<String> createListView() {
        pathListView = new ListView<>(pathListItems);
        pathListView.setPrefWidth(170);
        pathListView.setMinHeight(160);
        pathListView.setStyle("-fx-font-size: 12px;");
        pathListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || allPaths == null) return;
            int shown = pathListView.getSelectionModel().getSelectedIndex();
            int actual = (filteredPathIndices != null && shown >= 0 && shown < filteredPathIndices.size())
                    ? filteredPathIndices.get(shown) : shown;
            if (actual >= 0 && actual < allPaths.size()) onPathSelected.accept(actual);
        });
        return pathListView;
    }

    /** 统计/错误行文字：error=true 用红字（序列条件非法等） */
    private void setScoreStats(String msg, boolean error) {
        UiHint.show(lblScoreStats, msg, error, UiHint.SUB);
    }
    // @anchor: list_view_end

    // @anchor: list_api
    // 对外接口：设置源数据 / 重填 / 清空 / 取叠画设置 / 注册回调——主界面不碰面板内部控件
    public void setOnPathSelected(IntConsumer callback) {
        this.onPathSelected = callback == null ? idx -> { } : callback;
    }

    public void setOnOverlayToggle(Consumer<Boolean> callback) {
        this.onOverlayToggle = callback == null ? on -> { } : callback;
    }

    public void setOnOverlayCountChanged(Runnable callback) {
        this.onOverlayCountChanged = callback == null ? () -> { } : callback;
    }

    /** 是否已枚举出结果 */
    public boolean hasPaths() {
        return allPaths != null && !allPaths.isEmpty();
    }

    /** 设置枚举结果并整表填充（不套过滤条件，按当前排序方式） */
    public void setPaths(List<List<Integer>> paths) {
        this.allPaths = paths;
        populatePathList();
    }

    /** 清空列表、统计与源数据引用（源数据作废时调用） */
    public void clear() {
        allPaths = null;
        filteredPathIndices = null;
        pathListItems.clear();
        UiHint.show(lblScoreStats, "", false, UiHint.SUB);
    }

    /** 叠画开关是否打开 */
    public boolean isOverlayEnabled() {
        return btnOverlaySample.isSelected();
    }

    /** 叠画条数输入框原文（由主界面解析并给出提示） */
    public String getSampleCountText() {
        return tfSampleCount.getText();
    }

    /** 回写叠画条数（非法输入回落默认值时同步界面） */
    public void setSampleCountText(int count) {
        tfSampleCount.setText(String.valueOf(count));
    }

    /** 叠画状态行 */
    public void setSampleHint(String msg, boolean error) {
        UiHint.show(lblSampleHint, msg, error, UiHint.NORMAL);
    }
    // @anchor: list_api_end

    // @anchor: list_populate_all
    // 全量填充：收集行（步数/分数/数字串一次算好）→ 按当前排序方式重排 → 一次填表
    /** 填充路径列表（不套过滤条件，按当前排序方式排序） */
    void populatePathList() {
        pathListItems.clear();
        filteredPathIndices = new ArrayList<>();
        if (allPaths == null || allPaths.isEmpty()) {
            pathListItems.add("（无路径）");
            setScoreStats("", false);
            return;
        }

        MazeSolver solver = solverSupplier.get();
        List<PathRow> rows = new ArrayList<>(allPaths.size());
        for (int i = 0; i < allPaths.size(); i++) {
            List<Integer> path = allPaths.get(i);
            MazeSolver.PathStats stats = solver.analyzePath(path);
            rows.add(new PathRow(i, path.size(), stats.getScore(), stats.getDigits()));
        }
        sortRows(rows);

        int maxScore = 0;
        int minScore = Integer.MAX_VALUE;
        for (PathRow row : rows) {
            filteredPathIndices.add(row.index);
            if (row.score > maxScore) maxScore = row.score;
            if (row.score < minScore) minScore = row.score;
            pathListItems.add(pathRowLabel(row));
        }
        setScoreStats("共 " + rows.size() + " 条路径 | 最高 " + maxScore + " 分 | 最低 " + minScore + " 分", false);
    }
    // @anchor: list_populate_all_end

    // @anchor: list_populate_filtered
    // 过滤填充：先编译序列条件（写错当场提示、不动列表），再按步数/分数/数字串筛选并排序
    /** 应用过滤条件（并按当前排序方式重排）后填充路径列表 */
    void applyFilterAndPopulate() {
        pathListItems.clear();
        filteredPathIndices = new ArrayList<>();

        if (allPaths == null || allPaths.isEmpty()) {
            pathListItems.add("（无路径）");
            setScoreStats("", false);
            return;
        }

        // 序列条件先编译：表达式写错就当场提示，不拿"半懂不懂"的条件去筛
        PathSequenceFilter digitFilter;
        try {
            digitFilter = PathSequenceFilter.parse(tfDigitFilter == null ? "" : tfDigitFilter.getText());
        } catch (IllegalArgumentException ex) {
            filteredPathIndices = null;
            pathListItems.add("（序列条件有误）");
            setScoreStats("序列条件有误：" + ex.getMessage(), true);
            return;
        }

        int stepMin = InputParse.lowerBound(tfStepMin.getText());
        int stepMax = InputParse.upperBound(tfStepMax.getText());
        int scoreMin = InputParse.lowerBound(tfScoreMin.getText());
        int scoreMax = InputParse.upperBound(tfScoreMax.getText());
        boolean constrained = stepMin > 0 || stepMax < Integer.MAX_VALUE
                || scoreMin > 0 || scoreMax < Integer.MAX_VALUE
                || !digitFilter.isEmpty();

        // 先收集再排序：排序只改展示顺序，真实下标跟着一起搬，选中项经 filteredPathIndices 仍能定位
        MazeSolver solver = solverSupplier.get();
        List<PathRow> rows = new ArrayList<>();
        for (int i = 0; i < allPaths.size(); i++) {
            List<Integer> path = allPaths.get(i);
            int steps = path.size();

            if (stepMin > 0 && steps < stepMin) continue;
            if (stepMax < Integer.MAX_VALUE && steps > stepMax) continue;

            // 分数与数字串来自同一次遍历（analyzePath），两者口径永远一致
            MazeSolver.PathStats stats = solver.analyzePath(path);
            if (scoreMin > 0 && stats.getScore() < scoreMin) continue;
            if (scoreMax < Integer.MAX_VALUE && stats.getScore() > scoreMax) continue;
            if (!digitFilter.matches(stats.getDigits())) continue;

            rows.add(new PathRow(i, steps, stats.getScore(), stats.getDigits()));
        }
        sortRows(rows);

        int maxScore = 0;
        int minScore = Integer.MAX_VALUE;
        for (PathRow row : rows) {
            filteredPathIndices.add(row.index);
            if (row.score > maxScore) maxScore = row.score;
            if (row.score < minScore) minScore = row.score;
            pathListItems.add(pathRowLabel(row));
        }

        if (rows.isEmpty()) {
            pathListItems.add("（无匹配路径）");
            setScoreStats("无匹配结果", false);
        } else if (constrained) {
            setScoreStats("筛选 " + rows.size() + " / " + allPaths.size()
                    + " 条 | 最高 " + maxScore + " 分 | 最低 " + minScore + " 分"
                    + digitFilterSuffix(digitFilter), false);
        } else {
            setScoreStats("共 " + rows.size() + " 条路径 | 最高 " + maxScore + " 分 | 最低 " + minScore + " 分", false);
        }
    }
    // @anchor: list_populate_filtered_end

    // @anchor: list_row
    // 行构造与排序：一行 = 真实下标 + 步数 + 分数 + 数字串；同长度按真实下标升序，保证重排结果稳定
    /** 列表行的中间形态：真实下标让选中项能回指 allPaths，"路径 N" 的编号也取自它 */
    private static final class PathRow {
        private final int index;
        private final int steps;
        private final int score;
        private final String digits;

        PathRow(int index, int steps, int score, String digits) {
            this.index = index;
            this.steps = steps;
            this.score = score;
            this.digits = digits;
        }
    }

    /** 当前排序方式（控件尚未创建时按默认处理） */
    private String currentSortMode() {
        String mode = cbSortMode == null ? null : cbSortMode.getValue();
        return mode == null ? SORT_DEFAULT : mode;
    }

    /** 按当前排序方式重排候选行；同长度按真实下标升序 */
    private void sortRows(List<PathRow> rows) {
        String mode = currentSortMode();
        if (SORT_STEPS_ASC.equals(mode)) {
            rows.sort((a, b) -> a.steps != b.steps
                    ? Integer.compare(a.steps, b.steps) : Integer.compare(a.index, b.index));
        } else if (SORT_STEPS_DESC.equals(mode)) {
            rows.sort((a, b) -> a.steps != b.steps
                    ? Integer.compare(b.steps, a.steps) : Integer.compare(a.index, b.index));
        }
    }

    /** 列表行文案：编号用真实下标（与路径文件 Path N 对齐），后接步数、分数与数字串 */
    private String pathRowLabel(PathRow row) {
        StringBuilder sb = new StringBuilder();
        sb.append("路径 ").append(row.index + 1)
                .append("  (").append(row.steps).append("步, ").append(row.score).append("分)");
        if (!row.digits.isEmpty()) sb.append("  序列 ").append(row.digits);
        return sb.toString();
    }

    /** 统计行附带的"按哪些序列条件筛的"，便于确认表达式被正确理解 */
    private String digitFilterSuffix(PathSequenceFilter filter) {
        if (filter == null || filter.isEmpty()) return "";
        return " | 序列条件: " + String.join(" 且 ", filter.describe());
    }
    // @anchor: list_row_end
}
