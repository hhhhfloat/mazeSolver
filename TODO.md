<!-- @anchor: todo_intro -->
<!-- 本轮任务：把"一次性枚举"改造成"可续跑的增量枚举"，并按 D1/D2/D3/D4 的决策落地到界面。本文件仅记录步骤与进度。 -->

# 增量枚举落地步骤

## 决策（据用户答复固化）

- **D1**：输入框 = **目标总条数**；「+」按钮 = 在已有结果上继续加深；「求解」= 先看目标是否已达成，未达成才增量枚举。
- **D2**：目标调小时**不缩水、不重跑**，直接返回已有结果（与既有断言兼容）。
- **D3**：内存用**实测平均步长的预算闸门**（比固定上限精确，比紧凑数组改动小且不牵连消费方）；同时把硬天花板下调。
- **D4**：保持**同步执行**，不加后台线程（算法已是可挂起的粒度，日后套线程无需改算法）。

## 步骤

- [x] 1. `MazeSolver` 现场状态机化：新增 `solver_incremental_state` / `solver_continue` / `solver_reset` 锚点，`continueFindAllPaths` + `resetEnumeration` + `hasMorePaths` + 盘面指纹 + 内存预算闸门；`findAllPaths` 语义改为"续跑到目标总数"。自测 `tmp/SmokeIncremental.java`。✅完成
- [ ] 2. `PathListPanel` 行缓存：`setPaths` 只补算新增段，`populate*` 不再重复 `analyzePath`，避免增量 k 次退化 O(k²)。
- [ ] 3. 界面接线：`MazeControlPanel` 枚举栏加「+」按钮；`MazeApp.onSolve` 走"目标已达成则直取/否则增量"、新增 `onContinueEnum`、`clearPathList` 内 `resetEnumeration`、状态行区分"还有更多/已穷尽/受内存预算限制"；`tmp/check_ui_wiring.py` 按钮数与接线断言同步。
- [ ] 4. 结果文件语义更新：`# limit` 注释为"目标总条数"，`# truncated` 注释为"是否还有未枚举的路径"，仍全量重写、格式不变。
- [ ] 5. 文档收尾（`PROJECT.md` 架构决策与约定、`UPDATE.md` 追加记录）+ 全量自测回归（含 `SmokeIo` 的"目标调小不缩水"）。

<!-- @anchor: todo_end -->
<!-- 末尾锚点：新的步骤条目插入此锚点之前 -->
