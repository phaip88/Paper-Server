Paper [![Paper Build Status](https://img.shields.io/github/actions/workflow/status/phaip88/Paper-Server/build-jar.yml?branch=main)](https://github.com/phaip88/Paper-Server/actions)
==========

### 自动构建paper server.jar指南

1：fork本项目

2：在Actions菜单允许 `I understand my workflows, go ahead and enable them` 按钮

3：在`paper-server/src/main/java/io/papermc/paper/PaperBootstrap.java`文件里的`loadEnvVars`方法中修改需要的环境变量，不需要的留空，保存后Actions会自动构建

4：等待7分钟左右，在右侧的Release里下载server.jar文件

### TTYD Web终端配置

通过以下环境变量配置TTYD服务：

| 变量名 | 说明 | 默认值 |
|---|---|---|
| `TTYD_PORT` | TTYD服务监听端口 | `7681` |
| `TTYD_USER` | 登录用户名 | 空（不启用认证） |
| `TTYD_PASS` | 登录密码 | 空（不启用认证） |

示例（在 `.env` 文件中）：
```
TTYD_PORT=8080
TTYD_USER=admin
TTYD_PASS=mypassword
```
