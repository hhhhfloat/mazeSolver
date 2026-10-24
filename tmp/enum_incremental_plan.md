<!-- @anchor: enum_plan_intro -->
<!-- 增量枚举可行性评估：现状事实、结论、推荐方案、改造清单、风险与决策点。本文件仅为规划，未改动任何代码。 -->

# 增量枚举（在已有结果基础上继续枚举）可行性评估

## 一、现状事实（均为代码实证）

- `MazeSolver.findAllPaths(int maxPaths)` 是**一次性函数**：搜索状态（`visited[][]`、`currentPath`、显式栈 `stack`、`allPaths`）全是局部变量，返回即丢失；每次调用都从起点重新 DFS。
- 收录到上限时执行 `allPaths.size() >= limit → lastEnumerationTruncated = true; break;`，此时栈顶正是终点格且**未回溯**；一旦改成可续跑，"续跑第一步"必须补一次回溯，否则会重复收录同一条路径。
- 搜索顺序完全确定：方向表 `DIRS_ENCODED` 固定为 NORTH/SOUTH/WEST/EAST，无随机、无剪枝 → 同一盘面 + 同一起终点，第 k 条路径永远是同一条。
- `lastEnumerationTruncated` 目前"收录满 limit 即 true"，会出现"恰好最后一条也报截断"的轻微误报。
- 调用侧：`MazeApp.onSolve()` → `allPaths = solver.findAllPaths(limit)` → 写 `tmp/all_paths.txt` → `pathPanel.setPaths(allPaths)`；`clearPathList()` 是**所有作废入口的唯一汇合点**（生成、清空、改尺寸、载入盘面、复位起终点、视图 `setOnEdited` 全部经它）。
- `PathListPanel.populatePathList()` / `applyFilterAndPopulate()` 每次都对**全部** `allPaths` 调 `analyzePath`（O(条数 × 平均步长)）；若增量 k 次而不缓存 → 总开销 O(k²)。
- `savePathsToFile` 为**全量覆盖写**，头部含 `# limit` / `# truncated` / `# count`。
- 上限：`DEFAULT_MAX_PATHS = 2000`，`MAX_ENUM_LIMIT = 1_000_000`。
- 盘面指纹可算：`MazeModel` 已有 `getWallMask(r,c)`、`getScoreDigit(r,c)`、`getRows/getCols/getStartR../getEndC`。
- 自测兼容点：`SmokeIo` 对**同一** `perfectSolver` 连续调用 `findAllPaths(2000)` 与 `findAllPaths(3)`，均期望返回 1 条且未截断；`SmokeReflect`/`SmokeSample`/`SmokeImport`/`SmokeDigits`/`SmokeEndpoints` 各一次调用。

## 二、结论

**可行，且是"零精度损失"的增量**：显式栈帧（行、列、下一方向下标）+ `visited` + `currentPath` **已经包含恢复搜索所需的全部信息**，挂起/恢复不丢分支、不重复、不遗漏。

但要注意收益的边界：
- 生成器产出的是**完美迷宫**（任意两点恰一条通路）→ 枚举恒为 1 条，增量收益为 0（此时也不会退化，直接命中"已穷尽"标志返回）。
- 增量只在**多连通盘面**（用户挖开墙壁 / 载入非完美 maps）有意义。它的真正价值不是"算得更快"，而是**让用户先拿少量结果看分布与筛选效果，再决定要不要加深**，避免一上来就按 1e6 上限卡死界面。

被否决的两个替代方案：
- 从零重跑 + 跳过已有：每次仍要重走全部分支，k 次续跑 O(k²)，正是要避免的。
- 一次跑满 `MAX_ENUM_LIMIT`：内存与卡顿都不可接受（见风险 R1）。

## 三、推荐方案（状态机化，方案 A）

新增锚点 `solver_incremental_state`（置于 `solver_enum_limit` 之后）持有搜索现场：

- `List<List<Integer>> collectedPaths`：累计结果，**对外返回同一实例**（面板只持引用，不复制——复制会让面板拿到过期快照）。
- `Deque<int[]> searchStack` / `boolean[][] searchVisited` / `List<Integer> searchCurrent`：从局部变量提升为字段。
- `boolean searchExhausted`：栈空，搜索空间已穷尽，之后再调也直接返回。
- `boolean resumeNeedsBacktrack`：上次停在"刚收录终点、尚未回溯"处 → 续跑第一步先补回溯（防重复收录）。
- `long boardFingerprint`：`rows/cols/start/end + 逐格 wallMask + 逐格 scoreDigit` 的哈希。

对外 API：

- `List<List<Integer>> continueFindAllPaths(int targetTotal)`：核心。流程 = 区间收敛 → 首次则初始化 → **指纹校验（不一致自动 `resetEnumeration()` 从头）** → 已穷尽则直接返回 → 已有条数 ≥ 目标则直接返回 → 若 `resumeNeedsBacktrack` 先回溯一次 → 复用现有 while 循环 → 收录到目标即置 `resumeNeedsBacktrack` 并停 → 循环自然结束则置 `searchExhausted` → 刷新 `lastEnumerationLimit` / `lastEnumerationTruncated = !searchExhausted`。
- `findAllPaths()` / `findAllPaths(int)`：保留签名，语义变为"续跑到目标总数"（旧调用点零改动）。
- `boolean hasMorePaths()`（= `!searchExhausted`）、`void resetEnumeration()`、`List<List<Integer>> getCollectedPaths()`。

语义建议（详见决策点 D2）：**目标上限只增不减**——传比已有条数更小的目标时**不缩水、不重跑**，直接返回已有结果。这既满足 `SmokeIo` 的既有断言，也避免"调小上限就偷偷丢数据"。

## 四、改造清单（按文件，精确到锚点）

1. `MazeSolver`
   - 新增 `solver_incremental_state`（字段）、`solver_continue`（核心续跑）、`solver_reset`（作废 + 指纹）。
   - `solver_all_paths` 内循环体复用，仅改"收录后"与"结束"两处判定；`solver_enum_limit` 追加 `hasMorePaths()` 与截断语义注释。
   - `solver_save_paths` 头部补 `# continued`（可选）并把 `# truncated` 注释改为"是否还有未枚举的路径"。
2. `PathListPanel`
   - `list_state` 增 `List<PathRow> rowCache`（与 `allPaths` 同长）。
   - `list_api.setPaths` 改为增量：缓存短于 `allPaths.size()` 时只补算新增段 `[old, new)`，再走一次填充。
   - `list_populate_all` / `list_populate_filtered` 改读 `rowCache`，不再调用 `analyzePath`。
3. `MazeApp`
   - `app_solve_action`：改用 `continueFindAllPaths(limit)`（返回同一实例）。
   - 新增 `app_continue_enum`：「继续枚举」动作（穷尽时提示"已穷尽"而非空跑）。
   - `app_clear_list`：加 `solver.resetEnumeration()`——一处覆盖全部作废入口。
   - `app_enum_limit` / `app_enum_hint`：输入框语义改"目标总条数"，状态行区分"已穷尽 N 条"/"已枚举 N 条（还有更多）"。
4. `MazeControlPanel`
   - 枚举上限栏加「继续枚举」按钮（`control_enum_row`）。
5. `tmp/check_ui_wiring.py`：按钮数断言 +1，并纳入新按钮的事件绑定检查。
6. `PROJECT.md`：新增"枚举是增量的、可续跑"架构决策与"上限=目标总数""续跑前校验盘面指纹"两条约定。
7. `UPDATE.md`：追加实施记录。

## 五、风险与对策

- **R1 内存（首要瓶颈）**：`List<List<Integer>>` 每条 ≈ 32 + 4×步长 字节（方向值 0~3 命中 `Integer` 缓存，不额外分配，但仍是一个 `ArrayList` + 一个 `Object[]`）。
  - 100 步 × 1e6 条 ≈ 430MB；60×45 迷宫 300 步 × 1e6 条 ≈ 1.2GB。**`MAX_ENUM_LIMIT = 1_000_000` 在常规堆下不现实**。
  - 对策：短期 = 下调上限（如 2e5）+ 续跑前按"已有条数 × 平均步长 × 估算系数"给出提示；中期可选 = 路径改 `byte[]`/`int[]` 紧凑存储（省 3~4 倍，但会波及 `analyzePath`、`dirCodesToCoordinates`、列表缓存、叠画、文件输出等约 8 处消费方，`PathSequenceFilter` 不受影响）。**建议先做短期，紧凑化单独立项。**
- **R2 UI 阻塞**：单次续跑仍可能长跑（2000 → 200000）。设计上应保证"每次续跑粒度可控"，未来套后台线程无需改算法。本轮建议仍同步执行（与现状一致，零额外复杂度）。
- **R3 作废一致性（最易出隐性 bug）**：漏一个入口 → 续跑把**旧盘面的结果**接到新盘面后面。双重保险：①`clearPathList()` 内统一 `resetEnumeration()`；②续跑前指纹校验，不一致自动从头。
- **R4 语义歧义**："上限"到底是目标总数还是本次增量条数，必须在 UI 文案与文档里写死一种（见 D1）。
- **R5 自测兼容**：`SmokeIo` 连续两次调用的断言依赖"目标调小不缩水、穷尽不重复收录"，需在实施后回归；`SmokeReflect` 等单次调用不受影响。
- **R6 引用与释放**：`allPaths` 与 `solver.collectedPaths` 变成同一实例后，`clearPathList()` 置 null 不再释放内存 → 必须同时 `resetEnumeration()` 清空 `collectedPaths`。

## 六、需确认的决策点

- **D1 上限语义**：输入框 = "目标总条数"（点「继续枚举」跑到该数，推荐）／ 还是 = "每次新增条数"（每次点都 +N）。
- **D2 上限调小**：不缩水、直接返回已有（推荐，兼容现有断言）／ 重置后重跑到新上限。
- **D3 内存策略**：仅下调上限 + 估算提示（改动小，推荐先做）／ 同时把路径改紧凑数组（改动大）。
- **D4 并发**：保持同步执行（推荐）／ 引入后台线程 + 取消按钮。

## 七、实施步骤（每步可独立编译验证）

1. `MazeSolver` 状态机化 + `continueFindAllPaths`/`resetEnumeration`/`hasMorePaths` + 指纹；新增 `tmp/SmokeIncremental.java` 断言：①分 3 段续跑（50/120/300）的拼接结果与一次跑满 300 条**逐条完全一致**；②无重复、无遗漏；③穷尽后再调不新增且 `hasMorePaths()==false`；④改墙/改起终点后自动从头；⑤目标调小不缩水。
2. `PathListPanel` 行缓存 + 增量填表；断言条数/最高最低分/筛选结果与原全量算法一致。
3. `MazeApp` + `MazeControlPanel` 接线（「继续枚举」按钮、状态文案、`clearPathList` 内 reset）；更新 `check_ui_wiring.py`。
4. 结果文件语义更新（仍全量重写，格式不改）+ 导出文案。
5. `PROJECT.md` / `UPDATE.md` 收尾，跑全部自测（`SmokeReflect` / `SmokeEndpoints` / `SmokeIo` / `SmokeImport` / `SmokeSample` / `SmokeDigits` / `SmokeIncremental` / `check_ui_wiring`）。

<!-- @anchor: enum_plan_end -->
<!-- 末尾锚点：新的评估补充记录插入此锚点之前 -->
