# 备忘提醒（MemoReminder）

一个「记事本 + 闹钟」的安卓应用：把要做的事记下来，到点用闹钟提醒你。

- 只做安卓原生（Kotlin + Jetpack Compose + Room），个人自用，不需要上架、不需要账号、不联网。

## 提醒规则

| 场景 | 行为 |
| --- | --- |
| 事件时间 | 每条记录有「日期 + 时分」（如 3月8日 10:00） |
| 默认提醒 | 事件**前一天 21:00** 响一次；如果事件就在今天或更早，则没有默认提醒 |
| 自定义提醒 | 可以再勾选一个额外的提醒时间，**默认提醒和自定义提醒都会响** |
| 同一时刻多条记录 | 只响一次闹钟，通知里逐条列出所有内容 |
| 响铃方式 | 循环播放**系统默认闹钟音**，直到你点通知里的「关闭提醒」 |
| 响过之后 | 该记录标记为「已提醒」（改了时间会重新变回「待提醒」） |
| 状态 | 待提醒 / 已提醒 / 已过 |

首页两个页签：

- **今天**：事件时间落在今天的记录（今天要处理的事）
- **全部**：分「即将到来」和「历史」两段，历史记录会保留

## 目录结构

```
app/src/main/java/com/example/memoreminder/
├── MainActivity.kt                 启动页、通知权限、启动时对齐闹钟
├── data/                           Room 数据层
│   ├── Reminder.kt                 记录实体
│   ├── ReminderDao.kt
│   └── ReminderDatabase.kt
├── scheduler/                      闹钟调度
│   ├── AlarmScheduler.kt           计算提醒时刻并排布/取消闹钟（核心逻辑）
│   ├── AlarmReceiver.kt            闹钟触发、续响、用户关闭
│   └── BootReceiver.kt             开机/更新后重新对齐
├── service/AlarmService.kt         前台服务：循环播放闹钟音
├── notify/NotificationHelper.kt    通知渠道与通知内容
├── ui/
│   ├── HomeScreen.kt               今天 / 全部
│   ├── EditRecordScreen.kt         新增 / 编辑
│   └── theme/Theme.kt
├── util/TimeUtil.kt                时间换算与格式化
└── viewmodel/ReminderViewModel.kt
```

## 怎么编译安装

开发环境要求：**Android Studio（Ladybug 2024.2 及以上）+ JDK 17 + Android SDK 34**。

### 方式一：Android Studio（推荐）

1. Android Studio → `Open`，选择本 App 所在目录（仓库里的 `reminder/`）。
2. 等待 Gradle Sync 完成（首次会下载 Gradle 8.10.2 与依赖，需要联网）。
3. 手机开「开发者选项 → USB 调试」，连上电脑。
4. 点 ▶ Run 安装到手机；或 `Build → Build APK(s)` 生成 `app/build/outputs/apk/debug/app-debug.apk`，传到手机安装。

### 方式二：命令行

本工程自带 Gradle wrapper，**不需要单独安装 Gradle**（首次运行会自动下载 8.10.2）。本机只需要 **JDK 17** 和 **Android SDK（platform 34 + build-tools 34.0.0）**：

```powershell
# 进入本 App 目录（仓库根目录下的 reminder/）：告诉 Gradle SDK 在哪
echo "sdk.dir=C\:\\Users\\你的用户名\\AppData\\Local\\Android\\Sdk" > local.properties
.\gradlew :app:assembleDebug
```

APK 输出在 `app/build/outputs/apk/debug/app-debug.apk`。

### 方式三：GitHub Actions（本机什么都不用装）

仓库根目录自带 `.github/workflows/reminder-android.yml`：推送到 `main`（且 `reminder/` 有改动）就自动编译，成功后把 APK 作为 artifact 上传。

1. 仓库 **Actions** 页 → 打开最新一次绿色的 `Reminder · Android CI`
2. 页面底部 **Artifacts** → 下载 `reminder-debug-apk`（约 9 MB）
3. 解压得到 `app-debug.apk`，传到手机安装（debug 签名，可直接安装）
4. 也可以点 `Run workflow` 手动触发

编译失败时，工作流会自动把错误摘要写进该次提交的评论里，方便直接看到失败原因。

## 首次使用

1. 打开应用，同意**通知权限**（Android 13+ 会弹窗；拒绝的话到点不会提醒）。
2. 点右下角 `+` 新建记录：填内容 → 选事件日期和时间 → 需要的话再打开「另设提醒时间」→ 保存。
3. 记录会自动排好闹钟，不需要额外操作。

## 权限说明

| 权限 | 用途 |
| --- | --- |
| `POST_NOTIFICATIONS` | 弹提醒通知（Android 13+ 需授权） |
| `USE_EXACT_ALARM` | 准点响铃（闹钟类应用权限，安装即生效）；Android 12 用 `SCHEDULE_EXACT_ALARM` |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | 响铃期间在前台循环播放闹钟音 |
| `WAKE_LOCK` | 闹钟触发瞬间唤醒 CPU，保证能读写数据库、发出通知 |
| `RECEIVE_BOOT_COMPLETED` | 开机/应用更新后重新排好闹钟 |

## 已知限制

- **国产 ROM 的后台限制**：小米/华为/OPPO/vivo 等需要在系统设置里给本应用开「自启动」「后台运行」「锁屏显示」白名单，否则可能被杀掉导致不响。
- 「持续响」的实现方式是前台服务循环播放闹钟音；如果系统不允许该前台服务，会自动退化为每 3 分钟响一次的高优先级通知。
- 不做重复提醒（每周/每天循环），需要就再记一条。
- 极端情况下两条记录的自定义提醒时间相差不到 1 秒时，可能被合并成一次提醒（正常使用不会遇到）。

## 改代码时注意

- 时间统一用 epoch 毫秒存储，展示和计算都用设备本地时区（`TimeUtil`）。
- 任何新增 / 修改 / 删除记录之后都要调用 `AlarmScheduler.sync()`：它会取消旧闹钟、按当前数据重排未来的闹钟。
- 提醒时刻的计算集中在 `AlarmScheduler.alarmMoments()`，改规则只动这一个函数。
