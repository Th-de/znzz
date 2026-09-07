import promptAction from '@ohos.promptAction';
import { Api } from '../../api/Api';
import { DeviceItem } from '../../model/Models';
import { fmtTime, str } from '../../utils/Format';

/**
 * 工厂端-设备管理：本厂设备列表（只读，无增删改）。
 */
@Component
export struct FactoryDevicesView {
  @State devices: DeviceItem[] = [];
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
      this.devices = await Api.deviceList();
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

  statusText(s: string | undefined): string {
    const v: string = str(s);
    if (v === 'FAULT' || v === 'MAINTENANCE') {
      return '故障/维护';
    }
    return '正常';
  }

  build() {
    Column() {
      Row() {
        Text('设备管理')
          .fontSize(17)
          .fontWeight(FontWeight.Bold)
          .fontColor('#1F2329')
        Blank()
        Text('共 ' + this.devices.length + ' 台')
          .fontSize(12)
          .fontColor('#909399')
      }
      .width('100%')
      .padding({ left: 16, right: 16, top: 12, bottom: 8 })

      if (this.loading && this.devices.length === 0) {
        Column() {
          LoadingProgress()
            .width(36)
            .height(36)
            .color('#F5A623')
        }
        .width('100%')
        .layoutWeight(1)
        .justifyContent(FlexAlign.Center)
      } else if (this.devices.length === 0) {
        Column() {
          Text('暂无设备')
            .fontSize(14)
            .fontColor('#909399')
        }
        .width('100%')
        .layoutWeight(1)
        .justifyContent(FlexAlign.Center)
      } else {
        Refresh({ refreshing: $$this.isRefreshing }) {
          List({ space: 10 }) {
            ForEach(this.devices, (d: DeviceItem) => {
              ListItem() {
                this.deviceCard(d)
              }
            }, (d: DeviceItem) => 'dev_' + (d.id === undefined ? 'x' : String(d.id)))
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
  deviceCard(d: DeviceItem) {
    Column() {
      Row() {
        Text(str(d.name))
          .fontSize(15)
          .fontWeight(FontWeight.Medium)
          .fontColor('#1F2329')
          .layoutWeight(1)
        Text(this.statusText(d.status))
          .fontSize(11)
          .fontColor(Color.White)
          .backgroundColor(d.status === 'FAULT' || d.status === 'MAINTENANCE' ? '#E84026' : '#07C160')
          .borderRadius(8)
          .padding({ left: 6, right: 6, top: 2, bottom: 2 })
      }
      .width('100%')

      Row() {
        Text('型号：' + str(d.model))
          .fontSize(12)
          .fontColor('#666666')
        Blank()
        Text('精度：' + str(d.precisionText))
          .fontSize(12)
          .fontColor('#666666')
      }
      .width('100%')
      .margin({ top: 6 })

      Row() {
        Text('日产能：' + (d.dailyCapacity === undefined ? '-' : String(d.dailyCapacity)) + ' 件')
          .fontSize(12)
          .fontColor('#666666')
        Blank()
        Text('状态：' + str(d.status))
          .fontSize(11)
          .fontColor('#B0B3B8')
      }
      .width('100%')
      .margin({ top: 4 })

      if (str(d.processNames) !== '-') {
        Text('适用工序：' + str(d.processNames))
          .fontSize(12)
          .fontColor('#666666')
          .width('100%')
          .margin({ top: 4 })
      }
      if (str(d.materials) !== '-') {
        Text('可加工材料：' + str(d.materials))
          .fontSize(12)
          .fontColor('#666666')
          .width('100%')
          .margin({ top: 4 })
      }
      Row() {
        Text('登记时间：' + fmtTime(d.createdAt))
          .fontSize(10)
          .fontColor('#C0C4CC')
          .margin({ top: 6 })
      }
      .width('100%')
    }
    .width('100%')
    .padding(12)
    .backgroundColor(Color.White)
    .borderRadius(10)
  }
}