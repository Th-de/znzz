import router from '@ohos.router';
import promptAction from '@ohos.promptAction';
import { Api } from '../api/Api';
import { Session } from '../common/Session';
import { LoginData } from '../model/Models';

/**
 * 登录页（真实后端）：POST /api/auth/login 获取 token + role，
 * 按 role(FACTORY/BUYER) 自动跳转工厂端/买家端。
 */
@Entry
@Component
struct Login {
  @State phone: string = '';
  @State password: string = '';
  @State loading: boolean = false;
  @State hint: string = '演示账号：买家 13000000001 / 工厂 13000000002~13000000005，密码 123456';

  aboutToAppear(): void {
    if (Session.token().length > 0) {
      this.routeByRole();
    }
  }

  routeByRole(): void {
    if (Session.isFactory()) {
      router.replaceUrl({ url: 'pages/FactoryMain' });
    } else {
      router.replaceUrl({ url: 'pages/BuyerMain' });
    }
  }

  async doLogin(): Promise<void> {
    const phone: string = this.phone.trim();
    if (phone.length === 0) {
      this.toast('请输入账号');
      return;
    }
    if (this.password.length === 0) {
      this.toast('请输入密码');
      return;
    }
    this.loading = true;
    try {
      const data: LoginData = await Api.login(phone, this.password);
      Session.setLogin(data, phone);
      this.toast('登录成功');
      this.routeByRole();
    } catch (e) {
      const msg: string = e instanceof Error ? (e as Error).message : '登录失败';
      this.toast(msg);
    } finally {
      this.loading = false;
    }
  }

  toast(msg: string): void {
    try {
      promptAction.showToast({ message: msg, duration: 2500 });
    } catch (e) {
      console.warn('toast');
    }
  }

  build() {
    Column() {
      Blank()
      Text('智造云平台')
        .fontSize(30)
        .fontWeight(FontWeight.Bold)
        .fontColor('#0A59F7')
      Text('外协供应链 · 信息查阅终端')
        .fontSize(13)
        .fontColor('#909399')
        .margin({ top: 6, bottom: 36 })

      Column() {
        Row() {
          Text('账号')
            .fontSize(15)
            .fontColor('#333333')
            .width(64)
          TextInput({ placeholder: '手机号，如 13000000001', text: this.phone })
            .layoutWeight(1)
            .type(InputType.PhoneNumber)
            .onChange((value: string) => {
              this.phone = value;
            })
        }
        .width('90%')
        .padding(12)
        .backgroundColor('#F5F7FA')
        .borderRadius(8)

        Row() {
          Text('密码')
            .fontSize(15)
            .fontColor('#333333')
            .width(64)
          TextInput({ placeholder: '登录密码', text: this.password })
            .layoutWeight(1)
            .type(InputType.Password)
            .onChange((value: string) => {
              this.password = value;
            })
        }
        .width('90%')
        .padding(12)
        .backgroundColor('#F5F7FA')
        .borderRadius(8)
        .margin({ top: 14 })

        Button(this.loading ? '登录中...' : '登  录')
          .width('90%')
          .height(44)
          .margin({ top: 24 })
          .backgroundColor('#0A59F7')
          .enabled(!this.loading)
          .onClick(() => {
            this.doLogin();
          })

        Text(this.hint)
          .fontSize(11)
          .fontColor('#B0B3B8')
          .margin({ top: 16 })
          .textAlign(TextAlign.Center)
      }
      .width('100%')

      Blank()
      Text('本 APP 为只读信息终端，所有业务提交请到 PC Web 端完成')
        .fontSize(10)
        .fontColor('#C0C4CC')
        .margin({ bottom: 24 })
    }
    .width('100%')
    .height('100%')
    .backgroundColor('#FFFFFF')
  }
}