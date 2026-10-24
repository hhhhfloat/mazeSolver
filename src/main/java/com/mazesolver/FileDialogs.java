// @anchor: file_dialogs_intro
// 文件选择入口：盘面保存 / 盘面载入 / 枚举结果导出三类对话框，统一默认目录 tmp/ 与扩展名过滤。
package com.mazesolver;

import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;

/**
 * 文件对话框工厂。主界面只关心"选到哪个文件"，默认目录、过滤器、默认文件名等界面细节集中在此。
 * 用户取消时统一返回 null，调用方据此不再区分"取消"与"未选择"。
 */
public final class FileDialogs {

    private FileDialogs() {
    }

    // @anchor: dialog_dir
    // 对话框默认目录：与枚举结果自动输出位置保持一致，产物统一落在 tmp/ 下
    /** 文件对话框的默认目录（相对运行目录） */
    public static final String DEFAULT_DIR = "tmp";

    /** 默认目录存在时作为初始目录（不存在就不设，避免 FileChooser 抛异常） */
    private static File initialDir() {
        File dir = new File(DEFAULT_DIR);
        return dir.isDirectory() ? dir : null;
    }
    // @anchor: dialog_dir_end

    // @anchor: dialog_board
    // 盘面存取的对话框：只允许 MazeIO.BOARD_EXTENSION 一种扩展名，避免存成无法再载入的文件
    /** 选择盘面保存位置；取消返回 null */
    public static File chooseSaveBoard(Stage stage, String defaultName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("保存盘面");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("迷宫盘面 (*" + MazeIO.BOARD_EXTENSION + ")",
                        "*" + MazeIO.BOARD_EXTENSION));
        File dir = initialDir();
        if (dir != null) chooser.setInitialDirectory(dir);
        chooser.setInitialFileName(defaultName);
        return chooser.showSaveDialog(stage);
    }

    /** 选择要载入的盘面文件；取消返回 null */
    public static File chooseOpenBoard(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("载入盘面");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("迷宫盘面 (*" + MazeIO.BOARD_EXTENSION + ")",
                        "*" + MazeIO.BOARD_EXTENSION));
        File dir = initialDir();
        if (dir != null) chooser.setInitialDirectory(dir);
        return chooser.showOpenDialog(stage);
    }
    // @anchor: dialog_board_end

    // @anchor: dialog_paths
    // 枚举结果导出的对话框：与自动输出的 tmp/all_paths.txt 同格式，只是换成用户指定位置
    /** 选择枚举结果的导出位置；取消返回 null */
    public static File chooseExportPaths(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("导出枚举结果");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("文本文件 (*.txt)", "*.txt"));
        File dir = initialDir();
        if (dir != null) chooser.setInitialDirectory(dir);
        chooser.setInitialFileName("all_paths.txt");
        return chooser.showSaveDialog(stage);
    }
    // @anchor: dialog_paths_end

    // @anchor: dialog_util
    // 写文件前的兜底：目标父目录不存在就创建，避免"路径不可写"掩盖真正原因
    /** 确保文件所在目录存在 */
    public static void ensureParentDir(File file) {
        File dir = file.getParentFile();
        if (dir != null && !dir.exists()) dir.mkdirs();
    }
    // @anchor: dialog_util_end
}
