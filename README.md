# apps

我的个人 App 合集。每个 App 一个独立子目录，各自带独立的构建配置和 CI workflow。

| 目录 | 应用 | 技术栈 | 下载 APK |
| --- | --- | --- | --- |
| [`reminder/`](./reminder) | **备忘提醒**：记事本 + 闹钟，事件前一天 21:00 提醒 | Android · Kotlin · Jetpack Compose · Room | [reminder-debug.apk](https://github.com/bingyenufe/apps/releases/download/reminder-latest/reminder-debug.apk) |
| （待添加） | | | |

## 目录约定

```
apps/
├── .github/workflows/          # CI 统一放仓库根目录（GitHub 只认这个位置）
│   └── reminder-android.yml    # 一个 App 一个 workflow
├── .gitattributes              # 换行符规则（repo 级，作用于所有子目录）
├── .gitignore
├── README.md                   # 本文件：App 索引
└── reminder/                   # 一个 App 一个完整工程
    ├── app/
    ├── build.gradle.kts
    ├── gradlew / gradlew.bat / gradle/wrapper/
    ├── settings.gradle.kts
    └── README.md               # 该 App 自己的说明
```

## 怎么加一个新 App

1. 在仓库根目录新建子目录，例如 `my-app/`，把完整工程放进去（自带 gradle wrapper 最省事）
2. 在 `.github/workflows/` 新建一个 workflow，参考 `reminder-android.yml`，改这几处即可：
   - `on.push.paths` / `on.pull_request.paths` → `['my-app/**', '.github/workflows/my-app-*.yml']`
   - `defaults.run.working-directory` → `my-app`
   - `gradle/actions/setup-gradle` 的 `build-root-directory` → `my-app`
   - artifact / 报告路径加上 `my-app/` 前缀，artifact 名字也换一个
3. 在上面表格里登记一行

> 注意：workflow **不能**放在子目录的 `.github/workflows/` 里，GitHub 不会执行它。

## CI 说明

- 推送到 `main` 自动触发，但只在该 App 自己的文件有改动时才跑（`paths` 过滤）
- 也可以到 Actions 页手动 `Run workflow`
- 编译成功：APK 既作为 artifact 上传（Actions 页底部，需登录+解压），也会自动发布到 **Releases**（免登录直链下载）
- 编译失败：错误摘要会自动写进该次提交的评论里，方便直接看到原因
