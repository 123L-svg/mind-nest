# ============ 构建阶段 ============
FROM node:20-alpine AS build
WORKDIR /app

COPY package.json ./
RUN npm install --registry=https://registry.npmmirror.com

COPY . .
# 确保 vite 等二进制有执行权限（防止宿主环境权限位丢失导致 Permission denied）
RUN chmod +x node_modules/.bin/*
RUN npm run build

# ============ 运行阶段（Nginx） ============
FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]