#!/bin/bash
set -e

echo "========== [1/2] 安装 Nginx =========="
apt-get update
apt-get install -y nginx
systemctl enable nginx

echo "========== [2/2] 配置反向代理 =========="
cat > /etc/nginx/sites-available/feishu-bot << 'NGINXEOF'
server {
    listen 80;
    server_name _;

    location /feishu_callback {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
NGINXEOF

ln -sf /etc/nginx/sites-available/feishu-bot /etc/nginx/sites-enabled/feishu-bot
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl restart nginx
echo "Nginx 反向代理配置完成"
echo "HTTP 地址: http://115.191.36.7/feishu_callback"
