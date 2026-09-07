import router from '@ohos.router';
import promptAction from '@ohos.promptAction';
import { Api } from '../api/Api';
import { Session } from '../common/Session';
import {
  ComboItem, Contract, OrderDetail, WorkStage
} from '../model/Models';
import {
  CONTRACT_STATUS, ESCROW_STATUS, fmtMoney, fmtTime, label,
  ORDER_STATUS, STAGE_STATUS, statusColor, str
} from '../utils/Format';

interface NavParams {
  id: number;
}

/**
 * 订单详情（只读，双端共用）：
 * 订单摘要 / 实施流程图 / 合同 / 工单与阶段款。
 * 工厂端合同取“本厂合同”，买家端合同取全部合同。
 */
@Entry
@Component
struct OrderDetailPage {
  @State orderId: number = 0;
  @State order: OrderDetail = { id: 0 };
  @State contracts: Contract[] = [];
  @State stages: WorkStage[] = [];
  @State loading: boolean = true;

  aboutToAppear(): void {
    const p = router.getParams() as NavParams;
    this.orderId = Number(p.id);
    this.load();
  }

  async load(): Promise<void> {
    this.loading = true;
    try {
      this.order = await Api.orderDetail(this.orderId);
      let cs: Contract[] = [];
      if (Session.isFactory()) {
        try {
          const mine: Contract = await Api.orderContractMine(this.orderId);
          const arr: Contract[] = [];
          if (mine !== null && mine !== undefined) {
            arr.push(mine);
          }
          cs = arr;
        } catch (e) {
          cs = [];
        }
      } else {
        cs = await Api.orderContracts(this.orderId);
      }
      this.contracts = cs;
      try {
        this.stages = await Api.orderStages(this.orderId);
      } catch (e) {
        this.stages = [];
      }
    } catch (e) {
      const msg: string = e instanceof Error ? (e as Error).message : '加载失败';
      this.toast(msg);
    } finally {
      this.loading = false;
    }
  }

  comboList(): ComboItem[] {
    return this.order.combo ?? [];
  }

  flowList(): string[] {
    const steps: string[] = this.order.flowSteps ?? [];
    if (steps.length === 0) {
      return ['选定方案', '合同', '开工', '质检托管', '完工结算'];
    }
    return steps;
  }

  flowActive(): number {
    return this.order.flowActive ?? 0;
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
        Text('订单详情（只读）')
          .fontSize(18)
          .fontWeight(FontWeight.Bold)
          .fontColor('#1F2329')
        Blank()
        Text('#' + this.orderId)
          .fontSize(13)
          .fontColor('#909399')
          .padding({ right: 16 })
      }
      .width('100%')
      .height(52)
      .backgroundColor('#FFFFFF')

      if (this.loading) {
        Column() {
          LoadingProgress()
            .width(40)
            .height(40)
            .color('#0A59F7')
        }
        .width('100%')
        .layoutWeight(1)
        .justifyContent(FlexAlign.Center)
      } else {
        Scroll() {
          Column() {
            // 订单摘要
            Column() {
              Row() {
                Text(str(this.order.title))
                  .fontSize(15)
                  .fontWeight(FontWeight.Bold)
                  .fontColor('#1F2329')
                  .layoutWeight(1)
                Text(label(ORDER_STATUS, str(this.order.status)))
                  .fontSize(11)
                  .fontColor(Color.White)
                  .backgroundColor(statusColor(str(this.order.status)))
                  .borderRadius(8)
                  .padding({ left: 6, right: 6, top: 2, bottom: 2 })
              }
              .width('100%')
              this.info('产品', str(this.order.productName))
              this.info('数量', this.order.quantity === undefined ? '-' : String(this.order.quantity) + ' 件')
              this.info('硬交期', str(this.order.deadlineHard))
              this.info('总金额', fmtMoney(this.order.totalAmount))
              this.info('佣金', fmtMoney(this.order.commissionAmount))
              this.info('下单时间', fmtTime(this.order.createdAt))
              this.flowSteps()
            }
            .width('100%')
            .padding(14)
            .backgroundColor(Color.White)
            .borderRadius(10)
            .margin({ left: 12, right: 12, top: 12 })

            // 按厂承包
            Column() {
              Text('按厂承包明细')
                .fontSize(15)
                .fontWeight(FontWeight.Bold)
                .fontColor('#1F2329')
                .width('100%')
                .margin({ bottom: 4 })
              if (this.comboList().length === 0) {
                Text('暂无承包明细')
                  .fontSize(13)
                  .fontColor('#B0B3B8')
                  .width('100%')
              } else {
                ForEach(this.comboList(), (c: ComboItem) => {
                  Row() {
                    Text(str(c.factoryName))
                      .fontSize(12)
                      .fontColor('#1F2329')
                      .layoutWeight(1)
                    Text(str(c.processName))
                      .fontSize(12)
                      .fontColor('#666666')
                    Blank()
                    Text(fmtMoney(c.price))
                      .fontSize(12)
                      .fontColor('#E84026')
                  }
                  .width('100%')
                  .padding({ top: 8, bottom: 8 })
                  .border({ width: { bottom: 0.5 }, color: '#F0F1F5' })
                }, (c: ComboItem, index: number) => 'combo_' + index.toString())
              }
            }
            .width('100%')
            .padding(14)
            .backgroundColor(Color.White)
            .borderRadius(10)
            .margin({ left: 12, right: 12, top: 12 })

            // 合同
            Column() {
              Text(Session.isFactory() ? '合同（本厂）' : '合同')
                .fontSize(15)
                .fontWeight(FontWeight.Bold)
                .fontColor('#1F2329')
                .width('100%')
                .margin({ bottom: 4 })
              if (this.contracts.length === 0) {
                Text('暂无合同')
                  .fontSize(13)
                  .fontColor('#B0B3B8')
                  .width('100%')
              } else {
                ForEach(this.contracts, (c: Contract, index: number) => {
                  Column() {
                    Row() {
                      Text(str(c.factoryName))
                        .fontSize(13)
                        .fontColor('#1F2329')
                        .layoutWeight(1)
                      Text(label(CONTRACT_STATUS, str(c.status)))
                        .fontSize(11)
                        .fontColor(statusColor(str(c.status)))
                    }
                    .width('100%')
                    this.info('承包工序', str(c.processNames))
                    this.info('合同文件', str(c.fileName))
                    this.info('创建时间', fmtTime(c.createdAt))
                    this.info('签署时间', fmtTime(c.signedAt))
                  }
                  .width('100%')
                  .padding({ top: 8, bottom: 8 })
                  .border({ width: { bottom: 0.5 }, color: '#F0F1F5' })
                }, (c: Contract, index: number) => 'contract_' + index.toString())
              }
              Text('合同上传/签署请到 PC 端，本端只读')
                .fontSize(10)
                .fontColor('#C0C4CC')
                .width('100%')
                .margin({ top: 4 })
            }
            .width('100%')
            .padding(14)
            .backgroundColor(Color.White)
            .borderRadius(10)
            .margin({ left: 12, right: 12, top: 12 })

            // 工单与阶段款
            Column() {
              Text('工单与阶段款')
                .fontSize(15)
                .fontWeight(FontWeight.Bold)
                .fontColor('#1F2329')
                .width('100%')
                .margin({ bottom: 4 })
              if (this.stages.length === 0) {
                Text('暂无工单')
                  .fontSize(13)
                  .fontColor('#B0B3B8')
                  .width('100%')
              } else {
                ForEach(this.stages, (s: WorkStage, index: number) => {
                  Column() {
                    Row() {
                      Text(str(s.factoryName) + ' · ' + str(s.processName))
                        .fontSize(13)
                        .fontColor('#1F2329')
                        .layoutWeight(1)
                      Text(label(STAGE_STATUS, str(s.status)))
                        .fontSize(11)
                        .fontColor(statusColor(str(s.status)))
                    }
                    .width('100%')
                    this.info('数量', s.quantity === undefined ? '-' : String(s.quantity) + ' 件')
                    this.info('本段工钱', fmtMoney(s.amount))
                    this.info('承诺交期', str(s.promisedDate))
                    this.info('托管状态', label(ESCROW_STATUS, str(s.escrowStatus)))
                    this.info('进度', s.actualProgress === undefined ? '0%' : String(s.actualProgress) + '%')
                  }
                  .width('100%')
                  .padding({ top: 8, bottom: 8 })
                  .border({ width: { bottom: 0.5 }, color: '#F0F1F5' })
                }, (s: WorkStage, index: number) => 'stage_' + index.toString())
              }
            }
            .width('100%')
            .padding(14)
            .backgroundColor(Color.White)
            .borderRadius(10)
            .margin({ left: 12, right: 12, top: 12, bottom: 24 })
          }
          .width('100%')
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
  info(k: string, v: string) {
    Row() {
      Text(k)
        .fontSize(13)
        .fontColor('#909399')
        .width(80)
      Text(v)
        .fontSize(13)
        .fontColor('#1F2329')
        .layoutWeight(1)
        .textAlign(TextAlign.End)
    }
    .width('100%')
    .padding({ top: 4, bottom: 4 })
  }

  @Builder
  flowSteps() {
    Column() {
      Text('实施流程图')
        .fontSize(14)
        .fontWeight(FontWeight.Bold)
        .fontColor('#1F2329')
        .width('100%')
        .margin({ top: 6, bottom: 6 })
      Row() {
        ForEach(this.flowList(), (step: string, index: number) => {
          Column({ space: 3 }) {
            Text(index < this.flowActive() ? '✓' : String(index + 1))
              .fontSize(11)
              .fontColor(index < this.flowActive() ? Color.White : '#666666')
              .width(20)
              .height(20)
              .textAlign(TextAlign.Center)
              .backgroundColor(index < this.flowActive() ? '#07C160' : '#E8EAF0')
              .borderRadius(10)
            Text(step)
              .fontSize(9)
              .fontColor(index < this.flowActive() ? '#07C160' : '#909399')
              .maxLines(1)
          }
          .layoutWeight(1)
          .alignItems(HorizontalAlign.Center)
        }, (step: string, index: number) => 'step_' + index.toString())
      }
      .width('100%')
    }
    .width('100%')
    .padding(10)
    .backgroundColor('#F8FAFC')
    .borderRadius(8)
    .margin({ top: 8 })
  }
}