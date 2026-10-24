// @anchor: maze_editor_intro
// 编辑执行体：把"加/拆墙、落/清标记数字、拾取起终点"落到模型上，锁定长按方向、同一目标只处理一次。
package com.mazesolver;

/**
 * 迷宫编辑的执行核心。视图只负责把命中结果喂进来，这里负责：
 * 按下瞬间锁定操作方向（加墙/拆墙、落子成哪个数字）、拖拽时沿锁定方向连续生效、
 * 同一目标只处理一次，改动成功后通知外部作废解法并重绘。
 */
public final class MazeEditor {

    // @anchor: editor_pick_mode
    // 起终点拾取模式：视图、主界面与渲染器共用这三个常量，避免各处写字面量
    /** 不拾取起终点 */
    public static final int PICK_NONE = 0;
    /** 下次点击的格子设为起点 */
    public static final int PICK_START = 1;
    /** 下次点击的格子设为终点 */
    public static final int PICK_END = 2;
    // @anchor: editor_pick_mode_end

    // @anchor: editor_state
    // 编辑状态：模型引用、改动后的重绘通知，以及"本次按下锁定的目标"与"上一个已处理目标"
    private MazeModel model;
    /** 改动生效后作废解法并重绘，由视图注入 */
    private final Runnable onChanged;

    /** 本次按下锁定的墙壁动作：true=加墙, false=拆墙, null=未锁定 */
    private Boolean wallEditAdding = null;
    /** 本次按下锁定的得分点落子目标（-1 = 未锁定；MazeModel.SCORE_NONE 表示清除） */
    private int scoreEditTargetDigit = -1;

    /** 上一个已处理的墙壁段与单元格，避免拖拽在同一目标上重复执行 */
    private int lastWallRow = -1, lastWallCol = -1, lastWallDir = -1;
    private int lastCellRow = -1, lastCellCol = -1;

    public MazeEditor(MazeModel model, Runnable onChanged) {
        this.model = model;
        this.onChanged = onChanged;
    }

    /** 换模型（换尺寸 / 载入盘面时调用）并清掉长按锁定 */
    public void setModel(MazeModel newModel) {
        this.model = newModel;
        reset();
    }


    // @anchor: editor_state_end

    // @anchor: editor_wall
    // 墙壁编辑：按下时按"有墙则拆、无墙则加"锁定方向，拖拽期间沿该方向连续生效
    /** 按下命中墙壁：锁定本次动作方向并立即执行一次；未命中传 null 只清锁定 */
    public void beginWall(MazeHitTester.WallHit hit) {
        if (hit == null) {
            wallEditAdding = null;
            return;
        }
        wallEditAdding = !model.hasWall(hit.row(), hit.col(), hit.dir());
        applyWallAction(hit.row(), hit.col(), hit.dir(), wallEditAdding);
    }

    /** 拖拽到新的墙壁段：沿用按下时锁定的方向 */
    public void dragWall(MazeHitTester.WallHit hit) {
        if (wallEditAdding == null || hit == null) return;
        applyWallAction(hit.row(), hit.col(), hit.dir(), wallEditAdding);
    }

    /** 执行墙壁操作：内部墙双向同步、外边界只改本格一侧；已处于目标状态或同一目标已处理则跳过 */
    public void applyWallAction(int r, int c, int dir, boolean adding) {
        if (r == lastWallRow && c == lastWallCol && dir == lastWallDir) return;
        if (adding == model.hasWall(r, c, dir)) return;

        model.setWallSide(r, c, dir, adding);
        lastWallRow = r;
        lastWallCol = c;
        lastWallDir = dir;
        notifyChanged();
    }
    // @anchor: editor_wall_end

    // @anchor: editor_score
    // 得分点编辑：按下时锁定"落子成哪个数字"（已是该数字则锁定为清除），拖拽期间保持同一目标
    /** 按下命中单元格：锁定落子目标并立即执行一次；起终点所在格由模型拒绝 */
    public void beginScore(int r, int c, int editorDigit) {
        if (model.isStart(r, c) || model.isEnd(r, c)) {
            scoreEditTargetDigit = -1;
            return;
        }
        scoreEditTargetDigit = targetDigitFor(r, c, editorDigit);
        applyScoreAction(r, c, scoreEditTargetDigit);
    }

    /** 拖拽到新的单元格：沿用按下时锁定的落子目标 */
    public void dragScore(int r, int c) {
        if (scoreEditTargetDigit < 0) return;
        if (model.isStart(r, c) || model.isEnd(r, c)) return;
        applyScoreAction(r, c, scoreEditTargetDigit);
    }

    /** 执行得分点操作：targetDigit 为落子目标数字，MazeModel.SCORE_NONE 表示清除该格标记 */
    public void applyScoreAction(int r, int c, int targetDigit) {
        if (r == lastCellRow && c == lastCellCol) return;
        if (model.getScoreDigit(r, c) == targetDigit) return;
        if (!model.applyScoreDigit(r, c, targetDigit)) return;

        lastCellRow = r;
        lastCellCol = c;
        notifyChanged();
    }

    /** 按下瞬间决定该格的落子目标：已是当前标记数字则清除，否则改为当前标记数字 */
    public int targetDigitFor(int r, int c, int editorDigit) {
        return model.getScoreDigit(r, c) == editorDigit ? MazeModel.SCORE_NONE : editorDigit;
    }
    // @anchor: editor_score_end

    // @anchor: editor_endpoint
    // 起终点拾取：单击即定，越界或与另一端点重合（模型返回 false）时忽略
    /** 执行起终点拾取；返回是否真的改动 */
    public boolean applyEndpointAction(int pickMode, int r, int c) {
        if (pickMode != PICK_START && pickMode != PICK_END) return false;

        boolean changed = (pickMode == PICK_START) ? model.setStart(r, c) : model.setEnd(r, c);
        if (!changed) return false;

        notifyChanged();
        return true;
    }
    // @anchor: editor_endpoint_end

    // @anchor: editor_reset
    // 复位：松开鼠标、移出画布、切换编辑模式时清空"本次按下锁定"与"上一个已处理目标"
    public void reset() {
        wallEditAdding = null;
        scoreEditTargetDigit = -1;
        lastWallRow = -1;
        lastWallCol = -1;
        lastWallDir = -1;
        lastCellRow = -1;
        lastCellCol = -1;
    }
    // @anchor: editor_reset_end

    // @anchor: editor_notify
    // 改动生效后的统一出口：作废既有解法、通知主界面清理失效结果并重绘（回调由视图注入）
    private void notifyChanged() {
        if (onChanged != null) onChanged.run();
    }
    // @anchor: editor_notify_end
}
