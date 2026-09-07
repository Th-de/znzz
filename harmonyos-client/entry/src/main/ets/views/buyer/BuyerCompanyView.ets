import router from '@ohos.router';
import promptAction from '@ohos.promptAction';
import { Api } from '../../api/Api';
import { Session } from '../../common/Session';
import { CompanyProfile } from '../../model/Models';
import { fmtMoney, str } from '../../utils/Format';

/**
 * 买家端-公司主页：企业信息 + 信誉分 + 账户资金（只读），底部退出登录。
 */
@Component
export struct BuyerCompanyView {
  @State profile: CompanyProfile | undefined = undefined;
  @State loading: boolean = true;

  aboutToAppear(): void {
    this.load();
  }

  async load(): Promise<void> {
    this.loading = true;
    try {
      this.profile = await Api.companyProfile();
    } catch (e) {
      const msg: string = e instanceof Error ? (e as Error).message : '加载失败';
      this.toast(msg);
    } finally {
      this.loading = false;
    }
  }

  profileObj(): CompanyProfile {
    const v: CompanyProfile | undefined = this.profile;
    if (v !== undefined && v !== null) {
      return v;
    }
    const empty: CompanyProfile = {};
    return empty;
  }

  logout(): void {
    Session.clear();
    this.toast('已退出登录');
    router.replaceUrl({ url: 'pages/Login' });
  }

  toast(msg: string): void {
    try {
      promptAction.showToast({ message: msg, duration: 2000 });
    } catch (e) {
      console.warn('toast');
    }
  }

  build() {
    Column() {
      if (this.loading) {
        Column() {
          LoadingProgress()
            .width(36)
            .height(36)
            .color('#0A59F7')
        }
        .width('100%')
        .layoutWeight(1)
        .justifyContent(FlexAlign.Center)
      } else {
        Scroll() {
          Column({ space: 12 }) {
            Column() {
              Text('🏢')
                .fontSize(40)
              Text(str(this.profileObj().name))
                .fontSize(18)
                .fontWeight(FontWeight.Bold)
                .fontColor('#1F2329')
                .margin({ top: 6 })
              Text('买家（需求方）')
                .fontSize(12)
                .fontColor('#0A59F7')
                .margin({ top: 4 })
            }
            .width('100%')
            .padding({ top: 24, bottom: 24 })
            .backgroundColor(Color.White)
            .borderRadius(10)

            Column() {
              this.row('企业名称', str(this.profileObj().name))
              this.row('统一信用代码', str(this.profileObj().creditCode))
              this.row('联系人', str(this.profileObj().contactName))
              this.row('联系电话', Session.userPhone())
              this.row('企业地址', str(this.profileObj().address))
              this.row('认证状态', str(this.profileObj().authStatus))
              this.row('信誉分', this.creditText())
              this.row('账户余额', fmtMoney(this.profileObj().balance))
              this.row('冻结金额', fmtMoney(this.profileObj().frozen))
              this.row('企业简介', str(this.profileObj().introduction))
            }
            .width('100%')
            .backgroundColor(Color.White)
            .borderRadius(10)

            Text('信息只读，修改请到 PC 网页端')
              .fontSize(11)
              .fontColor('#B0B3B8')
              .width('100%')

            Button('退出登录')
              .width('100%')
              .height(44)
              .backgroundColor('#FDEEEC')
              .fontColor('#E84026')
              .onClick(() => {
                this.logout();
              })
              .margin({ bottom: 24 })
          }
          .width('100%')
          .padding({ left: 12, right: 12, top: 12 })
        }
        .layoutWeight(1)
        .scrollBar(BarState.Off)
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor('#F5F6FA')
  }

  creditText(): string {
    const c: CompanyProfile = this.profileObj();
    return c.creditScore === undefined ? '-' : String(c.creditScore) + ' 分';
  }

  @Builder
  row(k: string, v: string) {
    Row() {
      Text(k)
        .fontSize(13)
        .fontColor('#909399')
      Blank()
      Text(str(v))
        .fontSize(13)
        .fontColor('#1F2329')
        .textAlign(TextAlign.End)
        .layoutWeight(1)
    }
    .width('100%')
    .padding({ left: 14, right: 14, top: 12, bottom: 12 })
    .border({ width: { bottom: 0.5 }, color: '#F0F1F5' })
  }
}