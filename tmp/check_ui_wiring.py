# @anchor: check_ui_wiring_intro
# 静态自检：扫描 MazeApp / MazeControlPanel / PathListPanel 三个界面源文件，确保
# 1) 每个 Button/ToggleButton/Label/ComboBox 都真正挂进了布局容器；
# 2) 面板暴露的每个控件访问器都被主界面绑定或读取（"控件建了没接线"同样算失败）；
# 3) 列表排序确实参与了"过滤填充"与"全量填充"两条路径。
import re
import os

HERE = os.path.dirname(os.path.abspath(__file__))
SRC_DIR = os.path.join(HERE, '..', 'src', 'main', 'java', 'com', 'mazesolver')

FILES = ('MazeApp.java', 'MazeControlPanel.java', 'PathListPanel.java')


def read(name):
    with open(os.path.join(SRC_DIR, name), encoding='utf-8') as f:
        return f.read()


app_text = read('MazeApp.java')
panel_text = read('MazeControlPanel.java')
list_text = read('PathListPanel.java')
# 合并文本：控件可能在面板里创建、在布局里挂载、在主界面绑定，跨文件判断才完整
text = app_text + '\n' + panel_text + '\n' + list_text


def collect(pattern):
    names = set()
    for m in re.finditer(pattern, text):
        name = m.group(1) or m.group(2)
        if name and name not in ('x', 'lbl'):
            names.add(name)
    return names


# 1. 按钮：字段赋值 或 局部变量声明
buttons = collect(r'(?:(\w+)\s*=\s*|\b(?:Button|ToggleButton)\s+(\w+)\s*=\s*)'
                  r'new\s+(?:Button|ToggleButton)\s*\(')
# 2. 标签：字段赋值 或 局部变量声明
labels = collect(r'(?:(\w+)\s*=\s*|\bLabel\s+(\w+)\s*=\s*)new\s+Label\s*\(')
# 3. 下拉框：字段赋值 或 局部变量声明（泛型写法一并覆盖）
combos = collect(r'(?:(\w+)\s*=\s*|\bComboBox(?:<[^>]*>)?\s+(\w+)\s*=\s*)new\s+ComboBox')


def wired(name):
    """是否作为子节点出现在布局构造函数 / 容器添加调用里"""
    as_child = r'new\s+(?:HBox|VBox|BorderPane|StackPane|ScrollPane)\s*\([^;]*\b' + re.escape(name) + r'\b'
    as_added = (r'(setCenter|setTop|setBottom|setRight|setLeft|setContent|setAll|add|addAll|'
                r'setGraphic|getChildren)\s*\([^;]*\b' + re.escape(name) + r'\b')
    return (re.search(as_child, text, re.S) is not None
            or re.search(as_added, text, re.S) is not None)


def body_of(source, method):
    """粗取方法体文本：从方法签名到下一个同级方法签名之前"""
    start = source.find(method)
    if start < 0:
        return ''
    nxt = source.find('\n    private ', start + len(method))
    nxt2 = source.find('\n    public ', start + len(method))
    ends = [x for x in (nxt, nxt2) if x > 0]
    end = min(ends) if ends else len(source)
    return source[start:end]


ok = True
for name in sorted(buttons):
    mounted = wired(name)
    ok = ok and mounted
    print(f'[{"OK  " if mounted else "FAIL"}] 按钮 {name:<18} mounted={mounted}')

for name in sorted(labels):
    mounted = wired(name)
    ok = ok and mounted
    print(f'[{"OK  " if mounted else "FAIL"}] 标签 {name:<18} mounted={mounted}')

for name in sorted(combos):
    mounted = wired(name)
    ok = ok and mounted
    print(f'[{"OK  " if mounted else "FAIL"}] 下拉 {name:<18} mounted={mounted}')

# 4. 面板暴露的控件访问器必须在主界面里被真正使用（按钮类还必须是 setOnAction）
getters = re.findall(r'public\s+(Button|ToggleButton|TextField|ComboBox<[^>]*>|Label)\s+'
                     r'(get\w+)\(\)', panel_text)
for kind, getter in sorted(set(getters)):
    call = f'.{getter}()'
    used = call in app_text
    if kind in ('Button', 'ToggleButton'):
        wired_action = re.search(re.escape(call) + r'\.setOnAction', app_text) is not None
    else:
        wired_action = used
    good = used and wired_action
    ok = ok and good
    print(f'[{"OK  " if good else "FAIL"}] 面板访问器 {getter:<22} 类型={kind:<12} 使用={used} 绑定={wired_action}')

# 5. 列表回调必须被主界面接管
for callback in ('setOnPathSelected', 'setOnOverlayToggle', 'setOnOverlayCountChanged'):
    used = f'{callback}(' in app_text
    ok = ok and used
    print(f'[{"OK  " if used else "FAIL"}] 列表面板回调 {callback}')

# 6. 长度排序：常量齐全，且"过滤填充"与"全量填充"两条路径都经过 sortRows，下标映射同步记录
for const in ('SORT_DEFAULT', 'SORT_STEPS_ASC', 'SORT_STEPS_DESC'):
    defined = re.search(r'String\s+' + const + r'\s*=', list_text) is not None
    ok = ok and defined
    print(f'[{"OK  " if defined else "FAIL"}] 排序常量 {const}')

for method, label in (('void applyFilterAndPopulate(', '过滤填充'),
                      ('void populatePathList(', '全量填充')):
    body = body_of(list_text, method)
    uses_sort = 'sortRows(' in body
    keeps_mapping = 'filteredPathIndices.add(' in body
    ok = ok and uses_sort and keeps_mapping
    print(f'[{"OK  " if uses_sort and keeps_mapping else "FAIL"}] '
          f'{label}: sortRows={uses_sort} 下标映射={keeps_mapping}')

print('按钮数:', len(buttons), ' 标签数:', len(labels), ' 下拉数:', len(combos),
      ' 面板访问器:', len(set(getters)))
print('结论:', '全部控件均可显示、已接线，排序已接入列表' if ok else '存在未挂进界面/未接线的控件，或排序未接入')
raise SystemExit(0 if ok else 1)
