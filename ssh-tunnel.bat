@echo off
chcp 65001 > nul
title SSH 全端口反向代理隧道
:: ==========================================
:: 1. 基础配置区域
:: ==========================================
set SERVER_IP=115.190.230.175
set USERNAME=wuyong
:: 如果您的密钥不在默认位置，请取消下面一行的注释并修改路径
set SSH_KEY=C:\Users\37252\.ssh\id_rsa
:: ==========================================
:: 2. 端口映射列表 (注释必须单独一行，不能跟在 set 后面)
:: ==========================================
set PORT_MAPPINGS=
REM Nacos
set PORT_MAPPINGS=%PORT_MAPPINGS% -L 8846:localhost:8846
set PORT_MAPPINGS=%PORT_MAPPINGS% -L 8847:localhost:8847
set PORT_MAPPINGS=%PORT_MAPPINGS% -L 8848:localhost:8848
set PORT_MAPPINGS=%PORT_MAPPINGS% -L 9848:localhost:9848

:: ==========================================
:: 3. 密钥检测与命令构建
:: ==========================================
set SSH_CMD=ssh
REM 如果用户手动指定了密钥路径
if defined SSH_KEY (
    if exist "%SSH_KEY%" (
        echo [信息] 使用手动指定的密钥: %SSH_KEY%
        set SSH_CMD=ssh -i "%SSH_KEY%"
        goto :start_ssh
    )
)
REM 如果未手动指定，尝试读取默认路径
set DEFAULT_KEY=C:\Users\%USERNAME%\.ssh\id_rsa
if exist "%DEFAULT_KEY%" (
    echo [信息] 检测到默认 SSH 密钥，使用密钥认证...
    set SSH_CMD=ssh -i "%DEFAULT_KEY%"
    goto :start_ssh
)
REM 都没找到
echo [警告] 未检测到 SSH 私钥文件: %DEFAULT_KEY%！
echo [警告] 将使用**密码认证**，请准备好密码。
echo [提示] 如需使用密钥，请在脚本中修改 set SSH_KEY=...
echo.
:: ==========================================
:: 4. 启动
:: ==========================================
:start_ssh
echo ----------------------------------------
echo     SSH 隧道启动中...
echo     目标: %USERNAME%@%SERVER_IP%
echo ----------------------------------------
echo.
echo 按 Ctrl+C 停止代理
echo.
%SSH_CMD% %PORT_MAPPINGS% %USERNAME%@%SERVER_IP% -N
echo.
echo [信息] 隧道已断开
pause