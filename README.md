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
| 前端 | 原生 HTML/CSS/JS + Chart.js |
| 构建工具 | Maven |

---

## 📁 项目结构

```
demo/
├── src/main/java/com/greenhouse/
│   ├── config/          # 配置类（MyBatis Plus、MQTT 配置）
│   ├── controller/      # REST 接口
│   ├── entity/          # 数据库实体
│   ├── mapper/          # MyBatis Mapper
│   ├── mqtt/            # MQTT 消息监听与下发
│   ├── service/         # 业务逻辑接口
│   └── service/impl/    # 业务逻辑实现
├── src/main/resources/
│   ├── application.yaml # ⚠️ 核心配置文件（需要改！）
│   ├── init.sql          # 数据库初始化脚本
│   └── static/index.html # 前端页面
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
                                  ┌────────────────┐
                                  │  Spring Boot   │
                                  │  应用 (本系统)  │
                                  └───────┬────────┘
                                          │ JDBC(3306)
                                          ▼
                                  ┌────────────────┐
                                  │    MySQL 数据库  │
                                  └───────┬────────┘
                                          │ REST API(8080)
                                          ▼
                                  ┌────────────────┐
                                  │   浏览器前端     │
                                  │  (Chart.js 图表) │
                                  └────────────────┘
```

### 数据流转

1. **传感器上报** → IoT 设备通过 MQTT 上报属性到华为云 IoTDA
2. **应用接收** → `MqttMessageListener` 订阅 IoTDA 主题，解析传感器数据
3. **入库存储** → 解析后的数据写入 `gh_sensor_data` 表
4. **指令下发** → 前端操作 → `MqttSendUtil` 通过华为云 API 向设备下发控制指令

### 两种 MQTT 消息处理

| 消息来源 | 主题 | 解析方法 | 典型场景 |
|----------|------|----------|----------|
| 设备上行消息 | `msg/up` | `parseSensorData()` | 设备主动上报 |
| 属性上报转发 | `/message/down` | `parsePropertyReport()` | IoTDA 规则引擎转发 |

---

## 📡 API 接口

### 大棚管理 `/api/greenhouse`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/list?page=1&size=10&name=xxx` | 分页查询大棚 |
| GET | `/{id}` | 查询单个大棚 |
| POST | `/` | 新增大棚 |
| PUT | `/` | 修改大棚 |
| DELETE | `/{id}` | 删除大棚（逻辑删除） |

### 传感器数据 `/api/sensor-data`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/list?page=1&size=10&greenhouseId=1` | 分页查询传感器数据 |
| GET | `/chart?metric=temperature&greenhouseIds=1,2&startTime=...&endTime=...` | 📈 图表数据（多棚对比） |
| GET | `/latest/{greenhouseId}` | 查询大棚最新数据 |
| POST | `/` | 新增传感器数据 |
| PUT | `/` | 修改传感器数据 |
| DELETE | `/{id}` | 删除传感器数据 |

### 设备管理 `/api/device`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/list?page=1&size=10&greenhouseId=1&deviceType=FAN` | 分页查询设备 |
| GET | `/{id}` | 查询单个设备 |
| POST | `/` | 新增设备 |
| PUT | `/` | 修改设备 |
| PUT | `/{id}/toggle` | 开关设备（下发MQTT指令） |
| DELETE | `/{id}` | 删除设备 |

### 报警记录 `/api/alert-log`

> ⚠️ 报警由系统根据传感器数据**自动检测生成**，不再支持手动新增。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/list?page=1&size=10&greenhouseId=1&status=0` | 分页查询报警 |
| PUT | `/{id}/handle` | 标记报警已处理 |
| DELETE | `/{id}` | 删除报警 |

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
