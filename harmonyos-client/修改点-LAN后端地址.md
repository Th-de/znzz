# ★ 局域网后端地址修改点（唯一必须修改的位置）

## 修改文件
`entry/src/main/ets/common/Constants.ets`

## 修改内容
把 `BASE_URL` 改成运行 SpringBoot 后端那台电脑的局域网地址：

```ts
export class Constants {
  /** ★ 修改点：后端局域网根地址（不带 /api 后缀） */
  static readonly BASE_URL: string = 'http://192.168.1.100:8080';
  //                                              ↑ 改成你的后端 IP，端口默认 8080
}
```

## 要求
- 手机与后端电脑处于同一局域网，且能互相 ping 通；
- 全部请求会自动拼接为 `BASE_URL + /api/...`（`/api` 前缀无需手动添加）；
- 明文 HTTP 已通过 `entry/src/main/module.json5` 的网络安全配置放开
  （`resources/base/profile/network_config.json` 中 `cleartextTrafficPermitted: true`），无需额外设置。

## 其余无需改动
- 后端 CORS 已放开（application.yml / SecurityConfig 允许任意来源）；
- 接口路径 / 字段均复用既有后端，后端代码零改动。