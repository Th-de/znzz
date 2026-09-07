import router from '@ohos.router';
import promptAction from '@ohos.promptAction';
import { Api } from '../../api/Api';
import { NotifyItem } from '../../model/Models';
import { groupNotifies, keyOfNotify, NotifyGroup } from '../../utils/MessageGroup';
import { fmtTime, str } from '../../utils/Format';

interface MsgNav {
  key: string;
  title: string;
}

/**
 * 买家端-首页：公告/通知列表。
 * 同一需求(#需求ID)的多条通知合并成一行，展示需求名称 + 最新消息摘要；
 * 点击进入该需求的消息详情页查看全部历史消息。
 */
@Component
export struct BuyerHomeView {
  @StorageLink('userName') userName: string = '';
  @State groups: NotifyGroup[] = [];
  @State loading: boolean = true;
  @State isRefreshing: boolean = false;

  aboutToAppear(): void {
    this.load();
  }

  async load(): Promise<void> {
    if (!this.isRefreshing) {
      this.loading = true;
    }
    try {
      const list: NotifyItem[] = await Api.notifyList();
      this.groups = groupNotifies(list);
    } catch (e) {
      const msg: string = e instanceof Error ? (e as Error).message : '加载失败';
      this.toast(msg);
    } finally {
      this.loading = false;
      this.isRefreshing = false;
    }
  }

  openGroup(g: NotifyGroup): void {
    const p: MsgNav = { key: g.key, title: g.title };
    router.pushUrl({ url: 'pages/DemandMessagesPage', params: p });
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
      Column() {
        Text('智造云平台 · 买家端')
          .fontSize(17)
          .fontWeight(FontWeight.Bold)
          .fontColor(Color.White)
        Text('你好，' + this.userName + ' · 公告 / 需求消息')
          .fontSize(12)
          .fontColor('#E3EEFF')
          .margin({ top: 4 })
      }
      .width('100%')
      .alignItems(HorizontalAlign.Start)
      .padding(16)
      .backgroundColor('#0A59F7')

      if (this.loading && this.groups.length === 0) {
        Column() {
          LoadingProgress()
            .width(36)
            .height(36)
            .color('#0A59F7')
        }
        .width('100%')
        .layoutWeight(1)
        .justifyContent(FlexAlign.Center)
      } else if (this.groups.length === 0) {
        Column() {
          Text('暂无消息')
            .fontSize(14)
            .fontColor('#909399')
        }
        .width('100%')
        .layoutWeight(1)
        .justifyContent(FlexAlign.Center)
      } else {
        Refresh({ refreshing: $$this.isRefreshing }) {
          List({ space: 10 }) {
            ForEach(this.groups, (g: NotifyGroup) => {
              ListItem() {
                this.groupCard(g)
              }
            }, (g: NotifyGroup) => g.key)
          }
          .width('100%')
          .height('100%')
          .padding({ left: 12, right: 12, bottom: 12 })
          .scrollBar(BarState.Off)
        }
        .layoutWeight(1)
        .onRefreshing(() => {
          this.isRefreshing = true;
          this.load();
        })
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor('#F5F6FA')
  }

  @Builder
  groupCard(g: NotifyGroup) {
    Column() {
      Row() {
        if (g.key.length > 1 && g.key.charAt(0) === 'd') {
          Text('📌')
            .fontSize(16)
        } else {
          Text('📢')
            .fontSize(16)
        }
        Text(str(g.title))
          .fontSize(14)
          .fontWeight(FontWeight.Medium)
          .fontColor('#1F2329')
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .layoutWeight(1)
          .margin({ left: 6 })
        if (g.count > 1) {
          Text(String(g.count) + ' 条')
            .fontSize(10)
            .fontColor('#0A59F7')
            .backgroundColor('#EAF2FF')
            .borderRadius(8)
            .padding({ left: 6, right: 6, top: 2, bottom: 2 })
            .margin({ left: 6 })
        }
      }
      .width('100%')

      Text(str(g.latestContent))
        .fontSize(12)
        .fontColor('#666666')
        .width('100%')
        .margin({ top: 6 })
        .maxLines(2)
        .textOverflow({ overflow: TextOverflow.Ellipsis })

      Row() {
        Text('最新 ' + fmtTime(g.latestTime))
          .fontSize(10)
          .fontColor('#C0C4CC')
          .margin({ top: 6 })
        Blank()
        Text('查看全部消息 >')
          .fontSize(11)
          .fontColor('#0A59F7')
          .margin({ top: 6 })
      }
      .width('100%')
    }
    .width('100%')
    .padding(12)
    .backgroundColor(Color.White)
    .borderRadius(10)
    .onClick(() => {
      this.openGroup(g);
    })
  }
}