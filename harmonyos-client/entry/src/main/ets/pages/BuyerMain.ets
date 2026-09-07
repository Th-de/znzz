import { BuyerHomeView } from '../views/buyer/BuyerHomeView';
import { BuyerDemandsView } from '../views/buyer/BuyerDemandsView';
import { BuyerOrdersView } from '../views/buyer/BuyerOrdersView';
import { BuyerCompanyView } from '../views/buyer/BuyerCompanyView';

/**
 * 买家端主框架（4 Tab）：首页 / 我的需求 / 我的订单 / 公司主页（只读）。
 */
@Entry
@Component struct BuyerMain {
  @StorageLink('userName') userName: string = '';
  @State current: number = 0;

  build() {
    Column() {
      if (this.current === 0) {
        BuyerHomeView()
          .layoutWeight(1)
      } else if (this.current === 1) {
        BuyerDemandsView()
          .layoutWeight(1)
      } else if (this.current === 2) {
        BuyerOrdersView()
          .layoutWeight(1)
      } else {
        BuyerCompanyView()
          .layoutWeight(1)
      }

      Row() {
        this.tabItem(0, '🏠', '首页')
        this.tabItem(1, '📦', '我的需求')
        this.tabItem(2, '📋', '我的订单')
        this.tabItem(3, '🏢', '公司主页')
      }
      .width('100%')
      .height(56)
      .padding({ bottom: 24 })  // 新增这一行，给系统手势条留出空间
      .backgroundColor('#FFFFFF')
    }
    .width('100%')
    .height('100%')
    .backgroundColor('#F5F6FA')
  }

  @Builder
  tabItem(index: number, icon: string, title: string) {
    Column({ space: 2 }) {
      Text(icon)
        .fontSize(20)
      Text(title)
        .fontSize(10)
        .fontColor(this.current === index ? '#0A59F7' : '#999999')
    }
    .layoutWeight(1)
    .height('100%')
    .justifyContent(FlexAlign.Center)
    .backgroundColor('#FFFFFF')
    .onClick(() => {
      this.current = index;
    })
  }
}
