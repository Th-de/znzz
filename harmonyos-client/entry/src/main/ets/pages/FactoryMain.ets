import { FactoryHomeView } from '../views/factory/FactoryHomeView';
import { FactoryCompanyView } from '../views/factory/FactoryCompanyView';
import { FactoryDevicesView } from '../views/factory/FactoryDevicesView';
import { FactoryOrdersView } from '../views/factory/FactoryOrdersView';

/**
 * 工厂端主框架（4 Tab）：首页 / 公司主页 / 设备管理 / 订单详情（只读）。
 */
@Entry
@Component struct FactoryMain {
  @StorageLink('userName') userName: string = '';
  @State current: number = 0;

  build() {
    Column() {
      if (this.current === 0) {
        FactoryHomeView()
          .layoutWeight(1)
      } else if (this.current === 1) {
        FactoryCompanyView()
          .layoutWeight(1)
      } else if (this.current === 2) {
        FactoryDevicesView()
          .layoutWeight(1)
      } else {
        FactoryOrdersView()
          .layoutWeight(1)
      }

      Row() {
        this.tabItem(0, '🏠', '首页')
        this.tabItem(1, '🏭', '公司主页')
        this.tabItem(2, '⚙️', '设备管理')
        this.tabItem(3, '📋', '订单详情')
      }
      .width('100%')
      .height(56)
      .padding({ bottom: 24 })  // 新增：避开底部手势条
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
        .fontColor(this.current === index ? '#F5A623' : '#999999')
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
