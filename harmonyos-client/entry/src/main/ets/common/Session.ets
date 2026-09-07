import { LoginData } from '../model/Models';

/**
 * 登录会话：统一读写 AppStorage（已由 PersistentStorage 持久化）。
 * UI 中通过 @StorageLink('token') 等实现自动刷新（登录/退出即时切换页面）。
 */
export class Session {
  static token(): string {
    const v: string | undefined = AppStorage.get<string>('token');
    return v === undefined ? '' : v;
  }

  static role(): string {
    const v: string | undefined = AppStorage.get<string>('role');
    return v === undefined ? '' : v;
  }

  static tenantId(): string {
    const v: string | undefined = AppStorage.get<string>('tenantId');
    return v === undefined ? '' : v;
  }

  static userName(): string {
    const v: string | undefined = AppStorage.get<string>('userName');
    return v === undefined ? '' : v;
  }

  static userPhone(): string {
    const v: string | undefined = AppStorage.get<string>('userPhone');
    return v === undefined ? '' : v;
  }

  static isFactory(): boolean {
    return Session.role().toUpperCase() === 'FACTORY';
  }

  static isBuyer(): boolean {
    return Session.role().toUpperCase() === 'BUYER';
  }

  /** 登录成功后写入会话 */
  static setLogin(data: LoginData, phone: string): void {
    AppStorage.setOrCreate<string>('token', data.token);
    AppStorage.setOrCreate<string>('role', data.role);
    AppStorage.setOrCreate<string>('tenantId', String(data.tenantId));
    AppStorage.setOrCreate<string>('userName', data.name ?? '');
    AppStorage.setOrCreate<string>('userPhone', phone);
  }

  /** 退出登录：清空会话，Index 页 @StorageLink 自动切回登录界面 */
  static clear(): void {
    AppStorage.setOrCreate<string>('token', '');
    AppStorage.setOrCreate<string>('role', '');
    AppStorage.setOrCreate<string>('tenantId', '');
    AppStorage.setOrCreate<string>('userName', '');
    AppStorage.setOrCreate<string>('userPhone', '');
  }
}