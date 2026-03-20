#!/bin/bash
set -e

echo "========== [1/5] 安装 Java 21 =========="
if java -version 2>&1 | grep -q '"21'; then
    echo "Java 21 已安装，跳过"
else
    apt-get update
    apt-get install -y wget apt-transport-https gpg
    wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor | tee /etc/apt/trusted.gpg.d/adoptium.gpg > /dev/null
    echo "deb https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | tee /etc/apt/sources.list.d/adoptium.list
    apt-get update
    apt-get install -y temurin-21-jdk
    echo "Java 21 安装完成"
fi
java -version

echo "========== [2/5] 创建应用目录 =========="
mkdir -p /opt/feishu-bot
echo "目录已就绪: /opt/feishu-bot"

echo "========== [3/5] 检查 JAR 文件 =========="
if [ ! -f /opt/feishu-bot/feishu-smart-bot.jar ]; then
    echo "请先上传 JAR 文件到 /opt/feishu-bot/feishu-smart-bot.jar"
    exit 1
fi
echo "JAR 文件已就绪"

echo "========== [4/5] 配置 systemd 服务 =========="
cat > /etc/systemd/system/feishu-bot.service << 'SERVICEEOF'
[Unit]
Description=Feishu Smart Bot
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/feishu-bot
ExecStart=/usr/bin/java -jar /opt/feishu-bot/feishu-smart-bot.jar --server.port=8080
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
SERVICEEOF

systemctl daemon-reload
systemctl enable feishu-bot
systemctl restart feishu-bot
echo "服务已启动"

echo "========== [5/5] 验证服务 =========="
sleep 5
systemctl status feishu-bot --no-pager
echo ""
echo "========== 部署完成 =========="
echo "服务运行在: http://115.191.36.7:8080"
