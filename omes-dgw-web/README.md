# OMES DGW - 管理前端

Vue 3 + Vite + ant-design-vue。同一套前端同时支持：

| 模式 | 后端门户 | 实时 | 点位历史 | 性能指标 |
| --- | --- | --- | --- | --- |
| **边缘盒子** | `omes-dgw-portal-box` | Caffeine | 不可用 | 内存环形缓冲 |
| **中控电脑** | `omes-dgw-portal-center` | Redis | Influx | Influx（可回退内存） |

前端通过 `GET /system/portal` 识别能力，无需为模式单独打两套页面。

## 环境准备

```bash
cd omes-dgw-web
npm install
```

Node 建议 20+。默认登录账号见 `.env.development`（`admin` / `admin123`）。

## 本地开发（前后端分离）

先启动对应后端（仓库根目录），再起 Vite。开发态默认读 `.env.development`，API 直连 `http://127.0.0.1:12020/`。

### 1）连边缘盒子后端

```bash
# 终端 A：盒子门户（可跳过内嵌前端构建）
mvn -pl omes-dgw-portal-box -am spring-boot:run -Dskip.frontend=true

# 终端 B：前端
cd omes-dgw-web
npm run dev
```

浏览器：`http://localhost:12021`（见 `vite.config.js`）。  
此时点位「历史」入口会禁用；「性能」走内存缓存时间窗。

### 2）连中控电脑后端

需本机可用的 Redis、InfluxDB（配置见 `config/config-center.properties`）。

```bash
# 终端 A：中控门户
mvn -pl omes-dgw-portal-center -am spring-boot:run

# 终端 B：前端（同一套命令）
cd omes-dgw-web
npm run dev
```

浏览器同样：`http://localhost:12021`。  
点位历史与性能指标可走 Influx。

### 切换后端时

改 `.env.development` 中的 `VITE_API_BASE_URL` 指向实际后端地址后重启 `npm run dev`。两个门户默认端口都是 `12020`，一般只需停掉一个门户再启另一个。

## 构建

### 边缘盒子（内嵌进 JAR，同域无前缀）

```bash
npm run build:box
```

使用 `.env.box`（`VITE_API_BASE_URL=/`）。产物在 `dist/`，由 `omes-dgw-portal-box` 打包时拷入 `classpath:/static`。  
也可只打门户（会自动执行前端构建）：

```bash
# 仓库根目录
mvn -pl omes-dgw-portal-box -am package -DskipTests
java -jar omes-dgw-portal-box/target/omes-dgw-portal-box-1.0-SNAPSHOT.jar
# 浏览器打开 http://localhost:12020/
```

仅调后端、暂不重编前端：

```bash
mvn -pl omes-dgw-portal-box -am package -DskipTests -Dskip.frontend=true
```

### 中控电脑（Nginx 分离，`/api` 反代）

```bash
npm run build
```

使用 `.env.production`（`VITE_API_BASE_URL=/api`）。产物复制到 `omes-dgw-ops/build/packages/dist/` 再打前端镜像；Nginx 配置见 `nginx/default.conf`。

## 环境变量一览

| 文件 | 场景 | `VITE_API_BASE_URL` |
| --- | --- | --- |
| `.env.development` | 本地 Vite 开发 | `http://127.0.0.1:12020/` |
| `.env.box` | 盒子内嵌构建 | `/` |
| `.env.production` | 中控 Nginx 构建 | `/api` |

模板说明见 `.env.example`。
