import router from '@ohos.router';
import promptAction from '@ohos.promptAction';
import { Api } from '../../api/Api';
import { OrderItem } from '../../model/Models';
import { fmtMoney, fmtTime, label, ORDER_STATUS, statusColor, str } from '../../utils/Format';

interface OrderNav {
  id: number;
}

/**
 * 买家端-我的订单：本人发布生成的全部订单，点击查看只读详情。
 */
@Component
export struct BuyerOrdersView {
  @State orders: OrderItem[] = [];
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
      this.orders = await Api.listOrders();
    } catch (e) {
      const msg: string = e instanceof Error ? (e as Error).message : '加载失败';
      this.toast(msg);
    } finally {
      this.loading = false;
      this.isRefreshing = false;
    }
  }

  openDetail(id: number): void {
    const p: OrderNav = { id: id };
    router.pushUrl({ url: 'pages/OrderDetailPage', params: p });
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
        Text('我的订单')
          .fontSize(17)
          .fontWeight(FontWeight.Bold)
          .fontColor('#1F2329')
        Blank()
        Text('共 ' + this.orders.length + ' 单')
          .fontSize(12)
          .fontColor('#909399')
      }
      .width('100%')
      .padding({ left: 16, right: 16, top: 12, bottom: 8 })

      if (this.loading && this.orders.length === 0) {
        Column() {
          LoadingProgress()
            .width(36)
            .height(36)
            .color('#0A59F7')
        }
        .width('100%')
        .layoutWeight(1)
        .justifyContent(FlexAlign.Center)
      } else if (this.orders.length === 0) {
        Column() {
          Text('暂无订单')
            .fontSize(14)
            .fontColor('#909399')
        }
        .width('100%')
        .layoutWeight(1)
        .justifyContent(FlexAlign.Center)
      } else {
        Refresh({ refreshing: $$this.isRefreshing }) {
          List({ space: 10 }) {
            ForEach(this.orders, (o: OrderItem) => {
              ListItem() {
                this.orderCard(o)
              }
            }, (o: OrderItem) => 'o_' + String(o.id))
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
  orderCard(o: OrderItem) {
    Column() {
      Row() {
        Text(str(o.title))
          .fontSize(14)
          .fontWeight(FontWeight.Medium)
          .fontColor('#1F2329')
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .layoutWeight(1)
        Text(label(ORDER_STATUS, str(o.status)))
          .fontSize(11)
          .fontColor(Color.White)
          .backgroundColor(statusColor(str(o.status)))
          .borderRadius(8)
          .padding({ left: 6, right: 6, top: 2, bottom: 2 })
      }
      .width('100%')

      Row() {
        Text('产品：' + str(o.productName))
          .fontSize(12)
          .fontColor('#666666')
        Blank()
        Text('总金额 ' + fmtMoney(o.totalAmount))
          .fontSize(13)
          .fontColor('#E84026')
      }
      .width('100%')
      .margin({ top: 6 })

      Row() {
        Text('工厂：' + str(o.factoryNames))
          .fontSize(12)
          .fontColor('#666666')
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .layoutWeight(1)
        Text(fmtTime(o.createdAt))
          .fontSize(11)
          .fontColor('#B0B3B8')
      }
      .width('100%')
      .margin({ top: 6 })

      Row() {
        Text('查看详情（只读）>')
          .fontSize(12)
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
      this.openDetail(o.id);
    })
  }
}