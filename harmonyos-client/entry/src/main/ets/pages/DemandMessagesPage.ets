import router from '@ohos.router';
import promptAction from '@ohos.promptAction';
import { Api } from '../api/Api';
import { NotifyItem } from '../model/Models';
import { groupNotifies, keyOfNotify, NotifyGroup } from '../utils/MessageGroup';
import { fmtTime, str } from '../utils/Format';

interface NavParams {
  key: string;
  title: string;
}

/**
 * 需求消息详情页：展示该需求/通知分组下的全部历史消息（只读）。
 */
@Entry
@Component
struct DemandMessagesPage {
  @State groupKey: string = '';
  @State groupTitle: string = '';
  @State messages: NotifyItem[] = [];
  @State loading: boolean = true;

  aboutToAppear(): void {
    const p = router.getParams() as NavParams;
    this.groupKey = p.key;
    this.groupTitle = p.title;
    this.load();
  }

  async load(): Promise<void> {
    this.loading = true;
    try {
      const all: NotifyItem[] = await Api.notifyList();
      const groups: NotifyGroup[] = groupNotifies(all);
      let msgs: NotifyItem[] = [];
      for (let i = 0; i < groups.length; i++) {
        if (groups[i].key === this.groupKey) {
          msgs = groups[i].items.slice();
          if (this.groupTitle.length === 0) {
            this.groupTitle = groups[i].title;
          }
          break;
        }
      }
      this.messages = msgs;
    } catch (e) {
      const msg: string = e instanceof Error ? (e as Error).message : '加载失败';
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
      Row() {
        Text('‹')
          .fontSize(30)
          .fontColor('#1F2329')
          .padding({ left: 12, right: 12 })
          .onClick(() => {
            router.back();
          })
        Text('需求消息')
          .fontSize(18)
          .fontWeight(FontWeight.Bold)
          .fontColor('#1F2329')
        Blank()
        Text('共 ' + this.messages.length + ' 条')
          .fontSize(12)
          .fontColor('#909399')
          .padding({ right: 16 })
      }
      .width('100%')
      .height(52)
      .backgroundColor('#FFFFFF')

      Column() {
        Text(str(this.groupTitle))
          .fontSize(15)
          .fontWeight(FontWeight.Medium)
          .fontColor('#1F2329')
          .width('100%')
        Text('该需求下的全部历史消息')
          .fontSize(11)
          .fontColor('#909399')
          .width('100%')
          .margin({ top: 4 })
      }
      .width('100%')
      .padding(14)
      .backgroundColor('#EAF2FF')
      .margin({ top: 12, left: 12, right: 12 })
      .borderRadius(10)

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
      } else if (this.messages.length === 0) {
        Column() {
          Text('暂无消息')
            .fontSize(14)
            .fontColor('#909399')
        }
        .width('100%')
        .layoutWeight(1)
        .justifyContent(FlexAlign.Center)
      } else {
        List({ space: 10 }) {
          ForEach(this.messages, (m: NotifyItem) => {
            ListItem() {
              Column() {
                Row() {
                  Text(str(m.title))
                    .fontSize(13)
                    .fontWeight(FontWeight.Medium)
                    .fontColor('#1F2329')
                    .layoutWeight(1)
                  Text(fmtTime(m.createdAt))
                    .fontSize(10)
                    .fontColor('#B0B3B8')
                }
                .width('100%')
                Text(str(m.content))
                  .fontSize(12)
                  .fontColor('#666666')
                  .width('100%')
                  .margin({ top: 6 })
              }
              .width('100%')
              .padding(12)
              .backgroundColor(Color.White)
              .borderRadius(10)
            }
          }, (m: NotifyItem) => keyOfNotify(m) + '_' + (m.id === undefined ? 'x' : String(m.id)))
        }
        .width('100%')
        .layoutWeight(1)
        .padding({ left: 12, right: 12, bottom: 12 })
        .scrollBar(BarState.Off)
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor('#F5F6FA')
  }
}