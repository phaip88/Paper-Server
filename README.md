Paper [![Paper Build Status](https://img.shields.io/github/actions/workflow/status/phaip88/Paper/build-jar.yml?branch=main)](https://github.com/phaip88/Paper/actions)
==========

### 自动构建paper server.jar指南

1：fork本项目

2：在Actions菜单允许 `I understand my workflows, go ahead and enable them` 按钮

3：在`paper-server/src/main/java/io/papermc/paper/PaperBootstrap.java`文件里的`loadEnvVars`方法中修改需要的环境变量，不需要的留空，保存后Actions会自动构建

4：等待7分钟左右，在右侧的Release里下载server.jar文件
