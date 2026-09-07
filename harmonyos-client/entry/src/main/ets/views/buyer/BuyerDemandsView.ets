import promptAction from '@ohos.promptAction';
import { Api } from '../../api/Api';
import { Demand } from '../../model/Models';
import { DEMAND_STATUS, fmtTime, label, statusColor, str } from '../../utils/Format';

/**
 * 买家端-我的需求：上半部分待办需求，下半部分历史需求（仅查看，不可发布）。
 */
@Component
export struct BuyerDemandsView {
  @State pending: Demand[] = [];
  @State history: Demand[] = [];
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
      const all: Demand[] = await Api.demandsMine();
      const p: Demand[] = [];
      const h: Demand[] = [];
      for (let i = 0; i < all.length; i++) {
        const s: string = str(all[i].status);
        if (s === 'COMPLETED' || s === 'CANCELLED' || s === 'FLOW_FAILED') {
          h.push(all[i]);
        } else {
          p.push(all[i]);
        }
      }
      this.pending = p;
      this.history = h;
    } catch (e) {
      const msg: string = e instanceof Error ? (e as Error).message : '加载失败';
      this.toast(msg);
    } finally {
      this.loading = false;
      this.isRefreshing = false;
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
        Text('我的需求')
          .fontSize(17)
          .fontWeight(FontWeight.Bold)
          .fontColor('#1F2329')
        Blank()
        Text('仅查看 · 发布请在 PC 端')
          .fontSize(11)
          .fontColor('#909399')
      }
      .width('100%')
      .padding({ left: 16, right: 16, top: 12, bottom: 8 })

      if (this.loading && this.pending.length === 0 && this.history.length === 0) {
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
          Column() {
            Text('待办需求')
              .fontSize(15)
              .fontWeight(FontWeight.Bold)
              .fontColor('#0A59F7')
              .width('100%')
              .margin({ top: 4, bottom: 6 })
            if (this.pending.length === 0) {
              Text('暂无待办需求')
                .fontSize(13)
                .fontColor('#B0B3B8')
                .width('100%')
                .padding({ top: 6, bottom: 6 })
            } else {
              ForEach(this.pending, (d: Demand) => {
                this.demandCard(d)
              }, (d: Demand) => 'pend_' + String(d.id))
            }

            Text('历史需求')
              .fontSize(15)
              .fontWeight(FontWeight.Bold)
              .fontColor('#0A59F7')
              .width('100%')
              .margin({ top: 16, bottom: 6 })
            if (this.history.length === 0) {
              Text('暂无历史需求')
                .fontSize(13)
                .fontColor('#B0B3B8')
                .width('100%')
                .padding({ top: 6, bottom: 6 })
            } else {
              ForEach(this.history, (d: Demand) => {
                this.demandCard(d)
              }, (d: Demand) => 'hist_' + String(d.id))
            }
          }
          .width('100%')
          .padding({ left: 12, right: 12, bottom: 24 })
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
  demandCard(d: Demand) {
    Column() {
      Row() {
        Text(str(d.title))
          .fontSize(14)
          .fontWeight(FontWeight.Medium)
          .fontColor('#1F2329')
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .layoutWeight(1)
        Text(label(DEMAND_STATUS, str(d.status)))
          .fontSize(11)
          .fontColor(Color.White)
          .backgroundColor(statusColor(str(d.status)))
          .borderRadius(8)
          .padding({ left: 6, right: 6, top: 2, bottom: 2 })
      }
      .width('100%')
      Row() {
        Text('产品：' + str(d.productName))
          .fontSize(12)
          .fontColor('#666666')
        Blank()
        Text('数量：' + (d.quantity === undefined ? '-' : String(d.quantity)))
          .fontSize(12)
          .fontColor('#666666')
      }
      .width('100%')
      .margin({ top: 6 })
      Row() {
        Text('交期：' + str(d.deadlineHard))
          .fontSize(12)
          .fontColor('#666666')
        Blank()
        Text('发布：' + fmtTime(d.createdAt))
          .fontSize(11)
          .fontColor('#B0B3B8')
      }
      .width('100%')
      .margin({ top: 4 })
    }
    .width('100%')
    .padding(12)
    .backgroundColor(Color.White)
    .borderRadius(10)
    .margin({ top: 8 })
  }
}