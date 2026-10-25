<!-- @anchor: update_intro -->
<!-- maze-solver 更新记录：历史条目按 log_NNN 顺序保留原文，新记录统一插入末尾追加锚点之前 -->

# 更新记录

<!-- @anchor: log_001 -->
## v1.0.0 - 初始版本 (2026-01-18)
- 创建 Maven + JavaFX 17 项目结构
- 实现 MazeModel：20×20 位掩码墙壁数据模型
- 实现 MazeGenerator：DFS 递归回溯生成完美迷宫
- 实现 MazeSolver：BFS 最短路径求解
- 实现 MazeView：Canvas 渲染（墙壁/起点/终点/路径）
- 实现 MazeApp：JavaFX 主界面（生成/求解/重置按钮）
- Maven 编译通过
<!-- @anchor: log_001_end -->

<!-- @anchor: log_002 -->
## v1.1.0 - 迷宫编辑功能 (2026-07-18)
- MazeModel 新增 `addWall`、`addWallBetween`、`toggleWallBetween` 方法
- MazeView 新增编辑模式：鼠标悬停高亮 + 点击切换内部墙壁
- 外边界墙壁始终保持封闭，不可修改
- 编辑模式下自动切换十字光标，橙色高亮悬停墙壁
- MazeApp 新增"编辑迷宫"ToggleButton，编辑时禁用生成/求解
- 编辑操作自动清除当前求解路径
- Maven 编译通过
<!-- @anchor: log_002_end -->

<!-- @anchor: log_003 -->
## v1.2.0 - 全路径枚举 (2026-07-18)
- MazeSolver 新增 `findAllPaths()`：DFS 回溯找出所有理论路径（不重复访问单元格）
- 方向编码：0=NORTH(上), 1=SOUTH(下), 2=WEST(左), 3=EAST(右)
- MazeSolver 新增 `savePathsToFile()`：将所有路径写入 all_paths.txt
- 求解按钮点击时同步执行 BFS（视图显示最短路径）+ DFS 全路径枚举（文件输出）
- Maven 编译通过
<!-- @anchor: log_003_end -->

<!-- @anchor: log_004 -->
## v1.3.0 - 滚动列表查询单个解法 (2026-07-18)
- MazeSolver 新增 `dirCodesToCoordinates()`：将方向编码路径转换为坐标列表
- MazeApp 新增右侧滚动列表面板（JavaFX ListView），求解后自动填充所有路径
- 点击列表中的路径项，视图即时切换渲染对应解法（蓝色路径线）
- 列表显示每条路径的步数（如"路径 1 (45步)"）
- 生成迷宫 / 重置操作自动清空路径列表
- Maven 编译通过
<!-- @anchor: log_004_end -->

<!-- @anchor: log_005 -->
## v1.4.0 - 得分点系统 (2026-07-18)
- MazeModel 新增得分点数据结构 `boolean[][] scorePoints`，支持 `hasScorePoint` / `addScorePoint` / `clearScorePoints`
- MazeGenerator 生成迷宫后随机放置约 30 个金色得分点（避开起点和终点）
- MazeView 在单元格中心绘制金色小圆（直径约 0.3 倍单元格）表示得分点
- MazeSolver 新增 `computePathScore()` 方法，计算路径经过的得分点总数（每个得分点只计一次）
- MazeApp 路径列表显示每条路径的得分（如"路径 1 (45步, 3分)"）
- 侧面板新增统计标签：显示路径总数、最高分、最低分
- Maven 编译通过
<!-- @anchor: log_005_end -->

<!-- @anchor: log_006 -->
## v1.5.0 - 得分点编辑功能 (2026-07-18)
- MazeModel 新增 `toggleScorePoint(r, c)` 方法：切换指定单元格的得分点状态
- MazeView 新增 `findNearestCell(mx, my)` 方法：检测鼠标所在单元格
- 编辑模式下，点击空白单元格可添加/移除金色得分点（起点和终点不可编辑）
- 编辑模式下，悬停空白单元格显示半透明金色方框高亮
- 编辑得分点后自动清除当前求解路径（得分已变化）
- 鼠标交互优先级：墙壁编辑 > 得分点编辑
- Maven 编译通过
<!-- @anchor: log_006_end -->

<!-- @anchor: log_007 -->
## v1.6.0 - 编辑按钮分离 + 清空按钮 (2026-07-18)
- 将"编辑迷宫" ToggleButton 拆分为两个独立按钮："编辑墙壁"和"编辑得分点"
- 两个编辑按钮互斥：选中一个自动取消另一个
- 编辑模式下禁用生成/求解按钮的逻辑适配双按钮
- MazeView 将单一 `editMode` 拆分为 `wallEditMode` 和 `scoreEditMode`
- 鼠标交互根据当前活动模式分别响应（墙壁编辑模式只响应墙壁、得分点编辑模式只响应单元格）
- MazeView 新增 `setWallEditMode()` / `setScoreEditMode()` 替代旧 `setEditMode()`
- 新增"清空"按钮：一键清除所有内部墙壁和所有得分点
- MazeModel 新增 `clearInternalWalls()` 方法：移除所有相邻单元格之间的墙壁，保留外边界
- Maven 编译通过
<!-- @anchor: log_007_end -->

<!-- @anchor: log_008 -->
## v1.7.0 - 长按连续修改 + 按参数搜索 (2026-07-18)

### 长按连续修改
- MazeView 新增长按状态字段：`mouseDown`、`wallEditAdding`、`scoreEditAdding` 等
- 将 `setOnMouseClicked` 替换为 `setOnMousePressed` / `setOnMouseDragged` / `setOnMouseReleased`
- 按下时锁定操作方向（加墙/拆墙、加得分点/拆得分点），拖拽时持续应用同方向操作
- `applyWallAction(r,c,dir,adding)`：仅当目标墙壁状态与操作方向匹配时才执行修改
- `applyScoreAction(r,c,adding)`：仅当目标得分点状态与操作方向匹配时才执行修改
- `lastToggledWallR/C/Dir` 和 `lastToggledCellR/C` 防止重复切换同一目标
- 鼠标离开或编辑模式切换时自动重置长按状态

### 按参数搜索
- MazeApp 侧面板新增"步数范围"输入框（最小/最大）和"分数范围"输入框（最小/最大）
- 新增"搜索"按钮：按输入的步数/分数范围过滤路径列表
- 新增"清除"按钮：清空过滤条件并还原全部路径
- `applyFilterAndPopulate()`：解析过滤条件、遍历 allPaths 按步数和分数筛选
- `filteredPathIndices`：维护过滤后列表位置到原始 allPaths 索引的映射
- 路径列表选择事件适配过滤索引映射，点击过滤后的路径可正确渲染
- Maven 编译通过
<!-- @anchor: log_008_end -->

<!-- @anchor: log_009 -->
## v1.8.0 - 迷宫形状可调整 (2026-07-26)

### 形状工具栏
- MazeApp 顶部新增形状配置栏：行数输入框 + 列数输入框 + "应用"按钮
- 行列数范围限制：3 ~ 100（超出自动恢复原值）
- 移除所有硬编码的 ROWS/COLS 常量，改为实例字段 `rows`/`cols`（默认 20）

### 动态画布
- MazeView 新增 `rebuildCanvas(rows, cols)` 方法：根据新尺寸重建 Canvas
- MazeView 新增 `setModel(MazeModel)` 方法：更新模型引用
- 单元格大小 `cellSize` 动态计算：≤25 用 30px，≤40 用 20px，≤60 用 14px，≤80 用 10px，>80 用 8px
- 所有绘制方法（墙壁、路径、得分点、悬停高亮）均基于动态 cellSize
- 墙壁命中检测阈值、路径线宽、得分点半径均按 cellSize 比例自适应

### 高效形状切换
- `applyShapeChange()` 仅重建模型/生成器/求解器/画布，不重建整个 UI
- 工具栏和侧面板保持不变，编辑状态自动重置

### 得分点按比例生成
- MazeGenerator 得分点密度 `SCORE_DENSITY = 0.07`（约占总单元格数 7%）
- 数量不再硬编码为 30，而是动态计算：`max(1, totalCells * 0.07)`
- 安全上限确保不超过可放置单元格数（总格数 - 2）

### 其他改进
- 窗口标题动态更新："迷宫求解器 - {rows}×{cols}"
- 窗口可自由调整大小 (`stage.setResizable(true)`)
- 所有文件硬编码数值已清除，全部改为动态引用

### Maven 编译通过
<!-- @anchor: log_009_end -->

<!-- @anchor: log_010 -->
## v1.8.1 - 修复形状调整后编辑功能失效 (2026-07-26)

### 问题诊断
- `applyShapeChange()` 在重建迷宫时未重置编辑模式状态
- `btnEditWall`、`btnEditScore`、`btnGenerate`、`btnSolve` 是 `buildUI()` 的局部变量，`applyShapeChange()` 无法访问
- 导致形状变更后编辑 ToggleButton 保持选中、view 编辑模式未清除，后续编辑操作状态混乱

### 修复内容
- 将 `btnEditWall`、`btnEditScore`、`btnGenerate`、`btnSolve` 提升为 MazeApp 类字段
- `applyShapeChange()` 中新增编辑模式重置逻辑：
  - 取消选中 `btnEditWall` / `btnEditScore`
  - 调用 `view.setWallEditMode(false)` / `view.setScoreEditMode(false)`
  - 重新启用 `btnGenerate` / `btnSolve`
- `buildUI()` 中对应的局部声明改为对类字段的赋值

### Maven 编译通过
<!-- @anchor: log_010_end -->

<!-- @anchor: log_011 -->
## v1.9.0 - 项目规整（锚点规范 / 文档瘦身 / 产物收敛）(2026-10-24)

### 锚点规范
- 5 个源文件补齐 `_intro` 锚点，功能块统一为 `[module]_[function]` + `_end` 成对形式，锚点后紧邻职责描述
- 修正成对关系错乱：`model_toggle_wall`/`model_toggle_end`、`solver_bfs_loop`/`solver_bfs_end` 等
- 修正区间嵌套错位：`model_toggle_score_point` 原跨在 `model_clear_internal_walls` 前后
- 移除占位锚点 `view_end_marker`
- UPDATE.md 旧锚点 ID 含 `.`，索引无法解析 → 全部改为 `log_NNN` / `log_NNN_end`，并补 `update_intro` 与末尾追加锚点

### 代码清理（不改变行为）
- MazeSolver：游离在"方向编码→坐标"小节前的 `savePathsToFile` javadoc 归位到方法上
- MazeModel：删除悬空注释，`addWall` 并入墙壁查询块
- MazeApp：路径列表字段集中，`filteredPathIndices` 与 `allPaths` 同步维护

### 修复 bug
- 编辑墙壁/得分点后右侧列表仍显示已失效路径：`view.setOnWallToggled` 原为空实现（注释还引用了不存在的方法），改为 `this::clearPathList`

### 产物收敛
- pom 构建输出重定向到 `tmp/target`，项目根目录不再产生 `target/`
- 全路径输出改为 `tmp/all_paths.txt`（自动建目录），删除根目录遗留的 `all_paths.txt`
- 新增 `.gitignore` 忽略 `tmp/`

### 文档
- PROJECT.md 瘦身为语义级文档：移除目录树、类清单、锚点清单表，聚焦定位/架构决策/约定/构建运行

### 验证
- `mvn compile` 通过
- 无界面自测（tmp/SmokeReflect.java，反射加载 classes）：BFS 最短路 94 步、DFS 枚举路径数 = 1（完美迷宫成立）、坐标数与步数一致、路径文件正常写出、清空后外边界仍封闭
<!-- @anchor: log_011_end -->

<!-- @anchor: log_012 -->
## v1.9.0 备注 - 产物隔离的落地方式 (2026-10-24)
- 沙箱不允许创建以 `.` 开头的文件，故上述 `.gitignore` 未实际创建（本条为更正）
- 产物隔离改由两处硬约束保证：pom 的 `build.directory` 指向 `tmp/target`；求解输出固定写 `tmp/all_paths.txt`
- 后续记录编号顺延 `log_013`
<!-- @anchor: log_012_end -->


<!-- @anchor: log_013 -->
## 构建校验 - 仅编译主源码，不编译测试 (2026-10-24)
- 执行 `mvn compile`（只到 compile 阶段，不做 test-compile，也无测试源码）
- 5 个主源文件全部编译通过，产物落 `tmp/target/classes/com/mazesolver/`：MazeApp、MazeGenerator、MazeModel、MazeSolver、MazeView
- 项目根目录保持干净，无 `target/` 残留；无测试类生成
- 后续记录编号顺延 `log_014`
<!-- @anchor: log_013_end -->


<!-- @anchor: log_014 -->
## v1.10.0 - 修复编辑无效 + 迷宫尺寸范围放开 (2026-10-24)

### 修复：点击编辑墙壁/得分点无效
- 根因：`MazeView.applyWallAction` 与 `applyScoreAction` 的状态判定写反了
  - `wallEditAdding` / `scoreEditAdding` 在按下时被设为"目标状态"（`!hasWall` / `!hasScorePoint`），
    但执行体里写成 `if (adding != currentState) return;`，等于"目标与现状不同就不动手"，
    于是加墙/拆墙/加得分点/拆得分点全部变成空操作。
  - 改为 `if (adding == currentState) return;`（仅当已处于目标状态才跳过）。
- 外边界墙壁（首行北侧、首列西侧、末行南侧、末列东侧）完全无法编辑：
  - `findWall` 原先只检测内部墙（北侧限 `r>0`、西侧限 `c>0`），边界永远命中不到；
    现在按"每段物理墙一个规范侧"补齐四段边界命中。
  - `applyWallAction` 原先要求相邻格存在，边界处直接 return；新增 `MazeModel.setWallSide(r,c,dir,present)`
    统一处理：内部侧双向同步（等价 addWallBetween），边界侧只改本格一面。
- 副作用说明：边界缺口只是视觉标记，求解器仍只在格内搜索；重新生成时边界恢复封闭。

### 新增：迷宫尺寸真正可调（3~200）
- `MazeModel` 新增 `MIN_DIM = 3` / `MAX_DIM = 200`，作为尺寸合法区间的唯一来源（原先 `3/100` 字面量散落在 `applyShapeChange` 里）。
- `MazeView.calcCellSize` 的 if 链改为分档表 `DIM_TIERS → TIER_CELL_PX`（25/40/60/80/120/200 → 30/20/14/10/8/6px），超出最高档兜底 `MIN_CELL_PX = 4`。
- 生成器 `MazeGenerator.dfs` 与求解器 `MazeSolver.findAllPaths/dfsFindAll` 改为**显式栈迭代**，去掉递归深度这个隐式尺寸上限（200×200 无栈溢出）。
- 主界面：
  - 非法尺寸不再静默恢复原值，改为 `lblShapeHint` 红字提示；输入框回车即应用；应用成功后回写规范化数值并显示当前尺寸。
  - 画布改由 `ScrollPane` + `StackPane` 承载（新增 `app_mount_canvas` 锚点）：内容最小尺寸跟随视口，小迷宫居中、大迷宫滚动，`applyShapeChange` 只换容器里的画布，不重建 ScrollPane。
  - 右侧列表去掉写死的 `prefHeight=380`，改为 `VBox.setVgrow` 随窗口伸缩。
  - 默认尺寸抽成 `DEFAULT_ROWS/DEFAULT_COLS` 常量。

### 校验
- `mvn compile`：BUILD SUCCESS（产物仍在 `tmp/target/classes`）。
- `tmp/SmokeReflect.java` 全绿：3/10×15/20/60×45/120/200 六档尺寸均生成成功、BFS 有解、枚举恰 1 条、边界封闭；
  边界四侧拆/补生效、内部墙双向同步、得分点切换、`findWall` 四类墙段命中、`applyWallAction` 真正改到墙。
<!-- @anchor: log_014_end -->

<!-- @anchor: log_015 -->
## v1.11.0 - 起点/终点可在迷宫范围内任选 (2026-10-24)

### 新增：起终点可选定
- `MazeModel` 新增起终点坐标（`model_endpoints` 锚点）：`getStartR/C`、`getEndR/C`、`isStart/isEnd`、
  `setStart/setEnd`（越界或与另一端点重合返回 false，并清除目标格得分点，维持"起终点不参与计分"）、
  `resetEndpoints`（构造期调用，默认左上角 / 右下角）。
- 求解器去掉硬编码：`solve`、`findAllPaths`、`dirCodesToCoordinates`、`computePathScore` 一律从模型取起终点；
  `reconstructPath(prev, endR, endC)` 显式接收终点；起终点重合时返回单格路径（防御分支）。
- 生成器撒分点改按 `model.isStart/isEnd` 判定，用户自定义的起终点不会被撒点；打洞起点仍是 (0,0)，与起终点无关。
- 视图：
  - 起终点按模型坐标绘制（绿起红终），不再写死在角上。
  - 新增拾取模式 `setEndpointEditMode(ENDPOINT_NONE / ENDPOINT_START / ENDPOINT_END)`：
    悬停格子显示绿/红方框（目标是另一端点时不高亮），按下即设定；
    成功后经 `applyEndpointAction` 作废当前解法并回调清空右侧列表。
  - 得分点编辑与悬停的"起终点不可撒点"判定改用 `model.isStart/isEnd`。
- 主界面：新增「设起点」「设终点」两个 ToggleButton；四个编辑开关（墙壁 / 得分点 / 起点 / 终点）互斥，
  由 `EditMode` 状态机 + `applyEditMode` 集中同步按钮选中态、视图模式与生成/求解可用性；
  再点同一开关即退出编辑；换尺寸时以 `applyEditMode(NONE)` 复位。

### 校验
- `mvn compile`：BUILD SUCCESS（仅主源码，产物在 `tmp/target/classes`）。
- 新增 `tmp/SmokeEndpoints.java` 16 项全绿：默认起终点、重合/越界拒绝、设起点清分点、BFS 首尾跟随、
  全路径枚举首尾跟随、重新生成保留起终点且不撒点、新模型回到角上、视图拾取生效与重合目标忽略。
- `tmp/SmokeReflect.java` 回归全绿：尺寸分档、边界开闭、内部墙双向同步、得分点切换、视图命中均不受影响。
<!-- @anchor: log_015_end -->


<!-- @anchor: log_016 -->
## v1.12.0 - 起终点调整入口上页面（回显 + 复位） (2026-10-24)

### 修复：页面上根本没有起终点调整入口
- 根因：`buildUI` 里 `btnEditStart` / `btnEditEnd` 只做了「创建 + `setOnAction`」，却没有出现在任何布局容器中
  （工具栏仍是 `HBox(btnGenerate, btnSolve, btnReset, btnEditWall, btnEditScore, btnClear)`）。
  后端（模型坐标、视图拾取）早已就绪，但界面上看不到入口，等于功能不可用。
- 处理：`app_top_layout` 由「两行」改为四行顶部区——尺寸栏 / 动作栏 / 编辑模式栏 / 起终点状态栏，
  四个互斥编辑开关与新增的「复位起终点」一起放进编辑模式栏。

### 新增：页面与后端呼应
- 起终点状态栏（新锚点 `app_endpoint_hint`）：从 `MazeModel` 读取并显示 `起点 (r,c) → 终点 (r,c)`，
  并按当前编辑模式给出提示（"正在拾取起点：单击画布任意格（绿色高亮）"等）；
  模式切换、编辑回调、换尺寸后都会刷新，页面不缓存坐标副本。
- 「复位起终点」按钮（新锚点 `app_endpoint_reset`）：调用 `MazeModel.resetEndpoints` 回到左上角/右下角，
  同时作废视图解法与右侧列表，避免页面留着已失效的结果。

### 重构
- 视图编辑回调改名：`onWallToggled` → `onEdited`、`setOnWallToggled` → `setOnEdited`，
  与"墙壁 / 得分点 / 起终点三类编辑共用同一回调"的实际语义一致；`MazeApp` 的回调里追加刷新起终点状态。
- 主界面字段区补 `btnResetEndpoints` / `lblEndpointHint`，按钮创建段与工具栏排布同步整理（缩进、分组注释）。

### 校验
- `mvn compile`：BUILD SUCCESS（仅主源码，产物在 `tmp/target/classes`）。
- 新增 `tmp/check_ui_wiring.py`：静态扫描 `MazeApp.java`，断言 12 个按钮、8 个标签全部挂进布局容器且按钮都绑了事件，
  用于防住"控件建了没上页面"这一类问题复发——全部 OK。
- `tmp/SmokeEndpoints.java` 16 项、`tmp/SmokeReflect.java` 22 项回归全绿（改名校验通过，后端行为未受影响）。
<!-- @anchor: log_016_end -->


<!-- @anchor: log_017 -->
# v1.13.0 - 枚举条数上限 + 盘面保存/载入 + 枚举结果文件化 (2026-10-24)

## 1. 枚举上限：把"要搜多久"变成显式参数
- `MazeSolver` 新增 `findAllPaths(int maxPaths)`：收录到上限即 `break` 并置 `lastEnumerationTruncated`；原无参版本保留为"按默认上限枚举"的重载，调用方无需改写。
- 新增 `solver_enum_limit` 锚点：`DEFAULT_MAX_PATHS = 2000`、`MIN_ENUM_LIMIT = 1`、`MAX_ENUM_LIMIT = 1_000_000` 是上限区间的唯一来源，`getLastEnumerationLimit()` / `isLastEnumerationTruncated()` 供界面回显。
- 界面新增「解数上限」输入框 + 枚举状态行（`app_enum_limit` / `app_enum_hint`）：非法输入红字提示并回落默认值（不静默改变搜索规模）；达上限时状态行标红提示"结果可能不完整，可调大上限后重试"。

## 2. 盘面保存 / 载入
- 新增 `MazeIO`（`io_*` 锚点）：盘面文本格式在此定义 —— `size` / `start` / `end` 头字段 + `walls` 段（每格一位十六进制 4 位掩码）+ `scores` 段（`#` 有 / `.` 无）。`loadBoard` 对尺寸区间、行列长度、掩码与得分点字符、起终点合法性逐项校验，任何不符抛 `IOException` 且不产出半成品。
- `MazeModel` 增补盘面读写的存取口：`getWallMask` / `setWallMask`、`setEndpoints`（一次性设定两端的版本，避免 `setStart` 与默认终点撞车）、`setScorePoint`。
- 界面新增「保存盘面」「载入盘面」按钮（`app_board_save` / `app_board_load` / `app_apply_board`）：文件对话框默认打开 `tmp/`，默认文件名带尺寸；载入后 `rows/cols` 缓存、模型/生成器/求解器、画布与编辑模式整体重建，**不重新生成**迷宫（载入即还原）。

## 3. 枚举结果落文件
- `savePathsToFile` 增加 `File` 重载并返回是否成功；头部写入 `# size / # start / # end / # limit / # truncated / # count`，每行为 `Path N (S步, P分): 方向编码`，可与盘面文件对照。
- 求解后自动写 `tmp/all_paths.txt`（失败在状态栏红字提示）；新增「导出解法」按钮（`app_export_paths`）把当前枚举结果另存到任意位置。
- 顶部区从四行改为五行：新增「枚举上限栏」，起终点回显与文件提示合并为「状态栏」（`app_top_layout` 描述同步更新）；`app_file_hint` 统一承载保存/载入/导出的成功与失败提示。

## 4. 验证
- `mvn compile` BUILD SUCCESS（6 个源文件，产物 `tmp/target/classes`），未编译测试。
- 新增 `tmp/SmokeIo.java` 18 项全绿：盘面往返（尺寸 / 全部墙壁掩码 / 得分点 / 起终点 / 外边界缺口 / BFS 步数一致）、非法盘面被拒（尺寸低于下限、墙壁行数不符）、空旷迷宫上限截断为 50、完美迷宫不被误判截断、结果文件头部与每行一条路径。
- 回归：`SmokeReflect` 22 项、`SmokeEndpoints` 16 项、`check_ui_wiring` 15 按钮 + 11 标签全部通过。
<!-- @anchor: log_017_end -->


<!-- @anchor: log_018 -->
# v1.14.0 - 抽样叠画枚举路径 (2026-10-24)

## 1. 抽样：把"枚举规模"和"画面规模"解耦
- `MazeSolver` 新增 `solver_sample` 锚点：`DEFAULT_SAMPLE_COUNT = 30`、`MIN_SAMPLE_COUNT = 1`、`MAX_SAMPLE_COUNT = 200` 是条数区间的唯一来源；`sampleIndices(total, count)` 做**等间隔确定性抽样**（期望条数先收敛到区间，首尾必取、严格升序、无重复）。
- 抽样不用随机：同一枚举结果每次重绘取的条数完全一致，画布不会在重绘间跳动。
- 自测发现的边界缺陷已修：原实现"期望条数 ≥ 总数就返回全部"，会绕过 `MAX_SAMPLE_COUNT` 把上万条全画出来；改为**先收敛再比较**，请求量再大也只叠画上限条数。

## 2. 视图：叠画三件事（去重 / 预折算 / 撤销）
- `MazeView` 新增 `view_overlay_fields`（6 色调色板 + 分桶线段 + 已叠画条数）与叠画存取口 `setOverlayPaths` / `clearOverlayPaths` / `getOverlayPathCount`。
- `setOverlayPaths` 一次完成：抽样路径坐标化 → 按"每段物理墙只描一次"去重 → 预折算成像素坐标并按调色板分桶；重绘只做描线，开销与枚举总条数无关。
- 新增 `view_draw_overlay`：绘制顺序为"迷宫 → 得分点 → 抽样叠画 → 悬停高亮 → 当前主解法"，主解法始终压在最上层。
- `rebuildCanvas` 会作废旧叠画（单元格尺寸变了，预折算的像素坐标作废）。

## 3. 页面：条数可控 + 状态回显 + 自动失效
- 右侧面板新增「叠画条数」输入框与「叠画抽样」开关（新锚点 `app_sample_ui`）：开关默认开，求解后自动按当前条数叠画；回车即按新条数重新叠画；关掉开关立即撤掉叠画。
- 新增 `app_sample_overlay`：`parseSampleCount` 校验区间（非法 → 红字提示并回落默认 30 条），`refreshSampleHint` 回显"已叠画抽样 X / 枚举 N 条"。
- `clearPathList` 升级为"作废一切派生物"：枚举结果、过滤映射与画布上的抽样叠画一起撤掉并重绘，避免编辑后画面残留旧解法的线条。

## 4. 验证
- `mvn compile` BUILD SUCCESS（6 个源文件，产物 `tmp/target/classes`），未编译测试。
- 新增 `tmp/SmokeSample.java` 20 项全绿：条数/首尾/升序、边界（总数 0、期望 0、负数、单条、超大请求被压到上限）、确定性、视图叠画条数与线段去重（2 条路径 4 段 → 3 段）、撤销与重建画布作废、抽样路径坐标首尾等于当前起终点。
- 回归：`SmokeReflect` 22 项、`SmokeEndpoints` 15 项、`SmokeIo` 17 项、`check_ui_wiring` 16 按钮 + 13 标签全部通过。
<!-- @anchor: log_018_end -->


<!-- @anchor: log_019 -->
# v1.15.0 - 修复盘面导入失败 + 长度排序 (2026-10-24)

## 1. 修复：导入 `maps/maze_12x12.maze` 必定失败
- **定位**：`MazeIO.loadBoard` 在解析任何行之前先做 `line.startsWith("#") → 跳过（当注释）`，而该文件 `scores` 段第 2 行是 `#....#..#...`（整行以 `#` 开头的数据行）。这一行被当注释吞掉后只剩 11 行得分点，`buildModel` 报"得分点行数应为 12，实际 11"，整个导入失败。
- **修复**：注释判定改为"`#` 后跟空白（或整行只有 `#`）"才算注释行，并把"矩阵段解析"放在注释判定之前——`scores` 段里 `#` 是数据、`# 说明文字` 才是注释。报错信息里补充提示该规则，避免下次误判。
- **顺带加固**：载入后对内部墙做**双向归并**（任一侧有墙即两侧都有，`io_normalize_walls`）。该示例文件里 (0,8)SOUTH 有墙而 (1,8)NORTH 无墙，视图按"单侧有墙就画线"会画出这条线，求解器却能从另一侧穿过去——归并后渲染与求解一致；外边界缺口仍原样保留。
- 示例盘面本身属用户素材，未做任何改动。

## 2. 新增：列表按长度排序
- 右侧过滤区新增「排序」下拉（默认顺序 / 步数升序 / 步数降序），与"步数范围筛选"同一区域：筛选决定"留哪些"，排序决定"按什么顺序看"。
- 新锚点 `app_sort_mode`：`sortRows` 只重排"展示行 + 真实下标"这对数据，`allPaths` 原始顺序与"路径 N"编号不动，选中项仍经 `filteredPathIndices` 回指真实路径；同长度按真实下标升序，重排可重复。
- `applyFilterAndPopulate` 与 `populatePathList` 改为统一流程"收集 (真实下标, 步数, 分数) → 排序 → 一次性填列表与统计"；未设过滤条件时统计文案回到"共 N 条路径"，切换排序只改顺序、不改筛选条件。

## 3. 验证
- `mvn compile` BUILD SUCCESS（6 个源文件，产物 `tmp/target/classes`），未编译测试。
- 新增 `tmp/SmokeImport.java` 12 项全绿：示例盘面载入 12x12、起终点 (5,0)→(5,11)、`#` 开头得分点行保留、得分点 8 个、内部墙双向一致、BFS 可解 18 步、枚举 2000 条、往返存盘逐格一致、`# 注释` 与 `#..` 数据行区分、非法文件仍被拒。
- 回归：`SmokeIo` 18 项、`SmokeReflect` 22 项、`SmokeEndpoints` 15 项、`SmokeSample` 19 项全绿。
- `check_ui_wiring.py` 扩展为 16 按钮 + 14 标签 + 1 下拉 + 排序常量/两条填充路径的静态检查，全绿。
<!-- @anchor: log_019_end -->


<!-- @anchor: log_020 -->
# v1.16.0 - 一键构建脚本 run.bat + 构建校验 (2026-10-24)

## 1. 新增：`run.bat` 一键编译运行
- 放在项目根部，双击即"`mvn compile` → `mvn javafx:run`"；先 `cd /d "%~dp0"` 定位脚本目录，双击时工作目录不确定也不会跑错项目。
- 四种模式：无参数/`run`（编译 + 启动界面）、`compile`（只编译主源码，不编译测试）、`clean`（只清理 `tmp/target`）、`help`；未知模式告警后按 `run` 处理。
- 前置检查 `java`/`mvn` 是否在 PATH（缺失即报错并给出安装提示），Java 非 17+ 只告警不拦截；`mvn` 是批处理封装，脚本统一用 `call mvn` 保证返回本脚本继续判断 `errorlevel`。
- 失败与结束都 `pause`，双开窗口能看到日志与结果；产物仍按约定落在 `tmp\target`，`clean` 只清该目录。
- 新锚点：`run_intro`、`run_setup`、`run_usage`、`run_compile`、`run_launch`、`run_clean`、`run_check_env`（批处理注释形式为 `REM @anchor:`）。

## 2. 验证
- `mvn compile` BUILD SUCCESS（6 个源文件，产物 `tmp\target\classes`），未编译测试，根目录无 `target/`。
- `run.bat` 与 `pom.xml` 的 `build.directory`、`javafx-maven-plugin.mainClass` 一致（`com.mazesolver.MazeApp`），脚本内不含自定义路径。

## 3. 文档
- `PROJECT.md` 的「构建与运行」补一键脚本用法与"只检测环境不自动安装"的约定。
<!-- @anchor: log_020_end -->


<!-- @anchor: log_021 -->
# v1.16.0 备注 - `run.bat` 的注释形式与版本检查 (2026-10-24)
- 锚点索引按扩展名过滤，`.bat` 不在范围内：`build_anchor_index` 后 `run.bat` 显示"未标注任何锚点"。故 `run.bat` 内改用 `REM ===== [setup] / [compile] / [launch] / [clean] / [check_env] =====` 分节注释，**log_020 中"新锚点 `run_*`"的表述作废**（文件开头已注明该原因）。
- 版本检查从 `java -version | findstr` 改为 `javac -version | findstr /R /C:"^javac 1[7-9]" /C:"^javac 2[0-9]"`：`java -version` 输出带内嵌引号（`version "17.0.6"`），批处理里要套 `\"` 多层转义、易错；`javac` 输出无引号，且本项目的 `mvn compile` 本来就要求 JDK。
<!-- @anchor: log_021_end -->


<!-- @anchor: log_022 -->
# v1.16.1 - 修复 run.bat 刷屏报错（`'mpile' 不是内部或外部命令`） (2026-10-24)

## 1. 现象与根因
- 现象：双击 `run.bat` 持续刷屏 `'mpile' 不是内部或外部命令`、`文件名、目录名或卷标语法不正确。`，不进入编译。
- 根因：批处理文件当时是**UTF-8 多字节内容 + LF 行尾**（由编辑工具写出）。`cmd.exe` 对 `goto` / `call :label` 的跳转是按**字节偏移**定位的，而含多字节字符（中文注释/提示）或缺少 `\r` 时偏移量与字符位置不一致，跳转便落到目标行**中间**：`call mvn -B compile` 被从第 3 个字符开始执行，命令名变成 `mpile`；偏移继续错位，`goto :done` 又跳回前面，于是无限刷屏。
- 与目标标签无关：脚本逻辑（四模式、`call mvn`、`errorlevel` 判断、`pause`）本身是正确的，纯属文件编码/行尾问题。

## 2. 修复
- `run.bat` 重写为**纯 ASCII 文本 + CRLF 行尾 + 无 BOM**：所有提示改为英文，去掉 `chcp 65001`（纯 ASCII 下无需切码页，`chcp` 本身在部分 cmd 版本还会引发批处理解析异常）。逻辑与模式、环境检查、`pause` 收尾保持不变。
- 文件头用 `REM` 写明这条硬约束（为什么不能出现中文/LF），避免后续再被编辑工具改回。
- 新增生成器 `tmp/make_run_bat.py`：从 `LINES` 以 `\r\n` 与 ASCII 编码写出 `run.bat`，并自检 **无 BOM / 无非 ASCII 字节 / 无孤立 LF / 以 CRLF 结尾 / 每个 `goto`·`call` 目标标签都存在**。改脚本应改生成器再重跑，而不是手写批处理。
- `PROJECT.md`「构建与运行」补同样约定（ASCII+CRLF 是脚本的硬性前提，附失败症状与生成器入口）。

## 3. 验证
- `python tmp/make_run_bat.py`：`bytes: 4146  lines: 130`，`all checks passed: ASCII / no BOM / CRLF only`；标签—跳转一一对应自检通过（`:usage`/`:do_compile`/`:do_run`/`:do_clean`/`:check_env`/`:env_ok`/`:launch_failed`/`:fail`/`:done`）。
- 说明：沙箱为 Linux，无法实机执行 `cmd.exe`；本次修复以"消除 cmd 字节偏移错乱的两个诱因（多字节、LF）"为目标，并以字节级自检兜住复发。
<!-- @anchor: log_022_end -->



<!-- @anchor: log_023 -->
# v1.17.0 - 得分点标记数字 + 路径数字串 + 序列条件筛选 (2026-10-24)

## 1. 需求
- 每个得分点可标记一个数字（1~9，可重复）；
- 枚举路线后记录"按经过顺序得到的数字串"；
- 结果支持按数字串条件筛选，表达式形如 `3>7`（数字 3 在 7 之前）、`"37"`（包含子串 37），条件之间用 `&` 联立。

## 2. 实现
- `MazeModel`：得分点由布尔矩阵升级为**标记数字矩阵**（0 = 无，1~9 = 标记）。`MIN/MAX_SCORE_DIGIT`、`DEFAULT_SCORE_DIGIT`、`SCORE_NONE` 是区间与默认值的唯一来源；新增 `getScoreDigit/setScoreDigit/applyScoreDigit`（区间外归一为无、起点/终点格拒收），旧布尔 API（`hasScorePoint/toggleScorePoint/addScorePoint`）保留为默认数字的兼容包装。
- `MazeSolver`：新增 `analyzePath` 一次遍历同时得出 `PathStats(分数, 数字串)`，`computePathDigits` 只做"按方向编码走一遍、按经过顺序拼标记数字"，串长恒等于分数；结果文件每行改为 `Path N (S步, P分, 序列 D): 方向编码`，未经过得分点写 `-`。
- 新增 `PathSequenceFilter`（独立文件、6 个锚点）：语法常量集中在 `filter_grammar`（`&`、`>`、`<`、`"`、语法提示文案），`parse` 按 `&` 切分后逐条件解析，`matches` 对数字串求值。关系式口径是"存在一对先后出现的数字"（不要求相邻），链式 `3>7>1` 等价于两条关系同时成立；任何语法问题在解析期抛 `IllegalArgumentException`，消息可直接展示。
- `MazeIO`：`scores` 段由 `#`/`.` 扩展为"`.` 无 / 1~9 标记该数字 / `#` 等价默认 1（兼容旧盘面）"，保存与载入无损往返。
- `MazeView`：得分点圆圈内绘制标记数字（格子太小只画圆不写字）；编辑时按"当前标记数字"落子（已是该数字则清除），悬停按"将新增/将清除"变色。
- `MazeApp`：编辑栏加「标记数字」下拉（取值由模型常量生成），过滤区加「序列条件」输入框 + 语法提示 + 回车即搜；列表行显示 `序列 D`，筛选统计行附"按哪些序列条件筛的"（来自 `PathSequenceFilter.describe()`），表达式非法时红字提示且不改动列表。

## 3. 本次修复（上轮中断处）
- `PathSequenceFilter.parseTerm` 的状态机把"上一个字符是否为数字"写成了每轮无条件取反的 `expectDigit`，导致**结尾判定取反**：任何带关系符的表达式（`3>7`）都被判为"以运算符结尾"而抛异常——即除单数字与引号子串外，全部关系式不可用。已重写为"`afterDigit` 标记上一个有效字符是否为数字"的两态机，并补上 `1~9` 提示文案引用模型常量。
- `tmp/SmokeDigits.java` 两处用例期望写错（`"1237"` 本身包含子串 `"37"`；`describe()` 是"一个条件一条描述"，`3>7>1` 只有一条）已按实际语义修正；`tmp/SmokeIo.java` 的结果文件行断言随之更新为 `分, 序列 `。

## 4. 验证
- `mvn compile` BUILD SUCCESS（7 个源文件，产物 `tmp/target/classes`）。
- `tmp/SmokeDigits.java` 47 项全绿（模型标记语义、数字串顺序、`PathStats` 口径一致、表达式 `>`/`<`/链式/子串/单数字/`&`/`3>3` 与 6 类非法表达式、盘面数字往返、旧 `#` 盘面、结果文件 `序列` 段）。
- 回归：`SmokeReflect` 22 项、`SmokeEndpoints` 15 项、`SmokeIo` 18 项、`SmokeImport` 11 项、`SmokeSample` 19 项、`check_ui_wiring`（16 按钮 + 17 标签 + 2 下拉）全部通过。
- 文档：`PROJECT.md` 增「得分点即标记数字」「筛选用表达式」两条架构决策，跨文件约定改写得分点/盘面文件/结果文件三条并新增序列条件语法，自测清单补 `SmokeDigits`。

<!-- @anchor: log_023_end -->

# v1.18.0 - 拆分过大的两个源文件（MazeApp / MazeView）(2026-10-24)

## 拆分
- `MazeView.java`（700 行）→ 6 个类：`MazeTheme`（尺寸分档表 / 叠画调色板 / 随 cellSize 缩放的线宽半径字号与命中阈值）、`MazeOverlay`（叠画线段去重 + 像素坐标预折算 + 分桶）、`MazeHitTester`（像素 → 墙壁段/单元格，无状态）、`MazeRenderer`（纯绘制，只吃单帧快照 `Frame`）、`MazeEditor`（编辑执行体：方向锁定、同一目标只处理一次、起终点拾取）、`MazeView`（画布生命周期 + 模式开关 + 鼠标事件 + 对外 API）。
- `MazeApp.java`（1191 行）→ 6 个类：`UiHint`（提示标签三级配色）、`InputParse`（"文本 → 区间整数"解析）、`PathListPanel`（右侧列表：过滤条件 / 排序 / 叠画设置 / 列表 / 统计，自包含数据流）、`MazeControlPanel`（顶部五行控件创建与布局，只搭结构）、`FileDialogs`（盘面保存 / 载入 / 导出三类对话框）、`MazeApp`（装配 + `wireActions` 集中接线 + 业务协调）。

## 兼容与清理
- 对外方法名基本不变：`MazeView` 的 `setOverlayPaths / clearOverlayPaths / getOverlayPathCount / rebuildCanvas / setModel / setSolutionPath / setOnEdited / setWallEditMode / setScoreEditMode / setEndpointEditMode / setScoreEditDigit` 与 `ENDPOINT_*` 常量全部保留（常数改为引用 `MazeEditor.PICK_*` 的别名）；新增 `getOverlaySegmentCount()` 供自测替代原先的私有字段反射。
- 删除 `MazeView` 中仅转发给 `MazeEditor` 的三个私有薄壳（`applyWallAction / applyScoreAction / applyEndpointAction`）：它们已无内部调用方，留着会腐坏；自测改为直接验证 `MazeEditor`，覆盖更贴近被测单元。
- 修正 `MazeOverlay` 初版残留的未使用字段 `bucketsView`。

## 自测更新
- `SmokeReflect`：命中检测改测 `MazeHitTester.findWall`（读 `WallHit` record 访问器），编辑改测 `MazeEditor.applyWallAction`；不再需要"忽略 draw 异常"的包装方法。
- `SmokeSample`：叠画断言改用 `MazeView.getOverlaySegmentCount()`，不再反射私有 `overlayBuckets`。
- `SmokeEndpoints`：拾取改测 `MazeEditor.applyEndpointAction`，并断言返回值（成功 true / 与另一端点重合 false）。
- `check_ui_wiring.py`：从"只扫 MazeApp.java"改为扫描 `MazeApp` + `MazeControlPanel` + `PathListPanel`；新增两条检查——面板暴露的每个控件访问器必须在主界面被绑定/读取、`PathListPanel` 的三个回调必须被主界面接管（覆盖本次"控件搬到新文件后忘记接线"的风险）。

## 验证
`mvn compile` BUILD SUCCESS（17 个源文件，产物 `tmp/target/classes`）；`SmokeReflect` 23 项、`SmokeEndpoints` 17 项、`SmokeSample` 20 项、`SmokeIo` 17 项、`SmokeImport` 11 项、`SmokeDigits` 47 项全绿；`check_ui_wiring` 16 按钮 + 18 标签 + 2 下拉 + 17 访问器全通过；`make_run_bat` 自检通过（`run.bat` 未变）。


## v1.18.0 备注 - 拆分后立刻做的一次死代码清理 (2026-10-24)

- 删除 `UiHint.restyle()` 与 `MazeEditor.getModel()`：拆分时顺手加的对偶访问器，实际无人调用。
- `PathListPanel.hasPaths()` 原本也无人调用，改为在主界面 `applySampleOverlay` / `refreshSampleHint` 里使用——"是否已枚举"统一问面板，主界面不再各自判断 `allPaths` 是否为空。
- `MazeView.getScoreEditDigit()` 保留：它是拆分前就存在的公开访问器，与 `setScoreEditDigit` 对称，属正常 API。
- 重新编译（17 源文件）与 `SmokeReflect` + `check_ui_wiring` 回归全绿。


# @anchor: log_024
# v1.18.0 评估 - 增量枚举（可续跑）可行性 (2026-10-24)

本轮只做评估，未改动任何代码逻辑。

## 结论
可行，且是零精度损失的增量：`findAllPaths` 的显式栈帧（行/列/下一方向下标）+ `visited` + `currentPath` 已包含恢复搜索的全部信息，把这三者从局部变量提升为字段即可"挂起 → 续跑"。

收益边界：完美迷宫枚举恒为 1 条（增量收益为 0，但会直接命中"已穷尽"标志返回）；增量只在多连通盘面（挖墙 / 载入非完美盘面）有意义。真正价值是让用户先拿少量结果看分布与筛选，再决定是否加深，避免一上来按 1e6 上限卡死。

## 关键实现要点
- 中断点必须精确：现在"收录满上限即 break"，栈顶仍是终点格且未回溯 → 续跑第一步要补一次回溯，否则重复收录。
- 结果列表必须对外返回**同一实例**（复制会让面板拿到过期快照）；作废时须同时清空求解器内集合，否则置 null 不再释放内存。
- 作废一致性双保险：`clearPathList()` 内统一 `resetEnumeration()`（一处覆盖生成/清空/改尺寸/载入/复位起终点/改墙改分点），再叠"盘面指纹校验"兜底，不一致自动从头。
- `PathListPanel` 必须加行缓存，只补算新增段，否则增量 k 次的总开销退化为 O(k²)。
- 首选内存瓶颈：每条路径 ≈ 32 + 4×步长 字节，1e6 条 × 300 步 ≈ 1.2GB → `MAX_ENUM_LIMIT = 1_000_000` 在常规堆下不现实，需下调或另立紧凑存储项。

## 产出
- `tmp/enum_incremental_plan.md`：现状事实、方案对比、锚点级改造清单、6 项风险与对策、4 个待确认决策点（上限语义 / 上限调小是否缩水 / 内存策略 / 是否并发）、5 步可编译验证的实施顺序。
# @anchor: log_024_end


# @anchor: log_025
# v1.19.0 第 1 步 - 增量枚举的求解器状态机化 (2026-10-25)

按 D1~D4 决策动工，本轮只完成"准备工作"：拆分步骤（`TODO.md`）并落地求解器核心。

## 决策固化
- D1：输入框 = 目标累计条数；「+」按钮每次追加 `ENUM_INCREMENT` 条目标；「求解」先判断目标是否达成。
- D2：目标调小不缩水、不重跑。
- D3：内存用**实测平均步长的预算闸门**（优于固定上限、改动小于紧凑数组），`MAX_ENUM_LIMIT` 由 1e6 下调到 2e5。
- D4：保持同步执行。

## 求解器改动（`MazeSolver`）
- 新增 `solver_incremental_state`：栈帧 / 访问标记 / 当前路径 / 已穷尽标志 / `resumeNeedsBacktrack` / 累计步数 / 盘面指纹 / 内存预算常量。
- 新增 `solver_continue`：`continueFindAllPaths(目标总数)` 为唯一核心——指纹校验（不一致自动作废）→ 按需初始化现场 → 目标已达成直取 → 内存收敛 → 补正回溯 → 显式栈续跑 → 达目标挂起 / 栈空置穷尽。
- 新增 `solver_reset`：`resetEnumeration`、`hasMorePaths`、`getCollectedPaths`、`getEstimatedMemoryBytes`。
- `findAllPaths()` / `findAllPaths(int)` 保留签名，语义改为"续跑到目标总数"，旧调用点零改动。

## 验证
- `mvn compile` BUILD SUCCESS（17 源文件）。
- 新增 `tmp/SmokeIncremental.java` 28 项全绿。其中最关键一项：9×9 全通盘面按 50 / 120 / 300 三段续跑，结果与一次跑满 300 条**逐条完全一致**；另有"每条路径不越界/不穿墙/不重格/落在终点"的结构校验，确认续跑没有污染 `currentPath`。
- 回归：`SmokeIo` 18 / `SmokeReflect` 23 / `SmokeDigits` 47 / `SmokeSample` 20 / `SmokeEndpoints` 17 / `SmokeImport` 11 / `check_ui_wiring` 全绿（`SmokeIo` 的"上限 50 条截断"与"完美迷宫 1 条不截断"在新语义下保持不变）。

## 状态
第 2~5 步（列表行缓存 / 界面「+」按钮与状态文案 / 结果文件语义 / 文档收尾与全量回归）见 `TODO.md`。
# @anchor: log_025_end


<!-- @anchor: update_record_anchor -->
<!-- [末尾锚点] 追加区：后续新记录统一插入本锚点之前，编号顺延 log_012 -->
