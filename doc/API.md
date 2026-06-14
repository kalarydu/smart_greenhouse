# 📡 智慧大棚管理系统 — API 接口文档

> 后端服务：Spring Boot 3.x | 基础路径：`http://localhost:8080`

---

## 统一响应格式

所有接口统一响应格式：

```json
{
  "code": 200,
  "message": "成功",
  "data": { ... }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| code | int | 状态码：200 成功，500 失败 |
| message | string | 提示信息 |
| data | object/array/string/null | 返回数据 |

### 分页响应格式

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "records": [ ... ],
    "total": 25,
    "size": 10,
    "current": 1,
    "pages": 3
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| records | array | 当前页数据列表 |
| total | number | 总记录数 |
| size | number | 每页条数 |
| current | number | 当前页码 |
| pages | number | 总页数 |

---

## 1. 大棚管理 `/api/greenhouse`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/greenhouse/list` | 分页查询大棚 |
| GET | `/api/greenhouse/{id}` | 查询单个大棚 |
| POST | `/api/greenhouse` | 新增大棚 |
| PUT | `/api/greenhouse` | 修改大棚 |
| DELETE | `/api/greenhouse/{id}` | 删除大棚（逻辑删除） |

### GET /api/greenhouse/list — 分页查询

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

### POST /api/greenhouse — 新增大棚

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

### PUT /api/greenhouse — 修改大棚

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

### DELETE /api/greenhouse/{id} — 删除大棚

逻辑删除（`deleted=1`），数据不物理删除。

---

## 2. 传感器数据 `/api/sensor-data`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/sensor-data/list` | 分页查询传感器数据 |
| GET | `/api/sensor-data/chart` | 📈 图表数据（多棚多指标对比） |
| GET | `/api/sensor-data/latest/{greenhouseId}` | 查询大棚最新一条数据 |
| GET | `/api/sensor-data/{id}` | 查询单条数据 |
| POST | `/api/sensor-data` | 新增传感器数据 |
| PUT | `/api/sensor-data` | 修改传感器数据 |
| DELETE | `/api/sensor-data/{id}` | 删除传感器数据 |

### GET /api/sensor-data/list — 分页查询

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

### GET /api/sensor-data/chart — 📈 图表数据

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

### POST /api/sensor-data — 新增传感器数据

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

---

## 3. 设备管理 `/api/device`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/device/list` | 分页查询设备 |
| GET | `/api/device/{id}` | 查询单个设备 |
| POST | `/api/device` | 新增设备 |
| PUT | `/api/device` | 修改设备 |
| PUT | `/api/device/{id}/toggle` | 🔌 开关设备（下发 MQTT 指令） |
| PUT | `/api/device/{id}/mode` | 🔄 切换控制模式（自动 ↔ 手动） |
| DELETE | `/api/device/{id}` | 删除设备 |

### GET /api/device/list — 分页查询

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | int | 否 | 页码，默认 1 |
| size | int | 否 | 每页条数，默认 10 |
| greenhouseId | long | 否 | 按大棚ID筛选 |
| deviceType | string | 否 | 按设备类型筛选：FAN / LIGHT / IRRIGATION |

### POST /api/device — 新增设备

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
| deviceType | string | 是 | 设备类型：FAN-风机 / LIGHT-补光灯 / IRRIGATION-灌溉机 |
| status | int | 否 | 初始状态：0-关闭（默认），1-开启 |

### PUT /api/device/{id}/toggle — 🔌 开关设备

切换设备开关状态并通过华为云 IoTDA 下发 MQTT 指令到真实设备。

**指令映射：**

| 设备类型 | MQTT 指令名 | 下发参数 |
|----------|------------|----------|
| FAN（风机） | 电机控制 | `{"motor":"ON"/"OFF"}` |
| LIGHT（补光灯） | 紫光灯控制 | `{"light":"ON"/"OFF"}` |
| IRRIGATION（灌溉机） | 灌溉机控制 | `{"Irrigation":"ON"/"OFF"}` |

**响应示例：** `{ "code": 200, "message": "成功", "data": "设备已开启，指令已下发" }`

### PUT /api/device/{id}/mode — 🔄 切换控制模式

切换设备的控制模式：**AUTO**（自动，根据阈值自动开关）↔ **MANUAL**（手动，仅前端按钮控制）。

**响应示例：** `{ "code": 200, "message": "成功", "data": "已切换为手动控制" }`

> 新增设备默认模式为 AUTO。手动模式下 `AutoControlService` 会跳过该设备，不会自动开关。

---

## 4. 报警记录 `/api/alert-log`

> ⚠️ 报警由系统根据传感器数据**自动检测生成**，不提供手动新增接口。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/alert-log/list` | 分页查询报警 |
| GET | `/api/alert-log/{id}` | 查询单条报警 |
| PUT | `/api/alert-log` | 修改报警 |
| PUT | `/api/alert-log/{id}/handle` | ✅ 标记报警已处理 |
| DELETE | `/api/alert-log/{id}` | 删除报警 |

### GET /api/alert-log/list — 分页查询

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

### PUT /api/alert-log/{id}/handle — ✅ 处理报警

将报警状态改为"已处理"，自动设置 `handleTime` 为当前时间。

**响应示例：** `{ "code": 200, "message": "成功", "data": "报警已处理" }`

---

## 5. AI 助手 `/api/ai`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/ai/chat` | 🤖 发送消息给 AI 助手 |

### POST /api/ai/chat — AI 对话

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

---

## 6. 作物图像分类 `/api/classify`

基于 ONNX Runtime 的 YOLOv8 作物生长周期识别，支持 14 个类别（棉花、草莓、向日葵的生长阶段）。

> 支持三种图片来源：**文件上传** / **本地路径** / **MinIO 对象存储**。

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/classify` | 📤 上传图片进行预测 |
| POST | `/api/classify/path` | 📁 通过本地路径预测 |
| POST | `/api/classify/minio` | ☁️ 从 MinIO 拉取图片并预测 |
| GET | `/api/classify/classes` | 📋 获取支持的 14 个类别 |
| GET | `/api/classify/health` | ❤️ 模型健康检查 |

### POST /api/classify — 上传图片预测

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

### POST /api/classify/path — 本地路径预测

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

### POST /api/classify/minio — ☁️ MinIO 拉取预测

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

### GET /api/classify/classes — 支持的类别

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

---

## 7. 分类结果查询（SSE 推送）`/api/classification`

> 🆕 该模块为 SSE 实时推送 + 历史查询接口，供前端作物监测页面使用。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/classification/stream` | 🔗 SSE 实时推送（EventSource 连接） |
| GET | `/api/classification/status` | 📊 查询 SSE 连接状态 |
| GET | `/api/classification/recent` | 📋 查询最近分类结果（全局） |
| GET | `/api/classification/recent/{greenhouseKey}` | 🌾 按大棚查询最近分类结果 |

### GET /api/classification/stream — SSE 实时推送

SSE（Server-Sent Events）端点，前端通过 `EventSource` 连接接收实时分类结果推送。

**前端示例：**

```js
const es = new EventSource('/api/classification/stream');
es.addEventListener('classification', e => {
    const { count, data } = JSON.parse(e.data);
    // data 是 ClassificationResult[] 数组
});
```

**事件格式：**

```json
{
  "count": 5,
  "data": [
    {
      "id": 1001,
      "greenhouseKey": "cotton",
      "greenhouseName": "棉花大棚",
      "objectName": "test/cotton_flowering/aug_0_5367.png",
      "fileName": "aug_0_5367.png",
      "classId": 0,
      "classNameCn": "棉花-开花期",
      "classNameEn": "cotton_flowering",
      "confidence": 0.9876,
      "elapsedMs": 108,
      "createTime": "2026-06-10T14:30:00"
    }
  ]
}
```

### GET /api/classification/status — SSE 连接状态

**响应示例：**

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "connections": 2,
    "status": "running"
  }
}
```

### GET /api/classification/recent — 全局最近分类结果

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| limit | int | 否 | 返回条数，默认 30，最大 200 |

### GET /api/classification/recent/{greenhouseKey} — 按大棚查询

**路径参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| greenhouseKey | string | 是 | 大棚标识：`cotton` / `sunflower` / `strawberry` |

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| limit | int | 否 | 返回条数，默认 10，最大 100 |

**响应示例：**

```json
{
  "code": 200,
  "message": "成功",
  "data": [
    {
      "id": 1001,
      "greenhouseKey": "strawberry",
      "greenhouseName": "草莓大棚",
      "objectName": "test/strawberry_mature/img_001.png",
      "fileName": "img_001.png",
      "classId": 8,
      "classNameCn": "草莓-成熟期",
      "classNameEn": "strawberry_mature",
      "confidence": 0.9521,
      "elapsedMs": 95,
      "createTime": "2026-06-10T14:30:00"
    }
  ]
}
```

---

## MQTT 主题

| 主题 Pattern | 方向 | 用途 |
|-------------|------|------|
| `msg/up` | 设备 → 应用 | 设备上行消息（设备主动上报） |
| `/message/down` | IoTDA → 应用 | 属性上报转发（IoTDA 规则引擎转发） |
| `greenhouse/+/device/cmd` | 应用 → 设备 | 设备控制指令下发 |

---

## 错误码说明

| code | 说明 |
|------|------|
| 200 | 请求成功 |
| 500 | 业务错误（详见 message 字段） |

> 异常情况（如 404、参数校验失败）会由 Spring Boot 默认处理，返回标准 HTTP 错误码。
