#! /bin/bash

## 将静态文件的nginx的子配置文件放到nginx/conf/domains/目录下
cp ../nginx/map.conf /export/servers/nginx/conf/domains/

## 启动nginx
mkdir -p /dev/shm/nginx_temp/client_body
sudo /export/servers/nginx/sbin/nginx -c /export/servers/nginx/conf/nginx.conf