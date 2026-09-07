/**
 * 全局常量
 *
 * ★★★ 局域网后端地址修改点（共 1 处）★★★
 * 把 BASE_URL 改成运行 SpringBoot 后端的那台电脑的局域网地址，
 * 手机（模拟器/真机）需要与后端处于同一局域网并能互相 ping 通。
 * 示例：后端电脑 IP = 192.168.1.100，端口 = 8080
 *   -> http://192.168.1.100:8080
 * 说明：所有请求路径统一以 /api 开头（例如 /api/auth/login），
 *       最终请求地址 = BASE_URL + 路径。
 */
export class Constants {
  /** ★ 修改点：后端局域网根地址（不带 /api 后缀） */
  static readonly BASE_URL: string = 'http://192.168.137.217:8080';
  /** 连接超时（毫秒） */
  static readonly CONNECT_TIMEOUT: number = 10000;

  /** 读取超时（毫秒） */
  static readonly READ_TIMEOUT: number = 30000;

  /** 意向金固定金额：报名时展示用（元） */
  static readonly INTENTION_FEE: number = 1000;

  /** 填报方案时冻结保证金比例 5% */
  static readonly DEPOSIT_RATE: number = 0.05;
}