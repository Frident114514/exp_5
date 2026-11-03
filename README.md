# 多人协作与冲突练习项目

这是单独准备的 C++ 项目，供发布到 GitHub 后由项目组练习多人协作。当前只包含共同起点。

## 本地编译

```sh
mkdir -p build
clang++ -std=c++17 -Wall -Wextra -pedantic main.cpp -o build/word_counter
printf 'one two three\n' | ./build/word_counter
```

预期输出中的单词数为 3。

## 发布后如何协作

1. 你在 GitHub 新建空仓库，使用 VS Code 的“发布到 GitHub”或在集成终端设置远程地址并推送 `main`。仓库可公开查看，但要让组员提交代码，应逐个邀请组员为 Collaborator；“公开仓库”本身不等于任何人都能直接推送。
2. 两位组员分别克隆仓库，从同一个 `main` 提交创建 `feature/prompt-a` 与 `feature/prompt-b`，各自只修改 `main.cpp` 中 `const std::string prompt = ...` 这一行，写成不同提示语并提交。
3. 第一位组员把分支推送到 GitHub，创建 Pull Request，合并到 `main`。第二位组员先在 VS Code 中拉取最新 `main`，再把 `main` 合并进自己的分支。这时同一行会出现冲突。
4. 在 VS Code 的合并编辑器里查看“当前更改”和“传入更改”，两人商量最终提示语，手动编辑并删除冲突标记。编译、运行确认后，在源代码管理里暂存 `main.cpp` 并完成合并提交，最后推送分支、创建 Pull Request。
5. 报告里的“真实多人冲突”截图应在组员实际操作时补拍；当前准备阶段不把本地模拟当作真实多人协作。

## 查看状态

VS Code 左侧“源代码管理”可查看更改与提交图表；集成终端可执行 `git status`、`git log --graph --oneline --all` 和 `git diff`。
