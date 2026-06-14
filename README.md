# 🌱 智慧大棚管理系统

基于 Spring Boot 的智慧农业大棚监控系统，通过 MQTT 协议接入华为云 IoTDA 平台，实时采集并可视化展示温度、湿度、光照、CO2、土壤湿度、土壤 pH 值等传感器数据。

---

## 🛠 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.x |
| ORM | MyBatis Plus |
| 数据库 | MySQL 8.0 |
| 消息协议 | MQTT (Eclipse Paho) |
| IoT 平台 | 华为云 IoTDA |
| 对象存储 | MinIO（图片存储） |
| AI 推理 | ONNX Runtime（YOLOv8 作物分类） |
| 前端 | 原生 HTML/CSS/JS + Chart.js |
| 构建工具 | Maven |

---

## 📁 项目结构

```
demo/
├── src/main/java/com/greenhouse/
│   ├── DemoApplication.java    # Spring Boot 启动类
│   ├── common/                 # 通用类（统一响应 Result）
│   ├── config/                 # 配置类（MyBatis Plus、MQTT、报警阈值、自动控制、MinIO）
│   ├── controller/             # REST 接口
│   ├── entity/                 # 数据库实体
│   ├── mapper/                 # MyBatis Mapper
│   ├── mqtt/                   # MQTT 消息监听与下发
│   ├── onnx/                   # ONNX 模型推理（YOLOv8 作物分类）
│   │   ├── CropClassifierONNX.java       # 推理核心
│   │   ├── controller/ClassifierController.java  # 分类 REST 接口
│   │   └── service/ClassifierService.java        # 分类服务
│   ├── service/                # 业务逻辑
│   │   ├── MinioService.java   # MinIO 图片拉取服务
│   │   └── ...
│   └── service/impl/           # 业务逻辑实现
├── src/main/resources/
│   ├── application.yaml        # ⚠️ 核心配置文件
│   ├── init.sql                # 数据库初始化脚本
│   ├── models/best.onnx        # YOLOv8 作物分类模型（14类）
│   └── static/index.html       # 前端页面
├── src/test/java/com/greenhouse/
│   ├── OnnxClassifierTest.java # ONNX 分类测试（含 MinIO 联动）
│   └── MinioOnnxRunner.java    # 🆕 MinIO+YOLO 独立测试启动器
├── test/result/                # 🆕 测试结果输出目录（自动生成）
└── pom.xml
```

---

## 🚀 快速开始

### 1. 环境要求

- JDK 17+
- MySQL 8.0+
- Maven 3.6+
- 华为云 IoTDA 服务（已开通）

### 2. 数据库初始化

```bash
# 在 MySQL 中执行初始化脚本
mysql -u root -p < src/main/resources/init.sql
```

### 3. 修改配置文件 ⚠️

**`src/main/resources/application.yaml`** — 以下**所有**配置项都需要替换为你自己的：

```yaml
spring:
  datasource:          # ⚠️ 改：你自己的 MySQL
    url: jdbc:mysql://你的IP:3306/greenhouse?...
    username: 你的用户名
    password: 你的密码

  mqtt:                # ⚠️ 改：华为云 IoTDA 设备接入信息
    host: 你的IoTDA接入地址.st1.iotda-app.cn-east-3.myhuaweicloud.com
    port: 8883
    access_key: 你的设备接入Key
    access_code: 你的设备接入Code
    instance_id: 你的实例ID（可为空）

    # IoTDA 消息主题（通常不需要改）
    subscribe_topic: msg/up
    subscribe_topic_down: /message/down
    qos: 0

    # ⚠️ 改：华为云账号 AK/SK（用于下发控制指令）
    region_id: cn-east-3
    ak: 你的AccessKey
    sk: 你的SecretKey
    project_id: 你的项目ID

    # ⚠️ 改：你要控制的设备ID
    device_id: 你的设备ID
```

### 4. 启动应用

```bash
cd demo
./mvnw spring-boot:run
# Windows: mvnw.cmd spring-boot:run
```

### 5. 访问

打开浏览器 → **http://localhost:8080**

---

## 🔌 各配置项说明（哪些要换成你自己的）

### 必须修改的部分 ⚠️

| 配置项 | 说明 | 如何获取 |
|--------|------|----------|
| `spring.datasource.*` | MySQL 连接信息 | 本地或云服务器安装 MySQL |
| `spring.mqtt.host` | 华为云 IoTDA MQTT 接入地址 | IoTDA 控制台 → 设备接入 → 总览 → `MQTT 接入地址` |
| `spring.mqtt.port` | MQTT 端口 | 固定 `8883`（SSL） |
| `spring.mqtt.access_key` | 设备接入凭证 Key | IoTDA 控制台 → 设备 → 我的设备 → `设备详情` |
| `spring.mqtt.access_code` | 设备接入凭证 Code | 同上，创建设备时设置 |
| `spring.mqtt.instance_id` | 实例 ID | IoTDA 总览页，新实例通常为空字符串 |
| `spring.mqtt.ak` / `sk` | 华为云账号 AK/SK | 华为云控制台 → 我的凭证 → 访问密钥 |
| `spring.mqtt.project_id` | 华为云项目 ID | 华为云控制台 → 我的凭证 → 项目列表 |
| `spring.mqtt.device_id` | 设备 ID | IoTDA 控制台 → 设备 → 设备列表中复制 |

### 云服务的作用分工

| 配置 | 用途 | 数据流向 |
|------|------|----------|
| `access_key` + `access_code` | MQTT 设备身份认证，**接收**传感器上报数据 | IoT 设备 → IoTDA → 你的应用 |
| `ak` + `sk` + `project_id` | 华为云 API 签名认证，**下发**控制指令到设备 | 你的应用 → IoTDA → IoT 设备 |
| `subscribe_topic: msg/up` | 订阅设备上行消息主题 | 接收设备主动上报 |
| `subscribe_topic_down: /message/down` | 订阅属性上报转发主题 | 接收定时属性上报 |

> 💡 **理解关键**：`access_key/access_code` 是设备侧认证（收数据），`ak/sk` 是应用侧认证（发指令）。两套凭证各司其职。

---

## 🏗 系统架构

```
┌─────────────┐    MQTT(SSL:8883)    ┌──────────────┐
│  IoT 传感器  │ ──────────────────→  │  华为云 IoTDA  │
│  (真实设备)  │                      │  (消息中间件)  │
└─────────────┘                      └──────┬───────┘
                                           │ MQTT 订阅
                                           ▼
┌─────────────┐                    ┌────────────────────┐
│  MinIO      │ ←── 拉取图片 ──── │  Spring Boot 应用   │
│  对象存储   │                    │                    │
│  (图片仓库) │                    │  MqttMessageListener│
└─────────────┘                    │    ↓ 解析消息       │
                                   │  SensorDataService  │
                                   │    ↓ 入库           │
                                   │  AlertCheckService  │ ← 报警检测
                                   │  AutoControlService │ ← 🆕 自动控制
                                   │    ↓ 下发指令       │
                                   │  MqttSendUtil       │
                                   │                     │
                                   │  ┌────────────────┐ │
                                   │  │ ONNX 分类模块   │ │
                                   │  │ MinioService    │ │ ← 从 MinIO 拉图
                                   │  │ ClassifierService│ │ ← YOLO 推理
                                   │  └────────────────┘ │
                                   └────────┬───────────┘
                                            │ JDBC(3306)
                                            ▼
                                   ┌────────────────────┐
                                   │    MySQL 数据库     │
                                   │  gh_sensor_data     │
                                   │  gh_alert_log       │
                                   │  gh_device          │
                                   │  gh_greenhouse      │
                                   └────────┬───────────┘
                                            │ REST API(8080)
                                            ▼
                                   ┌────────────────────┐
                                   │   浏览器前端        │
                                   │  (Chart.js 图表)    │
                                   └────────────────────┘
```

### 数据流转

1. **传感器上报** → IoT 设备通过 MQTT 上报属性到华为云 IoTDA
2. **应用接收** → `MqttMessageListener` 订阅 IoTDA 主题，解析传感器数据
3. **入库存储** → 解析后的数据写入 `gh_sensor_data` 表
4. **报警检测** → `AlertCheckService` 逐项检测阈值，超标自动生成报警记录
5. **🆕 自动控制** → `AutoControlService` 检测温度/光照/土壤湿度，自动开关风机/补光灯/灌溉机
6. **指令下发** → 手动或自动触发 → `MqttSendUtil` 通过华为云 API 向设备下发控制指令
7. **☁️ 图片分类** → 前端/定时任务触发 → `MinioService` 从 MinIO 拉取图片 → `ClassifierService` 调用 ONNX YOLO 模型推理 → 返回生长周期识别结果

### 两种 MQTT 消息处理

| 消息来源 | 主题 | 解析方法 | 典型场景 |
|----------|------|----------|----------|
| 设备上行消息 | `msg/up` | `parseSensorData()` | 设备主动上报 |
| 属性上报转发 | `/message/down` | `parsePropertyReport()` | IoTDA 规则引擎转发 |

---

## 📡 API 接口文档

> 所有接口统一响应格式：`{ "code": 200, "message": "成功", "data": {...} }`

### 统一响应格式

```json
{
  "code": 200,        // 状态码：200 成功，500 失败
  "message": "成功",   // 提示信息
  "data": { ... }     // 返回数据（可为对象、数组、字符串或 null）
}
```

### 分页响应格式

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "records": [ ... ],   // 当前页数据列表
    "total": 25,          // 总记录数
    "size": 10,           // 每页条数
    "current": 1,         // 当前页码
    "pages": 3            // 总页数
  }
}
```

---

### 大棚管理 `/api/greenhouse`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/greenhouse/list` | 分页查询大棚 |
| GET | `/api/greenhouse/{id}` | 查询单个大棚 |
| POST | `/api/greenhouse` | 新增大棚 |
| PUT | `/api/greenhouse` | 修改大棚 |
| DELETE | `/api/greenhouse/{id}` | 删除大棚（逻辑删除） |

<details>
<summary><b>GET /api/greenhouse/list</b> — 分页查询</summary>

**请求参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | int | 否 | 页码，默认 1 |
| size | int | 否 | 每页条数，默认 10 |
| name | string | 否 | 大棚名称模糊搜索 |

**响应示例：**
```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "records": [
      {
        "id": 1,
        "name": "一号大棚",
        "location": "A区-东侧",
        "area": 200.0,
        "description": "番茄种植大棚",
        "status": 1,
        "createTime": "2026-06-01T10:00:00",
        "updateTime": "2026-06-10T14:30:00"
      }
    ],
    "total": 5, "size": 10, "current": 1, "pages": 1
  }
}
```
</details>

<details>
<summary><b>POST /api/greenhouse</b> — 新增大棚</summary>

**请求体：**
```json
{
  "name": "三号大棚",
  "location": "B区-南侧",
  "area": 150.0,
  "description": "黄瓜种植大棚",
  "status": 1
}
```

**字段说明：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | string | 是 | 大棚名称 |
| location | string | 否 | 位置描述 |
| area | double | 否 | 面积（平方米） |
| description | string | 否 | 描述 |
| status | int | 否 | 状态：0-停用，1-运行中，默认 1 |

**响应示例：** `{ "code": 200, "message": "成功", "data": "新增成功" }`
</details>

<details>
<summary><b>PUT /api/greenhouse</b> — 修改大棚</summary>

**请求体：** 与新增相同，但必须包含 `id` 字段
```json
{
  "id": 1,
  "name": "一号大棚(已改造)",
  "location": "A区-东侧",
  "area": 250.0,
  "description": "扩建后的番茄大棚",
  "status": 1
}
```
</details>

<details>
<summary><b>DELETE /api/greenhouse/{id}</b> — 删除大棚</summary>

逻辑删除（`deleted=1`），数据不物理删除。
</details>

---

### 传感器数据 `/api/sensor-data`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/sensor-data/list` | 分页查询传感器数据 |
| GET | `/api/sensor-data/chart` | 📈 图表数据（多棚多指标对比） |
| GET | `/api/sensor-data/latest/{greenhouseId}` | 查询大棚最新一条数据 |
| GET | `/api/sensor-data/{id}` | 查询单条数据 |
| POST | `/api/sensor-data` | 新增传感器数据 |
| PUT | `/api/sensor-data` | 修改传感器数据 |
| DELETE | `/api/sensor-data/{id}` | 删除传感器数据 |

<details>
<summary><b>GET /api/sensor-data/list</b> — 分页查询</summary>

**请求参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | int | 否 | 页码，默认 1 |
| size | int | 否 | 每页条数，默认 10 |
| greenhouseId | long | 否 | 按大棚ID筛选 |

**响应示例：**
```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "records": [
      {
        "id": 101,
        "greenhouseId": 1,
        "temperature": 26.5,
        "humidity": 68.0,
        "lightIntensity": 35000.0,
        "co2Concentration": 450.0,
        "soilMoisture": 55.0,
        "soilPh": 6.8,
        "recordTime": "2026-06-10T14:00:00",
        "createTime": "2026-06-10T14:00:01"
      }
    ],
    "total": 1200, "size": 10, "current": 1, "pages": 120
  }
}
```
</details>

<details>
<summary><b>GET /api/sensor-data/chart</b> — 📈 图表数据</summary>

**请求参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| metric | string | 是 | 指标：temperature / humidity / lightIntensity / co2Concentration / soilMoisture / soilPh |
| greenhouseIds | string | 是 | 大棚ID列表，逗号分隔，如 `1,2,3` |
| startTime | string | 否 | 开始时间，格式 `2026-06-05T00:00:00` |
| endTime | string | 否 | 结束时间，格式 `2026-06-08T23:59:59` |

**响应示例：**
```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "metric": "temperature",
    "metricLabel": "温度 (℃)",
    "series": [
      {
        "greenhouseId": 1,
        "greenhouseName": "一号大棚",
        "dataPoints": [
          { "recordTime": "2026-06-10T08:00:00", "value": 24.5 },
          { "recordTime": "2026-06-10T09:00:00", "value": 26.0 }
        ]
      },
      {
        "greenhouseId": 2,
        "greenhouseName": "二号大棚",
        "dataPoints": [
          { "recordTime": "2026-06-10T08:00:00", "value": 23.0 },
          { "recordTime": "2026-06-10T09:00:00", "value": 25.5 }
        ]
      }
    ]
  }
}
```
</details>

<details>
<summary><b>POST /api/sensor-data</b> — 新增传感器数据</summary>

**请求体：**
```json
{
  "greenhouseId": 1,
  "temperature": 26.5,
  "humidity": 68.0,
  "lightIntensity": 35000.0,
  "co2Concentration": 450.0,
  "soilMoisture": 55.0,
  "soilPh": 6.8,
  "recordTime": "2026-06-10T14:00:00"
}
```

**字段说明：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| greenhouseId | long | 是 | 所属大棚ID |
| temperature | double | 否 | 温度（℃） |
| humidity | double | 否 | 空气湿度（%） |
| lightIntensity | double | 否 | 光照强度（Lux） |
| co2Concentration | double | 否 | CO2浓度（ppm） |
| soilMoisture | double | 否 | 土壤湿度（%） |
| soilPh | double | 否 | 土壤pH值 |
| recordTime | string | 否 | 记录时间，不填则使用当前时间 |
</details>

---

### 设备管理 `/api/device`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/device/list` | 分页查询设备 |
| GET | `/api/device/{id}` | 查询单个设备 |
| POST | `/api/device` | 新增设备 |
| PUT | `/api/device` | 修改设备 |
| PUT | `/api/device/{id}/toggle` | 🔌 开关设备（下发 MQTT 指令） |
| DELETE | `/api/device/{id}` | 删除设备 |

<details>
<summary><b>GET /api/device/list</b> — 分页查询</summary>

**请求参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | int | 否 | 页码，默认 1 |
| size | int | 否 | 每页条数，默认 10 |
| greenhouseId | long | 否 | 按大棚ID筛选 |
| deviceType | string | 否 | 按设备类型筛选：FAN / PUMP / LIGHT / CURTAIN |
</details>

<details>
<summary><b>POST /api/device</b> — 新增设备</summary>

**请求体：**
```json
{
  "greenhouseId": 1,
  "deviceName": "风机-1号",
  "deviceType": "FAN",
  "status": 0
}
```

**字段说明：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| greenhouseId | long | 是 | 所属大棚ID |
| deviceName | string | 是 | 设备名称 |
| deviceType | string | 是 | 设备类型：FAN-风机 / PUMP-水泵 / LIGHT-补光灯 / CURTAIN-卷帘 |
| status | int | 否 | 初始状态：0-关闭（默认），1-开启 |
</details>

<details>
<summary><b>PUT /api/device/{id}/toggle</b> — 🔌 开关设备</summary>

切换设备开关状态并通过华为云 IoTDA 下发 MQTT 指令到真实设备。

**指令映射：**

| 设备类型 | MQTT 指令名 | 下发参数 |
|----------|------------|----------|
| FAN（风机） | 电机控制 | `{"motor":"ON"/"OFF"}` |
| LIGHT（补光灯） | 紫光灯控制 | `{"light":"ON"/"OFF"}` |
| PUMP（水泵） | 水泵控制 | `{"pump":"ON"/"OFF"}` |
| CURTAIN（卷帘） | 卷帘控制 | `{"curtain":"ON"/"OFF"}` |

**响应示例：** `{ "code": 200, "message": "成功", "data": "设备已开启，指令已下发" }`
</details>

---

### 报警记录 `/api/alert-log`

> ⚠️ 报警由系统根据传感器数据**自动检测生成**，不提供手动新增接口。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/alert-log/list` | 分页查询报警 |
| GET | `/api/alert-log/{id}` | 查询单条报警 |
| PUT | `/api/alert-log` | 修改报警 |
| PUT | `/api/alert-log/{id}/handle` | ✅ 标记报警已处理 |
| DELETE | `/api/alert-log/{id}` | 删除报警 |

<details>
<summary><b>GET /api/alert-log/list</b> — 分页查询</summary>

**请求参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | int | 否 | 页码，默认 1 |
| size | int | 否 | 每页条数，默认 10 |
| greenhouseId | long | 否 | 按大棚ID筛选 |
| alertType | string | 否 | 按报警类型筛选 |
| status | int | 否 | 按状态筛选：0-未处理，1-已处理 |

**响应示例：**
```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "records": [
      {
        "id": 10,
        "greenhouseId": 1,
        "alertType": "TEMP_HIGH",
        "message": "一号大棚温度过高：38.5℃（阈值：35.0℃）",
        "status": 0,
        "createTime": "2026-06-10T14:30:00",
        "handleTime": null
      }
    ],
    "total": 3, "size": 10, "current": 1, "pages": 1
  }
}
```
</details>

<details>
<summary><b>PUT /api/alert-log/{id}/handle</b> — ✅ 处理报警</summary>

将报警状态改为"已处理"，自动设置 `handleTime` 为当前时间。

**响应示例：** `{ "code": 200, "message": "成功", "data": "报警已处理" }`
</details>

---

### AI 助手 `/api/ai`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/ai/chat` | 🤖 发送消息给 AI 助手 |

<details>
<summary><b>POST /api/ai/chat</b> — AI 对话</summary>

**请求体：**
```json
{
  "message": "一号大棚最近有什么异常吗？"
}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "reply": "根据最近24小时的数据分析，一号大棚运行状态良好..."
  }
}
```
</details>

---

---

### 🆕 作物图像分类 `/api/classify`

基于 ONNX Runtime 的 YOLOv8 作物生长周期识别，支持 14 个类别（棉花、草莓、向日葵的生长阶段）。

> 支持三种图片来源：**文件上传** / **本地路径** / **MinIO 对象存储**。

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/classify` | 📤 上传图片进行预测 |
| POST | `/api/classify/path` | 📁 通过本地路径预测 |
| POST | `/api/classify/minio` | ☁️ 从 MinIO 拉取图片并预测 |
| GET | `/api/classify/classes` | 📋 获取支持的 14 个类别 |
| GET | `/api/classify/health` | ❤️ 模型健康检查 |

<details>
<summary><b>POST /api/classify</b> — 上传图片预测</summary>

**请求：** `multipart/form-data`，key 为 `file`

```bash
curl -X POST http://localhost:8080/api/classify \
  -F "file=@cotton_flowering.png"
```

**响应示例：**
```json
{
  "code": 200,
  "data": {
    "classId": 0,
    "classNameEn": "cotton_flowering",
    "classNameCn": "棉花-开花期",
    "confidence": 0.9521,
    "top3": [
      { "classId": 0, "classNameCn": "棉花-开花期", "probability": 0.9521 },
      { "classId": 1, "classNameCn": "棉花-结果期", "probability": 0.0312 },
      { "classId": 2, "classNameCn": "棉花-成株期", "probability": 0.0089 }
    ]
  }
}
```
</details>

<details>
<summary><b>POST /api/classify/path</b> — 本地路径预测</summary>

**请求体：**
```json
{
  "imagePath": "F:\\images\\cotton_flowering.png"
}
```

```bash
curl -X POST http://localhost:8080/api/classify/path \
  -H "Content-Type: application/json" \
  -d '{"imagePath": "F:\\images\\cotton_flowering.png"}'
```
</details>

<details>
<summary><b>POST /api/classify/minio</b> — ☁️ MinIO 拉取预测</summary>

从 MinIO 对象存储中拉取图片，直接送入 YOLO 模型推理，全程无磁盘写入。

**请求体：**
```json
{
  "bucket": "crops",
  "objectName": "test/cotton_flowering/aug_0_5367.png"
}
```

> `bucket` 可选，不填则使用配置文件中的 `minio.default-bucket`（默认 `crops`）。

```bash
curl -X POST http://localhost:8080/api/classify/minio \
  -H "Content-Type: application/json" \
  -d '{"bucket": "crops", "objectName": "test/cotton_flowering/aug_0_5367.png"}'
```

**响应示例：**
```json
{
  "code": 200,
  "data": {
    "classId": 0,
    "classNameEn": "cotton_flowering",
    "classNameCn": "棉花-开花期",
    "confidence": 0.9876,
    "source": "minio",
    "bucket": "crops",
    "objectName": "test/cotton_flowering/aug_0_5367.png"
  }
}
```
</details>

<details>
<summary><b>GET /api/classify/classes</b> — 支持的类别</summary>

返回 YOLOv8 模型支持的 14 个作物生长周期类别：

| ID | 英文名 | 中文名 |
|----|--------|--------|
| 0 | cotton_flowering | 棉花-开花期 |
| 1 | cotton_fruiting | 棉花-结果期 |
| 2 | cotton_plant | 棉花-成株期 |
| 3 | cotton_seedling | 棉花-幼苗期 |
| 4 | cotton_sprout | 棉花-发芽期 |
| 5 | strawberry_flowering | 草莓-开花期 |
| 6 | strawberry_fruiting | 草莓-结果期 |
| 7 | strawberry_growing | 草莓-生长期 |
| 8 | strawberry_mature | 草莓-成熟期 |
| 9 | sunflower_earlyBloom | 向日葵-早花期 |
| 10 | sunflower_healthy | 向日葵-健康期 |
| 11 | sunflower_matureBud | 向日葵-成熟花蕾期 |
| 12 | sunflower_wilted | 向日葵-枯萎期 |
| 13 | sunflower_youngBud | 向日葵-幼蕾期 |
</details>

---

### 错误码说明

| code | 说明 |
|------|------|
| 200 | 请求成功 |
| 500 | 业务错误（详见 message 字段） |

> 异常情况（如 404、参数校验失败）会由 Spring Boot 默认处理，返回标准 HTTP 错误码。

---

## ☁️ MinIO 对象存储配置

MinIO 用于存储农作物图片，供分类模型拉取推理。配置在 `application.yaml` 中：

```yaml
minio:
  endpoints:                    # 端点列表，按顺序尝试连接
    - 127.0.0.1:9000
    - 10.190.83.10:9000
  access-key: minioadmin        # 访问密钥
  secret-key: minioadmin        # 密钥
  secure: false                 # 是否使用 TLS（https）
  default-bucket: crops         # 默认 bucket
```

### 核心类

| 类 | 职责 |
|------|------|
| `config/MinioConfig.java` | 绑定 `minio.*` YAML 配置 |
| `service/MinioService.java` | MinIO 客户端，封装图片拉取、列举对象、批量获取 |
| `onnx/service/ClassifierService.java` | 注入 MinioService，提供 `predictFromMinio()` 方法 |

### MinioService 方法

| 方法 | 说明 |
|------|------|
| `getImageBytes(bucket, objectName)` | 获取单张图片字节数组（无磁盘写入） |
| `getImageBytes(objectName)` | 使用默认 bucket 获取 |
| `listObjects(bucket, prefix)` | 列举对象名称 |
| `getImagesBatch(bucket, prefix, maxFiles)` | 批量获取图片字节 |

---

## 🚨 自动报警机制

当 MQTT 收到传感器数据入库后，系统会自动逐项检测是否超出阈值，超标则自动生成报警记录。同一大棚 + 同一报警类型在 **去重窗口内（默认 2 小时）** 已有未处理报警时，不会重复生成。

### 报警类型与阈值

阈值可在 `application.yaml` 的 `greenhouse.alert.thresholds` 配置段中按需调整。

| 报警类型 | 触发条件 | 默认阈值 |
|----------|----------|----------|
| `TEMP_HIGH` | 温度过高 | > 35.0 ℃ |
| `TEMP_LOW` | 温度过低 | < 15.0 ℃ |
| `HUMIDITY_HIGH` | 空气湿度过高 | > 90.0 % |
| `HUMIDITY_LOW` | 空气湿度过低 | < 40.0 % |
| `CO2_HIGH` | CO2 浓度过高 | > 1000.0 ppm |
| `CO2_LOW` | CO2 浓度过低 | < 300.0 ppm |
| `LIGHT_LOW` | 光照不足 | < 5000.0 Lux |
| `SOIL_MOISTURE_HIGH` | 土壤湿度过高 | > 80.0 % |
| `SOIL_MOISTURE_LOW` | 土壤湿度过低 | < 30.0 % |
| `SOIL_PH_HIGH` | 土壤 pH 过高 | > 7.5 |
| `SOIL_PH_LOW` | 土壤 pH 过低 | < 5.5 |

### 配置示例

```yaml
greenhouse:
  alert:
    enabled: true        # 是否启用自动报警检测
    dedup-hours: 2       # 去重窗口（小时）
    thresholds:
      temperature-low: 15.0
      temperature-high: 35.0
      humidity-low: 40.0
      humidity-high: 90.0
      co2-low: 300.0
      co2-high: 1000.0
      light-low: 5000.0
      soil-moisture-low: 30.0
      soil-moisture-high: 80.0
      soil-ph-low: 5.5
      soil-ph-high: 7.5
```

---

## 🎮 自动设备控制机制（🆕）

当 MQTT 收到传感器数据入库并完成报警检测后，系统会自动检查是否需要控制设备。与手动 toggle 完全相同的 MQTT 指令格式下发到华为云 IoTDA。

### 控制逻辑

| 触发条件 | 动作 | 目标设备 | 冷却期 |
|----------|------|----------|--------|
| 温度 > `temperature-high` | 自动开启 | 同大棚 **风机 (FAN)** | 5 分钟 |
| 光照 < `light-low` | 自动开启 | 同大棚 **补光灯 (LIGHT)** | 5 分钟 |
| 土壤湿度 < `soil-moisture-low` | 自动开启 | 同大棚 **灌溉机 (IRRIGATION)** | 5 分钟 |

> 💡 自动控制仅对 **AUTO 模式**设备生效。切换到 **MANUAL（手动）模式**的设备不会触发自动开关，仅响应前端手动操作。

### 保护机制

| 机制 | 说明 |
|------|------|
| **状态检查** | 设备已处于目标状态（已开启/已关闭）则跳过，不重复下发 |
| **冷却期** | 同一设备在冷却窗口内（默认 5 分钟）不重复操作，避免阈值边界抖动 |
| **空值保护** | `greenhouseId` 缺失时跳过，不会报错中断 |
| **MQTT 格式一致** | 自动下发的指令格式与手动 toggle 完全一致（`电机控制` / `紫光灯控制`） |

### 数据流（MQTT → 自动控制）

```
MQTT 传感器数据到达
  → parseSensorData / parsePropertyReport  解析消息
  → sensorDataService.save()               入库
  → alertCheckService.checkAndCreateAlerts()  报警检测
  → autoControlService.checkAndControl()      🆕 自动控制
       ├─ 温度 > 30℃  → 查同大棚 FAN → 关闭中 → 开启，MQTT 下发 {"motor":"ON"}
       ├─ 光照 < 500 Lux → 查同大棚 LIGHT → 关闭中 → 开启，MQTT 下发 {"light":"ON"}
       └─ 土壤湿度 < 30% → 查同大棚 IRRIGATION → 关闭中 → 开启，MQTT 下发 {"Irrigation":"ON"}
```

### 配置示例

```yaml
greenhouse:
  auto-control:
    enabled: true              # 是否启用自动控制（关闭后只报警不控制）
    cooldown-minutes: 5        # 冷却时间（分钟），避免频繁开关
    thresholds:
      temperature-high: 30.0     # ℃，高于此值 → 自动开启风机
      light-low: 500.0           # Lux，低于此值 → 自动开启补光灯
      soil-moisture-low: 30.0    # %，低于此值 → 自动开启灌溉机
```

### 核心类

| 类 | 职责 |
|------|------|
| `config/AutoControlConfig.java` | 读取 YAML 配置（`greenhouse.auto-control`） |
| `service/AutoControlService.java` | 检测阈值、查询设备、更新状态、下发 MQTT |

---

## 🧪 MinIO + YOLO 定时自动分类测试（`MinioOnnxRunner`）

独立测试启动器，**不依赖 Spring 上下文**，直接连接 MinIO 拉取图片 → ONNX YOLO 模型推理 → 保存结果 JSON。

### 快速运行

```bash
# 1. 编译测试代码
mvn test-compile

# 2. 获取 classpath 并运行（单轮 5 张图片）
mvn -q dependency:build-classpath -DincludeScope=compile -Dmdep.outputFile=/tmp/cp.txt
java -cp "target/test-classes;target/classes;$(cat /tmp/cp.txt)" \
    com.greenhouse.MinioOnnxRunner \
    --batch-size=5 --rounds=1
```

### 命令行参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `--batch-size=N` | 5 | 每轮拉取多少张图片 |
| `--interval=N` | 30 | 轮询间隔（秒） |
| `--rounds=N` | 3 | 总轮数，0 = 无限循环直到 Ctrl+C |
| `--bucket=xxx` | crops | MinIO bucket 名称 |
| `--prefix=xxx` | test/ | MinIO 对象前缀 |
| `--endpoint=xxx` | http://10.190.83.10:9000 | MinIO 地址 |
| `--access-key=xxx` | minioadmin | 访问密钥 |
| `--secret-key=xxx` | minioadmin | 密钥 |

> 支持 `--key=value` 和 `--key value` 两种参数格式。

### 使用示例

```bash
# 单次测试：拉 10 张图片，跑 1 轮
java -cp ... com.greenhouse.MinioOnnxRunner --batch-size=10 --rounds=1

# 定时轮询：每 60 秒拉 5 张，无限循环
java -cp ... com.greenhouse.MinioOnnxRunner --batch-size=5 --interval=60 --rounds=0

# 指定 bucket 和前缀
java -cp ... com.greenhouse.MinioOnnxRunner --bucket=img --prefix=cotton/ --batch-size=3 --rounds=3
```

### 输出结果

每轮结果保存到 `test/result/round_NNN_yyyyMMdd_HHmmss.json`：

```json
{
  "round": 1,
  "timestamp": "2026-06-11T15:46:24",
  "bucket": "crops",
  "prefix": "test/",
  "totalFetched": 5,
  "successCount": 5,
  "failCount": 0,
  "avgMs": 108,
  "avgConfidence": 0.8867,
  "classDistribution": {
    "棉花-开花期": 1,
    "向日葵-早花期": 3,
    "棉花-结果期": 1
  },
  "records": [
    {
      "fileName": "aug_49_1034.png",
      "objectName": "test/cotton_flowering/aug_49_1034.png",
      "classNameCn": "棉花-开花期",
      "confidence": 0.9999,
      "elapsedMs": 337
    }
  ]
}
```

### 核心流程

```
MinioOnnxRunner.main()
  ├─ [1/2] MinioClient 连接 MinIO 对象存储
  ├─ [2/2] CropClassifierONNX 加载 YOLOv8 模型
  └─ 循环（按 --rounds 和 --interval 控制）
       ├─ listObjects()     列举 MinIO 对象
       ├─ 随机选取 batchSize 张
       ├─ 逐张：getImageBytes() → classifier.predict(byte[])
       ├─ saveRoundResult()  保存本轮结果 JSON
       └─ Thread.sleep()     等待下一轮
```

### JUnit 测试

也可以通过 JUnit 运行 MinIO 联动测试：

```bash
# MinIO 单张图片测试
mvn test -Dtest=OnnxClassifierTest#testMinioSingleImage

# MinIO 批量测试（10 张 + 保存结果）
mvn test -Dtest=OnnxClassifierTest#testMinioBatch

# 运行全部测试（含本地 + MinIO）
mvn test -Dtest=OnnxClassifierTest
```

> ⚠️ 注意：JUnit 测试依赖 Spring 上下文，需要 MySQL 可用。如果只想测试 MinIO+ONNX 联动，推荐使用 `MinioOnnxRunner` 独立启动器。

---

## 📊 传感器数据表结构 (`gh_sensor_data`)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| greenhouse_id | BIGINT | 所属大棚 ID |
| temperature | DOUBLE | 温度 (℃) |
| humidity | DOUBLE | 空气湿度 (%) |
| light_intensity | DOUBLE | 光照强度 (Lux) |
| co2_concentration | DOUBLE | CO2 浓度 (ppm) |
| soil_moisture | DOUBLE | 土壤湿度 (%) |
| soil_ph | DOUBLE | 土壤 pH 值 |
| record_time | DATETIME | 传感器采集时间 |
| create_time | DATETIME | 入库时间 |

---

## ⚠️ 注意事项（重要）

1. **凭证安全**：`application.yaml` 中的 `ak/sk/access_code` 是敏感信息，提交 GitHub 前务必用环境变量或配置中心替换，或加入 `.gitignore`
2. **QoS=0**：当前 MQTT 采用 QoS 0（最多一次送达），对数据可靠性要求高的场景建议改为 QoS 1
3. **时间精度**：`parsePropertyReport()` 优先使用 IoTDA 的 `event_time`（传感器真实采集时间），解析失败时退化为服务器接收时间
4. **CDC 场景**：如需在生产环境使用，建议将 `access_key/access_code/ak/sk` 等敏感配置抽到环境变量中：
   ```yaml
   spring:
     mqtt:
       access_key: ${IOTDA_ACCESS_KEY}
       access_code: ${IOTDA_ACCESS_CODE}
       ak: ${HUAWEI_AK}
       sk: ${HUAWEI_SK}
   ```
5. **前端页面**：前端为原生 HTML + Chart.js，无构建步骤，Spring Boot 直接从 `static/` 目录提供静态资源

---

## 📝 License

MIT
