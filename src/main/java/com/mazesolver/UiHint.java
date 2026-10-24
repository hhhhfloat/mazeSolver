// @anchor: ui_hint_intro
// 提示标签的统一着色：成功/普通/错误三级文字色集中在此，避免每个面板各写一套内联样式。
package com.mazesolver;

import javafx.scene.control.Label;

/**
 * 状态提示标签的着色工具。界面上的"尺寸提示 / 枚举状态 / 文件操作 / 叠画 / 列表统计"共用同一套配色，
 * 只改文案与级别，样式不再散落在各处。
 */
public final class UiHint {

    // @anchor: ui_hint_colors
    // 三级配色：成功绿、普通灰、错误红；界面上所有提示标签都取这几种，不再各写内联色值
    /** 成功提示色 */
    public static final String OK = "#2e7d32";
    /** 普通说明色 */
    public static final String NORMAL = "#666";
    /** 强调说明色（略深于 NORMAL，用于列表统计行） */
    public static final String SUB = "#555";
    /** 错误提示色 */
    public static final String ERROR = "#c62828";
    // @anchor: ui_hint_colors_end

    // @anchor: ui_hint_show
    // 写入文案并同步配色：error=true 一律红字，否则用调用方指定的普通色
    /** 显示提示（error=true 用红字，否则用 okColor） */
    public static void show(Label label, String msg, boolean error, String okColor) {
        if (label == null) return;
        label.setText(msg);
        label.setStyle(styleOf(error, okColor));
    }

    /** 提示标签的完整样式串（面板自建标签时用它设初始样式） */
    public static String styleOf(boolean error, String okColor) {
        return "-fx-font-size: 11px; -fx-text-fill: " + (error ? ERROR : okColor) + ";";
    }


    // @anchor: ui_hint_show_end

    private UiHint() {
    }
}
