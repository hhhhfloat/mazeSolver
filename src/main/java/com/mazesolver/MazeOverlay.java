// @anchor: maze_overlay_intro
// 抽样叠画数据：把抽样路径按"每段物理线只描一次"去重并预折算成像素线段，分桶存好，重绘时只描线、零计算。
package com.mazesolver;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 抽样叠画的线段数据。枚举结果规模不可控，因此这里在"交给视图时"就完成去重与坐标折算，
 * 重绘开销只与去重后的线段数有关，与枚举总条数无关。
 */
public final class MazeOverlay {

    // @anchor: overlay_state
    // 数据状态：按调色板分桶的线段（每段 {x1,y1,x2,y2}）+ 参与叠画的路径条数与线段总数；buckets 为 null 表示无叠画
    private List<List<double[]>> buckets = null;
    private int pathCount = 0;
    private int segmentCount = 0;
    // @anchor: overlay_state_end

    // @anchor: overlay_build
    // 建立叠画：抽样路径大量重叠（起终点附近几乎完全重合），同一段线只描一次，描线数量直接降一个量级
    /** 设置抽样叠画的路径（每条为格子坐标序列）；传 null 或空即关闭叠画 */
    public void setPaths(MazeModel model, int cellSize, List<List<int[]>> paths) {
        clear();
        if (model == null || paths == null || paths.isEmpty()) return;

        int cols = model.getCols();
        int bucketCount = MazeTheme.overlayBucketCount();
        List<List<double[]>> built = new ArrayList<>();
        for (int i = 0; i < bucketCount; i++) built.add(new ArrayList<>());
        Set<Long> drawnSegments = new HashSet<>();

        int drawn = 0;
        int segments = 0;
        for (List<int[]> path : paths) {
            if (path == null || path.size() < 2) continue;
            drawn++;
            List<double[]> bucket = built.get((drawn - 1) % bucketCount);
            for (int i = 0; i < path.size() - 1; i++) {
                int[] from = path.get(i);
                int[] to = path.get(i + 1);
                int idFrom = from[0] * cols + from[1];
                int idTo = to[0] * cols + to[1];
                long key = ((long) Math.min(idFrom, idTo) << 32) | Math.max(idFrom, idTo);
                if (!drawnSegments.add(key)) continue;
                bucket.add(new double[]{
                        from[1] * cellSize + cellSize / 2.0, from[0] * cellSize + cellSize / 2.0,
                        to[1] * cellSize + cellSize / 2.0, to[0] * cellSize + cellSize / 2.0});
                segments++;
            }
        }
        buckets = built;
        pathCount = drawn;
        segmentCount = segments;
    }

    /** 关闭叠画（枚举结果作废 / 换尺寸重建画布 / 用户关掉开关时调用） */
    public void clear() {
        buckets = null;
        pathCount = 0;
        segmentCount = 0;
    }
    // @anchor: overlay_build_end

    // @anchor: overlay_accessors
    /** 当前是否有可描线的叠画 */
    public boolean isEmpty() {
        return buckets == null || segmentCount == 0;
    }

    /** 参与叠画的路径条数（供主界面回显） */
    public int getPathCount() {
        return pathCount;
    }

    /** 去重后的线段总数（供自测与状态回显） */
    public int getSegmentCount() {
        return segmentCount;
    }

    /** 供渲染器逐桶描线的线段数据；无叠画时返回 null */
    public List<List<double[]>> getBuckets() {
        return buckets;
    }
    // @anchor: overlay_accessors_end
}
