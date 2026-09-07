import UIAbility from '@ohos.app.ability.UIAbility';
import window from '@ohos.window';
import { BusinessError } from '@ohos.base';

export default class EntryAbility extends UIAbility {
  onCreate(): void {
    // 注册持久化会话字段（token/角色等），使 App 重启后仍保持登录态
    PersistentStorage.persistProp('token', '');
    PersistentStorage.persistProp('role', '');
    PersistentStorage.persistProp('tenantId', '');
    PersistentStorage.persistProp('userName', '');
    PersistentStorage.persistProp('userPhone', '');
  }

  onWindowStageCreate(windowStage: window.WindowStage): void {
    windowStage.loadContent('pages/Login', (err: BusinessError) => {
      if (err.code) {
        console.error(`Failed to load the content. Code: ${err.code}, message: ${err.message}`);
        return;
      }
      console.info('智造云平台登录页加载成功');
    });
  }
}