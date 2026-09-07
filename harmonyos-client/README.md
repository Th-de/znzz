# 智造云平台 HarmonyOS NEXT 移动端（真实后端 · 只读终端）

基于既有 SpringBoot+Vue 外协供应链项目开发，**直接对接真实后端，不包含任何 mock 数据**。
定位为**纯信息查阅终端**：所有业务提交（发布需求、报名竞标、填报方案、上传/签署合同、支付等）
一律在 PC Web 后台完成，本 APP 只做浏览查看，**无任何提交/发布/报名/编辑按钮**。

## 页面结构

| 端 | Tab | 内容 |
| --- | --- | --- |
| 登录 | `pages/Login` | 真实登录 `POST /api/auth/login`，token+role 存 Session，按 role 自动跳工厂/买家端 |
| 买家端 | `pages/BuyerMain`（4 Tab） | 首页 / 我的需求 / 我的订单 / 公司主页 |
| 买家-首页 | `views/buyer/BuyerHomeView` | 平台公告/通知列表（`/api/common/notifies`，只读） |
| 买家-我的需求 | `views/buyer/BuyerDemandsView` | 上半待办需求 + 下半历史需求（`/api/demand/mine`），不可发布 |
| 买家-我的订单 | `views/buyer/BuyerOrdersView` | 本人订单（`/api/order/all`）→ 只读详情 |
| 买家-公司主页 | `views/buyer/BuyerCompanyView` | 企业信息/信誉分/账户资金（`/api/enterprise/mine`），只读 + 底部退出登录 |
| 工厂端 | `pages/FactoryMain`（4 Tab） | 首页 / 公司主页 / 设备管理 / 订单详情 |
| 工厂-首页 | `views/factory/FactoryHomeView` | 上半平台公告 + 下半本厂业务消息（notifies/todos，只读） |
| 工厂-公司主页 | `views/factory/FactoryCompanyView` | 企业信息/信誉分/账户资金，只读 + 底部退出登录 |
| 工厂-设备管理 | `views/factory/FactoryDevicesView` | 本厂设备列表（`/api/device/mine`），无增删改 |
| 工厂-订单详情 | `views/factory/FactoryOrdersView` | 本厂承接订单（后端已按租户过滤）→ 只读详情 |
| 订单详情 | `pages/OrderDetailPage` | 订单摘要 / 实施流程图 / 合同 / 工单与阶段款（只读）；工厂只看本厂合同 |

## 复用真实接口清单

| 方法 | 接口 | 用途 |
| --- | --- | --- |
| POST | `/api/auth/login` | 登录 |
| GET | `/api/common/notifies` | 站内通知/平台公告（当前后端没有独立公告表，公告与业务消息同源） |
| GET | `/api/common/todos` | 业务待办消息 |
| GET | `/api/enterprise/mine` | 企业信息 + 信誉分 + 账户资金 |
| GET | `/api/demand/mine` | 买家我发布的需求 |
| GET | `/api/order/all` | 订单列表（买家=本人；工厂=本厂承接，后端按租户过滤） |
| GET | `/api/order/{id}` | 订单摘要 + 实施流程 flowSteps/flowActive |
| GET | `/api/order/{id}/contracts` | 订单合同列表（买家） |
| GET | `/api/order/{id}/contract` | 本厂合同（工厂，只看自己那份） |
| GET | `/api/order/{id}/stages` | 工单与阶段款 |
| GET | `/api/device/mine` | 本厂设备列表 |

## 关键文件

```
entry/src/main/ets/
├─ entryability/EntryAbility.ets      # 入口加载 pages/Login
├─ common/Constants.ets               # ★ 局域网后端地址修改点(BASE_URL)
├─ common/HttpUtil.ets                # 网络封装(现有，复用)：token、R<T>解包、超时
├─ common/Session.ets                 # token/role/tenantId 等持久化 + isFactory/isBuyer
├─ model/Models.ets                   # 与后端字段对齐的数据模型
├─ api/Api.ets                        # 只读接口封装
├─ utils/Format.ets                   # 状态标签/时间/金额格式化
├─ pages/Login · BuyerMain · FactoryMain · OrderDetailPage
└─ views/buyer/*、views/factory/*     # 双端各 4 个 Tab 组件
```

## 局域网后端地址修改点

打开 `entry/src/main/ets/common/Constants.ets`，把 `BASE_URL` 改成运行后端的电脑局域网地址（如 `http://192.168.1.100:8080`），
请求会自动拼接 `BASE_URL + /api/...`；明文 HTTP 已在 `module.json5` + `network_config.json` 放开。

## 运行

1. 启动后端（MySQL + Redis + SpringBoot :8080）。
2. DevEco Studio `File → Open` 本目录；SDK 版本不符时在 `Project Structure` 调整 `compatibleSdkVersion`。
3. 修改 `Constants.ets` 的 `BASE_URL` 后 Run。
4. 登录演示账号：买家 `13000000001`、工厂 `13000000002~13000000005`，密码 `123456`。

## 只读与权限说明
- 双端页面均无提交/发布/报名/编辑按钮；
- 数据过滤依赖后端权限：订单/需求/通知/设备接口均按当前 token 的租户返回；
- 工厂订单详情合同调用“本厂合同”接口，不拉取其他厂合同。