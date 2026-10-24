// @anchor: io_intro
// 盘面文本文件的读写：格式只在本类定义，模型 / 求解器 / 界面都不直接拼文件内容。
package com.mazesolver;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 迷宫盘面的持久化。
 * 盘面文件是纯文本：尺寸、起终点、每格墙壁的 4 位掩码（一位十六进制）与每格得分点（# / .），
 * 因此可以人工查看与编辑，也能被载入还原成等价的 MazeModel。
 */
public final class MazeIO {

    // @anchor: io_board_format
    // 盘面格式：size/start/end 头 + walls 段（每格一位十六进制掩码）+ scores 段（1~9 为得分点标记，. 无）；
    // scores 段里的 '#' 视为默认标记 1（兼容旧盘面）；仅"# 后跟空白"的行算注释，得分点行可以整行以 '#' 开头
    /** 盘面文件扩展名（界面文件对话框使用） */
    public static final String BOARD_EXTENSION = ".maze";
    /** 文件首行标记，仅作人类可读的格式标识 */
    private static final String BOARD_MAGIC = "MazeSolver Board v1";

    private MazeIO() { }

    // @anchor: io_board_format_end

    // @anchor: io_save_board
    // 保存：头部写尺寸与起终点，随后按行写墙壁掩码与得分点标记数字，逐格无损落盘
    /** 将当前盘面写入文件（父目录需已存在） */
    public static void saveBoard(MazeModel model, File file) throws IOException {
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8))) {
            w.write("# " + BOARD_MAGIC);
            w.newLine();
            w.write("# 墙壁为 4 位掩码的十六进制值：1=NORTH 2=EAST 4=SOUTH 8=WEST；"
                    + "scores 段 1~" + MazeModel.MAX_SCORE_DIGIT + " 为得分点的标记数字，. 表示无（# 等同 1）");
            w.newLine();
            w.write("size " + model.getRows() + " " + model.getCols());
            w.newLine();
            w.write("start " + model.getStartR() + " " + model.getStartC());
            w.newLine();
            w.write("end " + model.getEndR() + " " + model.getEndC());
            w.newLine();
            w.write("walls");
            w.newLine();
            for (int r = 0; r < model.getRows(); r++) {
                StringBuilder sb = new StringBuilder(model.getCols());
                for (int c = 0; c < model.getCols(); c++) {
                    sb.append(Character.forDigit(model.getWallMask(r, c), 16));
                }
                w.write(sb.toString());
                w.newLine();
            }
            w.write("scores");
            w.newLine();
            for (int r = 0; r < model.getRows(); r++) {
                StringBuilder sb = new StringBuilder(model.getCols());
                for (int c = 0; c < model.getCols(); c++) {
                    int digit = model.getScoreDigit(r, c);
                    sb.append(digit == MazeModel.SCORE_NONE ? '.' : (char) ('0' + digit));
                }
                w.write(sb.toString());
                w.newLine();
            }
        }
    }

    // @anchor: io_save_board_end

    // @anchor: io_load_board
    // 载入：先解析出头字段与两段矩阵文本，再交给 buildModel 统一校验并还原
    /** 从文件载入盘面，返回一个新的 MazeModel；格式错误抛 IOException（不产出半成品） */
    public static MazeModel loadBoard(File file) throws IOException {
        int rows = -1, cols = -1;
        int startR = -1, startC = -1, endR = -1, endC = -1;
        boolean inWalls = false, inScores = false;
        List<String> wallLines = new ArrayList<>();
        List<String> scoreLines = new ArrayList<>();

        for (String raw : readLines(file)) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\s+");
            String key = parts[0].toLowerCase(Locale.ROOT);

            // 矩阵段先于注释判定：得分点行本身就以 '#' 开头（如 "#....#..#..."），
            // 所以只有 "# 后跟空白" 的才算注释行，否则会被误当成注释而丢掉一整行得分点
            if (inWalls) {
                if (key.equals("scores")) {
                    inWalls = false;
                    inScores = true;
                } else if (!isCommentLine(line)) {
                    wallLines.add(parts[0]);
                }
                continue;
            }
            if (inScores) {
                if (!isCommentLine(line)) scoreLines.add(parts[0]);
                continue;
            }
            if (isCommentLine(line)) continue;

            switch (key) {
                case "size":
                    rows = intAt(parts, 1, line);
                    cols = intAt(parts, 2, line);
                    break;
                case "start":
                    startR = intAt(parts, 1, line);
                    startC = intAt(parts, 2, line);
                    break;
                case "end":
                    endR = intAt(parts, 1, line);
                    endC = intAt(parts, 2, line);
                    break;
                case "walls":
                    inWalls = true;
                    break;
                case "scores":
                    inScores = true;
                    break;
                default:
                    throw new IOException("无法识别的行: " + line);
            }
        }
        return buildModel(rows, cols, startR, startC, endR, endC, wallLines, scoreLines);
    }

    // @anchor: io_load_board_end

    // @anchor: io_build_model
    // 校验并还原：尺寸区间、行数/列数、掩码与得分点字符都要合法，任一处不符即报错而不产出半成品
    private static MazeModel buildModel(int rows, int cols, int startR, int startC, int endR, int endC,
                                        List<String> wallLines, List<String> scoreLines) throws IOException {
        if (rows < 0 || cols < 0) throw new IOException("缺少 size 行");
        if (rows < MazeModel.MIN_DIM || rows > MazeModel.MAX_DIM
                || cols < MazeModel.MIN_DIM || cols > MazeModel.MAX_DIM) {
            throw new IOException("尺寸需在 " + MazeModel.MIN_DIM + "~" + MazeModel.MAX_DIM
                    + " 之间，文件中为 " + rows + "x" + cols);
        }
        if (wallLines.size() != rows) {
            throw new IOException("墙壁行数应为 " + rows + "，实际 " + wallLines.size()
                    + "（若首列为 '#'，需写成 '# 注解' 形式才被视为注释）");
        }
        if (!scoreLines.isEmpty() && scoreLines.size() != rows) {
            throw new IOException("得分点行数应为 " + rows + "，实际 " + scoreLines.size());
        }

        MazeModel model = new MazeModel(rows, cols);
        for (int r = 0; r < rows; r++) {
            String row = wallLines.get(r);
            if (row.length() != cols) {
                throw new IOException("第 " + (r + 1) + " 行墙壁应有 " + cols + " 个字符，实际 " + row.length());
            }
            for (int c = 0; c < cols; c++) {
                int mask = Character.digit(row.charAt(c), 16);
                if (mask < 0) {
                    throw new IOException("第 " + (r + 1) + " 行墙壁含非法字符: " + row.charAt(c));
                }
                model.setWallMask(r, c, mask);
            }
        }
        for (int r = 0; r < scoreLines.size(); r++) {
            String row = scoreLines.get(r);
            if (row.length() != cols) {
                throw new IOException("第 " + (r + 1) + " 行得分点应有 " + cols + " 个字符，实际 " + row.length());
            }
            for (int c = 0; c < cols; c++) {
                char ch = row.charAt(c);
                if (ch == '.') {
                    model.setScoreDigit(r, c, MazeModel.SCORE_NONE);
                } else if (ch == '#') {
                    // 旧盘面用 '#' 表示"有得分点"，按默认标记还原
                    model.setScoreDigit(r, c, MazeModel.DEFAULT_SCORE_DIGIT);
                } else if (ch >= '0' + MazeModel.MIN_SCORE_DIGIT && ch <= '0' + MazeModel.MAX_SCORE_DIGIT) {
                    model.setScoreDigit(r, c, ch - '0');
                } else {
                    throw new IOException("第 " + (r + 1) + " 行得分点含非法字符: " + ch
                            + "（可写 1~" + MazeModel.MAX_SCORE_DIGIT + " 或 . 或 #）");
                }
            }
        }
        normalizeInternalWalls(model);
        // 起终点最后落定：它们所在格的得分点会被清掉，保证"起终点不参与计分"的约定在载入后同样成立
        if (startR >= 0 || startC >= 0 || endR >= 0 || endC >= 0) {
            if (startR < 0 || startC < 0 || endR < 0 || endC < 0) {
                throw new IOException("start / end 必须成对出现");
            }
            if (!model.setEndpoints(startR, startC, endR, endC)) {
                throw new IOException("起终点非法（越界或重合）: (" + startR + "," + startC
                        + ") -> (" + endR + "," + endC + ")");
            }
        }
        return model;
    }

    // @anchor: io_build_model_end

    // @anchor: io_normalize_walls
    // 内部墙双向归并：文件里可能只写了某一侧（相邻两格掩码不一致），归并成"任一侧有墙即两侧都有"，
    // 与视图"单侧有墙就画这条线"的绘制结果保持一致，避免出现"看得见墙却走得过去"
    /** 把内部墙归并为双向一致（外边界缺口原样保留） */
    private static void normalizeInternalWalls(MazeModel model) {
        int rows = model.getRows();
        int cols = model.getCols();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (c + 1 < cols) {
                    boolean eastWall = model.hasWall(r, c, MazeModel.EAST)
                            || model.hasWall(r, c + 1, MazeModel.WEST);
                    model.setWallSide(r, c, MazeModel.EAST, eastWall);
                }
                if (r + 1 < rows) {
                    boolean southWall = model.hasWall(r, c, MazeModel.SOUTH)
                            || model.hasWall(r + 1, c, MazeModel.NORTH);
                    model.setWallSide(r, c, MazeModel.SOUTH, southWall);
                }
            }
        }
    }
    // @anchor: io_normalize_walls_end


    // @anchor: io_helpers
    // 底层读写工具：UTF-8 纯文本按行读取 + 整数字段解析（错误信息带上原行，便于定位手工改坏的地方）
    private static List<String> readLines(File file) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) lines.add(line);
        }
        return lines;
    }

    private static int intAt(String[] parts, int index, String line) throws IOException {
        if (index >= parts.length) throw new IOException("字段不足: " + line);
        try {
            return Integer.parseInt(parts[index]);
        } catch (NumberFormatException e) {
            throw new IOException("应为整数: " + line);
        }
    }

    /** 注释行判定：'#' 后跟空白（或整行只有 '#'）才算注释，这样以 '#' 开头的得分点行不会被误吞 */
    private static boolean isCommentLine(String line) {
        if (line.isEmpty() || line.charAt(0) != '#') return false;
        return line.length() == 1 || Character.isWhitespace(line.charAt(1));
    }

    // @anchor: io_helpers_end
}
