#!/bin/bash

# 检查package.json是否存在
if [ ! -f package.json ]; then
  echo "package.json not found!"
  exit 1
fi

# 获取当前版本号
current_version=$(jq -r '.version' package.json)
if [ -z "$current_version" ]; then
  echo "Failed to get current version from package.json"
  exit 1
fi

# 分割版本号
IFS='.' read -r -a version_parts <<< "$current_version"
if [ ${#version_parts[@]} -ne 3 ]; then
  echo "Version format is not valid. Expected format: x.y.z"
  exit 1
fi

# 版本号末位加1
version_parts[2]=$((version_parts[2] + 1))
new_version="${version_parts[0]}.${version_parts[1]}.${version_parts[2]}"

# 更新package.json中的版本号
jq --arg new_version "$new_version" '.version = $new_version' package.json > tmp.json && mv tmp.json package.json

# 提交到git
git add package.json
git commit -m "feat: $new_version"
git push

# 执行pnpm build
pnpm i
pnpm build

# 检查dist目录是否存在
if [ ! -d dist ]; then
  echo "dist directory not found!"
  exit 1
fi

# 压缩dist目录
cd dist
zip -r "../${new_version}.zip" ./*

echo "Done! New version is $new_version"