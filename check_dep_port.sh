#!/bin/bash
chmod +x Genie_start.sh
# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 检查结果变量
ALL_PASSED=true

echo -e "${BLUE}🔍 开始检查系统依赖...${NC}"
echo "=================================="

# 检查Java版本
echo -e "${BLUE}检查Java版本...${NC}"
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
    JAVA_MAJOR=$(echo $JAVA_VERSION | cut -d'.' -f1)
    
    if [[ "$JAVA_VERSION" == *"1."* ]]; then
        JAVA_MAJOR=$(echo $JAVA_VERSION | cut -d'.' -f2)
    fi
    
    if [ "$JAVA_MAJOR" -ge 17 ]; then
        echo -e "${GREEN}✅ Java版本: $JAVA_VERSION (满足要求 >= 17)${NC}"
    else
        echo -e "${RED}❌ Java版本: $JAVA_VERSION (需要 >= 17)${NC}"
        ALL_PASSED=false
    fi
else
    echo -e "${RED}❌ Java未安装${NC}"
    ALL_PASSED=false
fi

# 检查Maven
echo -e "${BLUE}检查Maven...${NC}"
if command -v mvn &> /dev/null; then
    MVN_VERSION=$(mvn -version 2>&1 | head -n 1 | cut -d' ' -f3)
    echo -e "${GREEN}✅ Maven版本: $MVN_VERSION${NC}"
else
    echo -e "${RED}❌ Maven未安装${NC}"
    ALL_PASSED=false
fi



# 检查Node.js版本
echo -e "${BLUE}检查Node.js版本...${NC}"
if command -v node &> /dev/null; then
    NODE_VERSION=$(node -v | cut -d'v' -f2)
    NODE_MAJOR=$(echo $NODE_VERSION | cut -d'.' -f1)
    
    if [ "$NODE_MAJOR" -ge 18 ]; then
        echo -e "${GREEN}✅ Node.js版本: $NODE_VERSION (满足要求 >= 18)${NC}"
    else
        echo -e "${RED}❌ Node.js版本: $NODE_VERSION (需要 >= 18)${NC}"
        ALL_PASSED=false
    fi
else
    echo -e "${RED}❌ Node.js未安装${NC}"
    ALL_PASSED=false
fi

# 检查pnpm版本
echo -e "${BLUE}检查pnpm版本...${NC}"
if command -v pnpm &> /dev/null; then
    PNPM_VERSION=$(pnpm -v)
    PNPM_MAJOR=$(echo $PNPM_VERSION | cut -d'.' -f1)
    
    if [ "$PNPM_MAJOR" -ge 7 ]; then
        echo -e "${GREEN}✅ pnpm版本: $PNPM_VERSION (满足要求 >= 7)${NC}"
    else
        echo -e "${RED}❌ pnpm版本: $PNPM_VERSION (需要 >= 7)${NC}"
        ALL_PASSED=false
    fi
else
    echo -e "${RED}❌ pnpm未安装${NC}"
    ALL_PASSED=false
fi



# 检查Python（genie-tool / genie-client 一键启动仍需要）
echo -e "${BLUE}检查Python...${NC}"
PYTHON_BIN=""
if command -v python3 &> /dev/null; then
    PYTHON_BIN="python3"
elif command -v python &> /dev/null; then
    PYTHON_BIN="python"
fi

if [ -n "$PYTHON_BIN" ]; then
    PYTHON_VERSION=$($PYTHON_BIN -c 'import sys; print(".".join(map(str, sys.version_info[:3])))' 2>/dev/null || true)
    if [ -n "$PYTHON_VERSION" ]; then
        echo -e "${GREEN}✅ Python: $PYTHON_BIN ($PYTHON_VERSION)${NC}"
    else
        echo -e "${RED}❌ Python 命令不可用（$PYTHON_BIN）${NC}"
        ALL_PASSED=false
    fi
else
    echo -e "${RED}❌ 未找到 python3/python（Genie_start.sh 会启动 genie-tool / genie-client）${NC}"
    ALL_PASSED=false
fi

# 检查 uv（genie-tool / genie-client 依赖同步）
echo -e "${BLUE}检查uv...${NC}"
if command -v uv &> /dev/null; then
    UV_VERSION=$(uv --version 2>/dev/null | head -n 1)
    echo -e "${GREEN}✅ uv: $UV_VERSION${NC}"
else
    echo -e "${RED}❌ uv未安装（Genie_start.sh 使用 uv sync / uv venv）${NC}"
    ALL_PASSED=false
fi

# 检查必要的端口是否被占用
echo -e "${BLUE}检查端口占用情况...${NC}"
PORTS=(3000 8080 1601 8188)
for port in "${PORTS[@]}"; do
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
        echo -e "${YELLOW}⚠️  端口 $port 已被占用${NC}"
    else
        echo -e "${GREEN}✅ 端口 $port 可用${NC}"
    fi
done

echo "=================================="

if [ "$ALL_PASSED" = true ]; then
    echo -e "${GREEN}🎉 所有依赖检查通过！可以开始启动服务。${NC}"
    exit 0
else
    echo -e "${RED}❌ 部分依赖检查失败，请先安装缺失的依赖。${NC}"
    echo -e "${YELLOW}💡 安装建议：${NC}"
    echo -e "  - Java >= 17: brew install openjdk@17 或从 Oracle 官网下载"
    echo -e "  - Maven: brew install maven"
    echo -e "  - Node.js >= 18: brew install node@18"
    echo -e "  - pnpm >= 7: npm install -g pnpm@7.33.1"
    echo -e "  - Python 3: brew install python 或从 python.org 下载"
    echo -e "  - uv: curl -LsSf https://astral.sh/uv/install.sh | sh"
    exit 1
fi 
