import router from '@ohos.router';
import promptAction from '@ohos.promptAction';
import { Api } from '../../api/Api';
import { NotifyItem, TodoItem } from '../../model/Models';
import { groupNotifies, NotifyGroup } from '../../utils/MessageGroup';
import { fmtTime, str } from '../../utils/Format';

interface MsgNav {
  key: string;
  title: string;
}

/**
 * 工厂端-首页：
 * 上半部分：平台公告/需求消息 —— 同一需求(#需求ID)的多条通知合并成一行，
 *           展示需求名称 + 最新消息摘要，点击进入该需求消息详情页查看全部历史消息；
 * 下半部分：本厂业务待办消息(/api/common/todos)，仅查看。
 */
@Component
export struct FactoryHomeView {
  @StorageLink('userName') userName: string = '';
  @State groups: NotifyGroup[] = [];
  @State todos: TodoItem[] = [];
  @State loading: boolean = true;

  aboutToAppear(): void {
    this.load();
  }

  async load(): Promise<void> {
    this.loading = true;
    try {
      const list: NotifyItem[] = await Api.notifyList();
      this.groups = groupNotifies(list);
      this.todos = await Api.todoList();
    } catch (e) {
      const msg: string = e instanceof Error ? (e as Error).message : '加载失败';
      this.toast(msg);
    } finally {
      this.loading = false;
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
        Text('智造云平台 · 工厂端')
          .fontSize(17)
          .fontWeight(FontWeight.Bold)
          .fontColor(Color.White)
        Text('你好，' + this.userName)
          .fontSize(12)
          .fontColor('#FFEEDB')
          .margin({ top: 4 })
      }
      .width('100%')
      .alignItems(HorizontalAlign.Start)
      .padding(16)
      .backgroundColor('#F5A623')

      if (this.loading && this.groups.length === 0 && this.todos.length === 0) {
        Column() {
          LoadingProgress()
            .width(36)
            .height(36)
            .color('#F5A623')
        }
        .width('100%')
        .layoutWeight(1)
        .justifyContent(FlexAlign.Center)
      } else {
        Scroll() {
          Column() {
            Text('平台公告 / 需求消息')
              .fontSize(15)
              .fontWeight(FontWeight.Bold)
              .fontColor('#F5A623')
              .width('100%')
              .margin({ top: 4, bottom: 6 })
            if (this.groups.length === 0) {
              Text('暂无消息')
                .fontSize(13)
                .fontColor('#B0B3B8')
                .width('100%')
            } else {
              ForEach(this.groups, (g: NotifyGroup) => {
                this.noticeGroupCard(g)
              }, (g: NotifyGroup) => 'fg_' + g.key)
            }

            Text('本厂业务消息')
              .fontSize(15)
              .fontWeight(FontWeight.Bold)
              .fontColor('#F5A623')
              .width('100%')
              .margin({ top: 16, bottom: 6 })
            if (this.todos.length === 0) {
              Text('暂无业务消息')
                .fontSize(13)
                .fontColor('#B0B3B8')
                .width('100%')
            } else {
              ForEach(this.todos, (t: TodoItem) => {
                Row() {
                  Text(str(t.title))
                    .fontSize(13)
                    .fontColor('#1F2329')
                    .maxLines(1)
                    .textOverflow({ overflow: TextOverflow.Ellipsis })
                    .layoutWeight(1)
                  Text(t.count === undefined ? '' : String(t.count) + ' 项')
                    .fontSize(12)
                    .fontColor('#F5A623')
                }
                .width('100%')
                .padding(12)
                .backgroundColor(Color.White)
                .borderRadius(10)
                .margin({ top: 8 })
              }, (t: TodoItem, index: number) => 'ft_' + index.toString())
            }
            Text('业务办理请到 PC 端完成，本端仅查看')
              .fontSize(10)
              .fontColor('#C0C4CC')
              .width('100%')
              .margin({ top: 12, bottom: 20 })
          }
          .width('100%')
          .padding({ left: 12, right: 12, top: 8 })
        }
        .layoutWeight(1)
        .scrollBar(BarState.Off)
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor('#F5F6FA')
  }

  @Builder
  noticeGroupCard(g: NotifyGroup) {
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
            .fontColor('#F5A623')
            .backgroundColor('#FFF3E0')
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
          .fontColor('#F5A623')
          .margin({ top: 6 })
      }
      .width('100%')
    }
    .width('100%')
    .padding(12)
    .backgroundColor(Color.White)
    .borderRadius(10)
    .margin({ top: 8 })
    .onClick(() => {
      this.openGroup(g);
    })
  }
}