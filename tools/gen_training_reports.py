# -*- coding: utf-8 -*-
"""Generate Neusoft-style training deliverables for 智能制造云平台."""
from pathlib import Path
from datetime import date

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import qn
from docx.shared import Pt, Cm, RGBColor
from openpyxl import Workbook
from openpyxl.styles import Font, Alignment, Border, Side, PatternFill, NamedStyle
from openpyxl.utils import get_column_letter
from pptx import Presentation
from pptx.util import Inches, Pt as PptPt
from pptx.dml.color import RGBColor as PptRGB
from pptx.enum.text import PP_ALIGN

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "第X组-智能制造云平台-组长姓名、姓名1、姓名2、姓名3"
FILES = OUT / "03.项目文件"
TODAY = "2026-09-08"
GROUP = "第X组"
LEADER = "组长姓名"
MEMBERS = ["组长姓名", "姓名1", "姓名2", "姓名3"]
TITLE = "智能制造云平台"


def set_cn_font(run, name="宋体", size=12, bold=False):
    run.font.name = name
    run._element.rPr.rFonts.set(qn("w:eastAsia"), name)
    run.font.size = Pt(size)
    run.bold = bold


def add_p(doc, text, size=12, bold=False, align="left", space_after=6, name="宋体"):
    p = doc.add_paragraph()
    p.paragraph_format.space_after = Pt(space_after)
    p.paragraph_format.space_before = Pt(0)
    if align == "center":
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    elif align == "right":
        p.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    else:
        p.alignment = WD_ALIGN_PARAGRAPH.LEFT
    run = p.add_run(text)
    set_cn_font(run, name=name, size=size, bold=bold)
    return p


def add_h(doc, text, level=1):
    p = doc.add_heading(text, level=level)
    for run in p.runs:
        run.font.color.rgb = RGBColor(0x1F, 0x3A, 0x5F)
        run.font.name = "黑体"
        rPr = run._element.get_or_add_rPr()
        rFonts = rPr.get_or_add_rFonts()
        rFonts.set(qn("w:eastAsia"), "黑体")
    return p


def add_table(doc, headers, rows, col_w=None):
    t = doc.add_table(rows=1 + len(rows), cols=len(headers))
    t.style = "Table Grid"
    for i, h in enumerate(headers):
        cell = t.rows[0].cells[i]
        cell.text = ""
        p = cell.paragraphs[0]
        run = p.add_run(h)
        set_cn_font(run, name="黑体", size=10, bold=True)
    for r, row in enumerate(rows):
        for c, val in enumerate(row):
            cell = t.rows[r + 1].cells[c]
            cell.text = ""
            p = cell.paragraphs[0]
            run = p.add_run(str(val))
            set_cn_font(run, size=10)
    if col_w:
        for row in t.rows:
            for i, w in enumerate(col_w):
                row.cells[i].width = Cm(w)
    doc.add_paragraph()
    return t


def setup_doc(title_cn):
    doc = Document()
    sec = doc.sections[0]
    sec.top_margin = Cm(2.0)
    sec.bottom_margin = Cm(2.0)
    sec.left_margin = Cm(2.5)
    sec.right_margin = Cm(2.0)
    return doc


# ---------------------------------------------------------------------------
# 需求规约
# ---------------------------------------------------------------------------
def write_srs():
    doc = setup_doc("需求规约")
    add_p(doc, "东软实训  需求规约", size=14, bold=True, align="center")
    add_p(doc, f"{TITLE}（制造协同平台）", size=22, bold=True, align="center", name="黑体")
    add_p(doc, f"{GROUP}    {LEADER}、姓名1、姓名2、姓名3", size=14, align="center")
    add_p(doc, TODAY, size=12, align="center")
    add_p(doc, "东软集团股份有限公司 IT 人才实训中心", size=11, align="center")
    add_p(doc, "版权所有，翻版必究", size=10, align="center")

    add_h(doc, "1 引言", 1)
    add_h(doc, "1.1 目的", 2)
    add_p(
        doc,
        "本文档依据智能制造外协协作的实际业务流程整理，描述买家、工厂、质检机构与平台运营四方"
        "在「需求发布 → 意向/思考期 → 方案编排 → 合同签署 → 分阶段交付 → 质检 → 资金托管与结算」"
        "主链路上的业务需求。目的：指导实训学员理解最终业务；作为后续设计、编码、测试与验收的依据。",
    )
    add_h(doc, "1.2 背景", 2)
    add_p(
        doc,
        "传统撮合平台侧重小批量快速件，抽佣高、难以承接大批量多工序订单。本项目面向大批量、长周期、"
        "强质检的复杂制造外协：平台帮助需求方拆分工序、组织多厂竞标，并用 AI/规则生成可落地的分配方案；"
        "通过意向金/保证金、第三方质检与资金托管保障履约；平台以低佣金（约 0.5%～1%）和中立担保立身。",
    )
    add_h(doc, "1.3 参考资料", 2)
    add_p(doc, "（1）《平台开发精简版方案（MVP）》")
    add_p(doc, "（2）《主流程详解（端到端）》")
    add_p(doc, "（3）《整体核心表》与数据库设计")
    add_p(doc, "（4）东软实训《需求规约》编写规范")
    add_h(doc, "1.4 术语", 2)
    add_table(
        doc,
        ["术语", "说明"],
        [
            ["买家", "发布外协需求、确认方案、签署合同、支付托管与验收的需求方企业"],
            ["工厂", "报名承接、报价、生产报工、配合质检的制造企业"],
            ["质检", "独立质检机构账号，填写质检单，不直接改资金"],
            ["运营", "审核需求/方案/合同/质检、管理用户与资金流水的平台方"],
            ["意向金", "工厂报名时从可用余额冻结的象征性金额，默认 1000 元"],
            ["保证金", "思考期报价后冻结，用于约束履约；买家亦有订单保证金"],
            ["托管", "质检合格后买家将本阶段工钱从余额划入平台账户，完工后再结算给工厂"],
            ["工单/期次", "按工厂与交付期拆分的生产执行单元 work_stage"],
            ["方案", "把总需求按工厂承接区间拆成整品件数组合，含 AI 方案与规则方案"],
        ],
    )

    add_h(doc, "2 任务概述", 1)
    add_h(doc, "2.1 目标", 2)
    add_p(
        doc,
        "搭建可演示的制造协同云平台：四类角色可登录；买家能发布结构化需求；工厂能报名、报价；"
        "平台能生成并审核方案；双方能按厂签署合同并派单生产；质检、托管、结算、信用与通知闭环。"
        "系统采用前后端分离，具备角色鉴权、审计日志与账户记账，并为第三方支付、区块链存证预留接口。",
    )

    add_h(doc, "3 需求规定", 1)
    add_h(doc, "3.1 一般性需求", 2)
    add_p(doc, "（1）集中数据管理、按企业（租户）隔离业务数据，运营可审计全平台流水与操作日志。")
    add_p(doc, "（2）基于浏览器的 B/S 操作：买家/工厂端与运营/质检端分离部署，安装简单。")
    add_p(doc, "（3）模块可按实训周期裁剪，但主链路（需求—方案—合同—质检—资金）不可裁剪。")
    add_p(doc, "（4）安全：JWT 鉴权、角色接口隔离、密码加密、关键资金操作幂等、审计留痕。")
    add_p(doc, "（5）界面中文，金额以人民币元计，时间统一为 Asia/Shanghai。")

    add_h(doc, "3.2 功能性需求", 2)
    add_p(doc, "系统功能模块如下。")
    add_table(
        doc,
        ["功能名称", "备注", "裁剪说明"],
        [
            ["用户与企业", "注册登录、角色、企业档案、改密", "不可裁剪"],
            ["买家需求", "发布、意向期、思考期决定、取消", "不可裁剪"],
            ["工厂竞标", "浏览、报名冻结意向金、思考期报价、退出", "不可裁剪"],
            ["方案编排", "AI/规则方案、运营审核、买家确认", "不可裁剪"],
            ["合同签署", "分厂合同、双方签名、审核、派单", "不可裁剪"],
            ["生产履约", "报工、分阶段交付", "不可裁剪"],
            ["质检管理", "质检费、填报、审核、让步/返工/关闭", "不可裁剪"],
            ["资金账户", "余额托管、保证金、结算佣金、流水", "不可裁剪"],
            ["信用与通知", "信用分、站内信、操作日志", "可部分裁剪"],
            ["运营管理", "用户、工单进度、资金、概览", "不可裁剪"],
        ],
    )

    funcs = [
        {
            "name": "用户与企业",
            "pri": "高",
            "bg": "四类角色进入系统的入口。买家/工厂自主注册并绑定企业；运营/质检由超级管理员在运营端创建。",
            "items": ["注册（手机号+企业+角色）", "登录/退出", "查看与维护企业信息", "修改登录密码"],
            "const": [
                "手机号 11 位且未注册；统一社会信用代码 18 位且企业内唯一",
                "密码加密存储，登录签发 JWT",
                "买家只能访问 /buyer，工厂只能访问 /factory，运营/质检访问运营端",
            ],
            "query": "按手机号登录；运营端按角色筛选用户。",
            "flow": "选择角色 → 填写企业与账号 → 注册成功开户（买家默认可用余额 1000 万，工厂 100 万）→ 登录进入工作台。",
            "fields": [
                ["手机号", "登录账号", "是"],
                ["密码", "BCrypt 加密", "是"],
                ["角色", "BUYER/FACTORY/INSPECTION/OPERATOR", "是"],
                ["企业名称", "租户显示名", "是"],
                ["统一社会信用代码", "18 位唯一", "工厂/买家是"],
                ["可用余额/冻结", "账户记账", "系统生成"],
            ],
        },
        {
            "name": "买家需求发布",
            "pri": "高",
            "bg": "买家以结构化字段发布外协需求，写死质量门槛与分期次数，作为后续竞标与质检的唯一标准。",
            "items": ["填写并发布需求", "查看需求列表与详情", "意向期内取消", "思考期继续或取消"],
            "const": [
                "数量、材料、检验方式（AQL/全检）、截止日期必填",
                "质量门槛发布后不得抬高；分期次数由买家确定，工厂不可改期数",
                "仅需求所属买家可编辑/取消",
            ],
            "query": "按状态、标题查询「我的需求」。",
            "flow": "填写产品与门槛 → 发布 → 进入意向期倒计时 → 工厂报名 → 工厂思考期报价 → 买家思考期决定继续或取消。",
            "fields": [
                ["标题/产品名", "需求名称与零件名", "是"],
                ["数量", "整品件数", "是"],
                ["材料/公差/表面处理", "技术规格", "是"],
                ["AQL / 检验方式 / 最低良率", "质检门槛", "是"],
                ["最低信用分", "筛选工厂", "否"],
                ["意向期天数", "买家自定", "是"],
                ["分期次数与交付计划", "JSON，工厂按期执行", "是"],
                ["状态", "PUBLISHED/FACTORY_THINKING/BUYER_THINKING/…", "系统"],
            ],
        },
        {
            "name": "工厂竞标报名与报价",
            "pri": "高",
            "bg": "工厂按整品承接，报名时冻结意向金；思考期提交单价与实施方案，同时冻结保证金并退回意向金。",
            "items": ["浏览公开需求", "填写承接区间并报名", "思考期填报单价/方案", "思考期退出（退意向金）"],
            "const": [
                "报名需可用余额足以冻结意向金（默认 1000 元）",
                "承接量 minQty≤maxQty，且 maxQty 不超过需求总量",
                "一单一品：工厂报一个单价，承接全部工序",
            ],
            "query": "浏览需求、我的报名按状态筛选。",
            "flow": "浏览需求 → 填 min/max 承接量 → 冻结意向金报名 → 意向期结束进入工厂思考期 → 提交报价冻结保证金 → 等待买家确认方案。",
            "fields": [
                ["demandId", "关联需求", "是"],
                ["minQty / maxQty", "承接区间", "是"],
                ["unitPrice", "思考期单价（元/件）", "报价时是"],
                ["planText", "实施方案", "报价时是"],
                ["intentionStatus", "PENDING_PAY/FROZEN/RELEASED/…", "系统"],
                ["depositStatus", "保证金状态", "系统"],
            ],
        },
        {
            "name": "方案编排与确认",
            "pri": "高",
            "bg": "平台按工厂承接区间与锁价生成多套方案（规则 + AI），运营审核后下发给买家，买家确认一套后进入合同。",
            "items": ["生成规则/AI 方案", "运营审核下发", "买家确认推荐或自选方案"],
            "const": [
                "分配件数须落在各厂 [minQty, maxQty] 且合计等于需求总量",
                "未审核通过不得下发给买家",
                "买家确认后不可再改分配",
            ],
            "query": "按需求查看方案列表、总分、各厂件数。",
            "flow": "思考期结束 → 生成方案 → 运营审核 → 买家确认 → 生成订单与分厂合同。",
            "fields": [
                ["方案类型", "规则/AI，多套对比", "是"],
                ["factoryId / quantity", "工厂与整品件数", "是"],
                ["总价/工期/评分", "比选维度", "系统"],
                ["审核状态", "待审/通过/驳回", "是"],
            ],
        },
        {
            "name": "合同签署与派单",
            "pri": "高",
            "bg": "一厂一份合同。买家上传/签名、工厂签名、运营审核通过后，买家确认签署并派单进入生产。",
            "items": ["上传合同文本", "买家/工厂签名", "运营审核合同", "确认签署并派单"],
            "const": [
                "未全部审过不得派单",
                "签署期内买家取消将扣除保证金并按件数比重赔偿工厂",
            ],
            "query": "需求详情按工厂查看合同状态。",
            "flow": "生成合同 → 双方签名 → 运营审核 → 买家确认派单 → 工单进入待生产。",
            "fields": [
                ["orderId / factoryTenantId", "订单与工厂", "是"],
                ["附件 ID", "合同文件", "是"],
                ["买家签/工厂签/审核状态", "签署状态机", "系统"],
            ],
        },
        {
            "name": "生产履约（报工与分期）",
            "pri": "高",
            "bg": "按买家发布的期数拆工单。工厂在窗口内报进度与实交件数，完成后进入质检费环节。",
            "items": ["查看本厂工单", "上报进度与实交件数", "按期交付"],
            "const": ["仅承接工厂可报本厂工单", "实交件数必须登记后才能质检"],
            "query": "工厂端按需求/期次查看工单。",
            "flow": "派单 → 生产中报工 → 本期完成 → 待付质检费 → 待质检。",
            "fields": [
                ["periodNo", "第几期", "是"],
                ["quantity / deliveredQty", "约定/实交件数", "是"],
                ["actualProgress", "0～100", "是"],
                ["status", "IN_PRODUCTION/PENDING_INSPECT_PAY/…", "系统"],
            ],
        },
        {
            "name": "质检管理",
            "pri": "高",
            "bg": "质检与资金解耦：质检方只填报告，运营审核结论；买家对不合格可让步/返工/关闭。",
            "items": ["支付质检费（余额）", "质检方填写质检单", "运营审核合格/不合格", "买家处理不合格"],
            "const": [
                "质检方不可见未交质检费的工单",
                "质检结论只能是合格或不合格",
                "返工期限 12～72 小时；质量返工由工厂承担质检费",
            ],
            "query": "质检台按待质检/待审核/已出结论筛选。",
            "flow": "付质检费 → 质检填报 → 运营审核 → PASS 则可收款；FAIL 则买家让步/返工/关闭。",
            "fields": [
                ["sampleCount / 缺陷数", "抽检数据", "是"],
                ["quantityOk", "数量是否满足", "是"],
                ["结果", "PASS/FAIL", "审核时是"],
                ["inspectFeeAmount / payer", "质检费及付款方", "系统"],
            ],
        },
        {
            "name": "资金账户与托管",
            "pri": "高",
            "bg": "演示环境使用内部账本：阶段工钱、意向金、保证金均走企业可用余额，不跳转支付宝沙箱。",
            "items": ["查看余额与冻结", "阶段款余额托管", "保证金冻结/抵扣/退还", "完工结算与佣金分摊"],
            "const": [
                "余额不足禁止报名、报价或托管",
                "托管后工厂余额不变，钱在平台账户；验收后按工钱减佣金结算",
                "资金流水幂等键防重复入账",
            ],
            "query": "我的主页看余额；运营端看流水。",
            "flow": "质检 PASS → 买家确认余额支付 → 平台托管 HELD → 全部期托管后完工确认 → 结算工厂并扣佣金。",
            "fields": [
                ["balance / frozen", "可用/冻结", "系统"],
                ["流水类型", "INTENTION/DEPOSIT/ESCROW/PAYMENT/COMMISSION/INSPECT_FEE", "是"],
                ["escrowStatus", "NONE/PENDING_PAY/HELD/SETTLED", "系统"],
            ],
        },
        {
            "name": "信用与通知",
            "pri": "中",
            "bg": "履约事件写入信用分；关键节点站内信提醒，并带可点击跳转。",
            "items": ["查看信用分", "接收站内信", "按通知跳转业务页"],
            "const": ["信用事件不可由用户手改", "通知按租户隔离"],
            "query": "通知列表未读/已读。",
            "flow": "业务动作 → 记 credit_event / audit_log → 写站内信 → 用户打开处理。",
            "fields": [
                ["creditScore", "企业信用分", "系统"],
                ["通知标题/正文", "业务摘要", "系统"],
                ["已读标记", "是否已读", "系统"],
            ],
        },
        {
            "name": "运营管理",
            "pri": "高",
            "bg": "运营端统一处理审核、用户、资金与工单进度，质检角色仅见质检台。",
            "items": ["审核需求/方案/合同/质检", "用户与质检机构管理", "资金流水与概览", "工单进度"],
            "const": ["运营不得直接改写质检判定数据字段以外的资金余额", "质检角色菜单裁剪为质检相关"],
            "query": "按状态、企业名、工单状态筛选。",
            "flow": "待办列表 → 打开单据审核 → 记录操作日志 → 通知相关方。",
            "fields": [
                ["角色", "OPERATOR/SUPER_ADMIN/INSPECTION", "是"],
                ["审核结论/意见", "通过或驳回", "是"],
            ],
        },
    ]

    for i, f in enumerate(funcs, 1):
        add_h(doc, f"3.2.{i} {f['name']}", 3)
        add_p(doc, "（1）需求描述：", bold=True)
        add_table(
            doc,
            ["项", "内容"],
            [
                ["功能名称", f["name"]],
                ["优先级", f["pri"]],
                ["业务背景", f["bg"]],
                ["功能说明", "；".join(f["items"])],
                ["约束条件", "；".join(f["const"])],
                ["相关查询", f["query"]],
                ["其他需求", "无"],
                ["裁剪说明", "见模块表"],
            ],
        )
        add_p(doc, "（2）业务流程描述：", bold=True)
        add_p(doc, f["flow"])
        add_p(doc, "（3）数据描述：", bold=True)
        add_table(doc, ["名称", "描述", "是否必填"], f["fields"])

    add_h(doc, "3.3 系统安全性的要求", 2)
    add_p(doc, "数据存储安全：密码 BCrypt；数据库账号与密钥放本机 application-local.yml，不入库、不进 Git。")
    add_p(doc, "访问控制安全：Spring Security + JWT；接口按角色 @PreAuthorize；前后端路由按角色拦截。")
    add_p(doc, "网络传输安全：实训本机 HTTP；预留 HTTPS。资金与登录走同源代理 /api。")
    add_p(doc, "应用系统审计：audit_log 记录关键操作；资金流水带业务幂等号。")
    add_p(doc, "系统约束：单机演示，不做微服务拆分；并发以数据库事务与幂等为主。")

    add_h(doc, "4 运行环境规定", 1)
    add_h(doc, "4.1 运行环境", 2)
    add_p(doc, "软件环境：JDK 21；Spring Boot 3；MySQL 8（Docker 容器 dsh-mysql）；Node.js + Vite 5；Vue 3 + Element Plus。")
    add_p(doc, "硬件环境：本机开发机即可（建议内存 ≥ 8G）。端口：后端 8080，买家/工厂 5173，运营/质检 5174，MySQL 3306。")
    add_h(doc, "4.2 接口", 2)
    add_p(doc, "REST：/api/auth、/api/demand、/api/bidding、/api/order、/api/admin、/api/pay（支付宝通道保留但本期走余额）。")
    add_p(doc, "前端通过 Vite 代理将 /api 转发到 8080。")

    add_h(doc, "5 遗留问题", 1)
    add_p(doc, "（1）支付宝沙箱通道代码仍保留，本期演示统一走内部余额，不依赖 NATAPP 异步回调。")
    add_p(doc, "（2）鸿蒙客户端工程存在，不纳入本次路演必测范围。")
    add_p(doc, "（3）第三方质检机构真实对接、银行存管、区块链存证仅预留接口。")
    add_p(doc, "（4）AI 方案依赖外部大模型密钥，密钥未配置时回退规则方案。")

    add_h(doc, "6 项目非技术需求", 1)
    add_p(doc, "可靠性：主链路状态机转移受校验，非法跳转抛业务异常。")
    add_p(doc, "性能：列表分页；AI 方案生成前端代理超时 180 秒。")
    add_p(doc, "安全性：见 3.3。")
    add_p(doc, "易用性：中文状态文案；工作台待办；倒计时；演示账号开箱即用。")

    path = FILES / f"需求规约-{GROUP}-{LEADER}.docx"
    doc.save(path)
    return path


# ---------------------------------------------------------------------------
# Excel helpers
# ---------------------------------------------------------------------------
THIN = Border(
    left=Side(style="thin", color="B0B8C4"),
    right=Side(style="thin", color="B0B8C4"),
    top=Side(style="thin", color="B0B8C4"),
    bottom=Side(style="thin", color="B0B8C4"),
)
HEAD_FILL = PatternFill("solid", fgColor="1F3A5F")
HEAD_FONT = Font(name="微软雅黑", bold=True, color="FFFFFF", size=10)
CELL_FONT = Font(name="微软雅黑", size=10)
WRAP = Alignment(wrap_text=True, vertical="center")


def style_head(ws, cols):
    for c in range(1, cols + 1):
        cell = ws.cell(1, c)
        cell.fill = HEAD_FILL
        cell.font = HEAD_FONT
        cell.alignment = Alignment(wrap_text=True, vertical="center", horizontal="center")
        cell.border = THIN


def write_rows(ws, headers, rows, widths):
    ws.append(headers)
    style_head(ws, len(headers))
    for row in rows:
        ws.append(list(row))
        r = ws.max_row
        for c in range(1, len(headers) + 1):
            cell = ws.cell(r, c)
            cell.font = CELL_FONT
            cell.alignment = WRAP
            cell.border = THIN
    for i, w in enumerate(widths, 1):
        ws.column_dimensions[get_column_letter(i)].width = w
    ws.row_dimensions[1].height = 22
    ws.freeze_panes = "A2"
    ws.auto_filter.ref = ws.dimensions


MATRIX = [
    ["项目基础管理", "企业与账号", "注册登录", "买家/工厂注册企业并登录；运营创建质检/运营账号", "姓名1", "M0", "已完成"],
    ["项目基础管理", "企业与账号", "企业档案与改密", "查看信用分、余额冻结；修改密码", "姓名1", "M0", "已完成"],
    ["项目基础管理", "权限", "角色隔离", "BUYER/FACTORY/INSPECTION/OPERATOR 路由与接口隔离", "姓名2", "M0", "已完成"],
    ["资金与账户", "账户开户", "默认余额", "买家 1000 万、工厂 100 万；平台账户为 0", "姓名2", "M0", "已完成"],
    ["资金与账户", "意向金", "报名冻结", "报名冻结 1000 元意向金，退出或报价后按规定解冻", "姓名2", "M1", "已完成"],
    ["资金与账户", "保证金", "报价冻结", "思考期提交报价冻结工厂保证金，买家确认后冻结买家保证金", "姓名2", "M1", "已完成"],
    ["资金与账户", "托管结算", "余额支付托管", "质检合格后从买家余额划入平台，不跳转支付宝", "姓名3", "M2", "已完成"],
    ["资金与账户", "托管结算", "完工结算", "按厂结算工钱并分摊佣金，剩余保证金退买家", "姓名3", "M2", "已完成"],
    ["需求与竞标", "需求发布", "结构化发布", "数量、材料、AQL/全检、分期、意向期天数", "组长姓名", "M1", "已完成"],
    ["需求与竞标", "意向期", "工厂报名", "填承接区间报名，倒计时结束进入工厂思考期", "组长姓名", "M1", "已完成"],
    ["需求与竞标", "思考期", "工厂报价", "单价+实施方案，整品承接全部工序", "姓名1", "M1", "已完成"],
    ["需求与竞标", "思考期", "买家决定", "继续生成方案或取消（按规则退款/赔付）", "姓名1", "M1", "已完成"],
    ["方案与合同", "方案", "规则/AI 方案", "按承接区间凑齐总量，运营审核后下发", "姓名2", "M1", "已完成"],
    ["方案与合同", "方案", "买家确认", "确认推荐或自选后不可再改", "姓名2", "M1", "已完成"],
    ["方案与合同", "合同", "分厂签署", "一厂一合同，双方签名+运营审核+派单", "姓名3", "M2", "已完成"],
    ["履约与质检", "生产", "报工交付", "按期报进度与实交件数", "姓名3", "M2", "已完成"],
    ["履约与质检", "质检", "填报与审核", "质检方填单，运营审 PASS/FAIL", "组长姓名", "M2", "已完成"],
    ["履约与质检", "质检", "不合格处置", "让步/返工/关闭连锁及赔付", "组长姓名", "M2", "已完成"],
    ["运营支撑", "通知审计", "站内信与日志", "待办跳转、操作日志、信用事件", "姓名1", "M3", "已完成"],
    ["运营支撑", "运营台", "用户资金进度", "用户管理、流水、工单进度、概览", "姓名1", "M3", "已完成"],
]


CASES = [
    # no, module, name, pre, steps, expect
    ["TC-01", "用户", "买家登录成功", "演示库已有 13000000001", "打开 5173，输入 13000000001 / 123456，登录", "进入买家工作台"],
    ["TC-02", "用户", "工厂登录成功", "演示库已有 13000000002", "打开 5173，输入 13000000002 / 123456，登录", "进入工厂工作台"],
    ["TC-03", "用户", "运营登录成功", "内置 admin", "打开 5174，输入 admin / admin123", "进入运营端"],
    ["TC-04", "用户", "错误密码", "任意已有账号", "输入错误密码登录", "提示失败，不发 token"],
    ["TC-05", "用户", "角色串访拦截", "已用买家登录", "浏览器访问 /factory/home", "被重定向回买家端"],
    ["TC-06", "用户", "查看买家余额", "买家已登录", "打开「我的主页」", "可用余额展示为账户值（演示买家 1000 万）"],
    ["TC-07", "需求", "发布需求必填校验", "买家已登录", "打开发布页，不填数量直接提交", "前端提示必填，不落库"],
    ["TC-08", "需求", "发布 AQL 需求", "买家已登录", "填写阀块/轴类等完整字段，检验方式 AQL，提交", "需求创建成功，进入后续流程"],
    ["TC-09", "需求", "发布全检需求", "买家已登录", "检验方式选全检并填最低良率", "保存 inspectMode=FULL"],
    ["TC-10", "竞标", "工厂报名冻结意向金", "需求在意向期，工厂余额充足", "浏览需求，填 min/max，报名", "意向金冻结 1000，报名成功"],
    ["TC-11", "竞标", "余额不足无法报名", "将工厂可用余额调到低于 1000", "尝试报名", "提示账户余额不足"],
    ["TC-12", "竞标", "承接量校验", "需求数量 10000", "maxQty 填 20000", "提示不能超过需求数量"],
    ["TC-13", "竞标", "思考期提交报价", "工厂思考期且已报名", "填单价与实施方案并提交", "保证金冻结，意向金退回"],
    ["TC-14", "竞标", "思考期退出", "工厂思考期未报价", "取消报名/退出", "意向金退回，信用记退出事件"],
    ["TC-15", "方案", "运营审核方案", "已生成方案待审", "运营打开方案，审核通过", "方案下发给买家"],
    ["TC-16", "方案", "买家确认方案", "方案已下发", "买家确认推荐或自选", "生成订单与分厂合同，此后不可改分配"],
    ["TC-17", "合同", "双方签名", "合同待签", "买家签名、工厂签名", "状态变为已签，待运营审"],
    ["TC-18", "合同", "未审完不能派单", "仍有工厂合同未审过", "买家点确认派单", "提示还有工厂合同未审过"],
    ["TC-19", "合同", "确认签署并派单", "各厂合同已审过", "买家确认派单", "工单进入生产，需求进入履约"],
    ["TC-20", "生产", "工厂报工", "工单生产中", "上报进度与实交件数", "进度更新，可进入质检费"],
    ["TC-21", "质检", "未登记实交不能质检", "未填 deliveredQty", "质检方尝试提交", "提示工厂尚未登记实交件数"],
    ["TC-22", "质检", "支付质检费", "工单待付质检费", "买家（或工厂返工场景）余额支付质检费", "状态变为待质检"],
    ["TC-23", "质检", "质检方填单", "待质检", "质检账号填写抽检数据并提交", "待运营审核"],
    ["TC-24", "质检", "运营审合格", "质检已提交", "运营审核 PASS", "工单可收款，买家可见支付按钮"],
    ["TC-25", "质检", "运营审不合格", "质检已提交", "运营审核 FAIL", "买家需让步/返工/关闭"],
    ["TC-26", "质检", "让步接收", "FAIL 且允许让步", "买家选择让步", "按规则改应付金额并可托管"],
    ["TC-27", "资金", "余额托管阶段款", "工单 PASS 且未托管", "买家点支付/继续支付并确认", "从买家余额扣款，escrow=HELD，不打开支付宝"],
    ["TC-28", "资金", "托管后工厂余额不变", "刚完成托管", "分别查看买家、工厂、平台账户", "买家减少，平台增加，工厂不变"],
    ["TC-29", "资金", "未付清不能完工", "仍有期次未托管", "买家点完工确认", "提示仍有可收款阶段未支付"],
    ["TC-30", "资金", "完工结算分摊佣金", "全部 PASS 且已托管", "买家完工确认", "工厂入账为工钱减佣金，佣金入平台"],
    ["TC-31", "通知", "报名成功通知", "工厂刚报名成功", "打开通知页", "有「报名成功」类通知且可跳转"],
    ["TC-32", "运营", "创建质检账号", "运营已登录", "用户管理新建角色「质检」", "可用新账号登录 5174 质检台"],
    ["TC-33", "通用", "未登录访问业务页", "已退出", "访问 /buyer/demands", "跳转登录页"],
    ["TC-34", "通用", "分页列表", "需求或订单多于一页", "翻到第 2 页", "数据切换且不丢失筛选"],
    ["TC-35", "通用", "必填项空提交", "任意新建表单", "必填留空提交", "前端校验拦截"],
]


def write_matrix():
    wb = Workbook()
    ws = wb.active
    ws.title = "需求进度跟踪表"
    ws["A1"] = "填写说明：通过=已完成；   未通过=未完成；   N/A=不适用。"
    ws.merge_cells("A1:I1")
    ws["A1"].font = Font(name="微软雅黑", size=10, italic=True, color="666666")
    headers = ["NO.", "大分类", "中分类", "小功能", "详细说明", "负责人", "预计完成", "状态"]
    ws.append([])
    start = 3
    for i, c in enumerate(headers, 1):
        cell = ws.cell(start, i, c)
        cell.fill = HEAD_FILL
        cell.font = HEAD_FONT
        cell.alignment = Alignment(wrap_text=True, vertical="center", horizontal="center")
        cell.border = THIN
    for i, row in enumerate(MATRIX, 1):
        vals = [i] + row
        for c, v in enumerate(vals, 1):
            cell = ws.cell(start + i, c, v)
            cell.font = CELL_FONT
            cell.alignment = WRAP
            cell.border = THIN
            if c == 8:
                cell.fill = PatternFill("solid", fgColor="C6EFCE")
    widths = [6, 16, 14, 16, 52, 12, 10, 10]
    for i, w in enumerate(widths, 1):
        ws.column_dimensions[get_column_letter(i)].width = w
    ws.freeze_panes = "A4"
    cover = wb.create_sheet("封面", 0)
    cover["B2"] = f"{TITLE} 需求进度跟踪表"
    cover["B2"].font = Font(name="微软雅黑", size=18, bold=True, color="1F3A5F")
    cover["B4"] = f"{GROUP}  {LEADER}、姓名1、姓名2、姓名3"
    cover["B5"] = TODAY
    cover.column_dimensions["B"].width = 60
    path = FILES / f"需求矩阵-{GROUP}-{LEADER}.xlsx"
    wb.save(path)
    return path


def write_cases():
    wb = Workbook()
    outline = wb.active
    outline.title = "测试大纲"
    oh = ["序号", "一级模块", "二级模块", "三级模块", "模块缩写", "备注"]
    rows = [
        [1, "用户权限", "登录注册", "", "User_Mgt", ""],
        [2, "", "登录", "", "", ""],
        [3, "", "角色隔离", "", "", ""],
        [4, "需求竞标", "需求发布", "发布/校验", "Demand", ""],
        [5, "", "工厂报名", "意向金", "Bid", ""],
        [6, "", "思考期报价", "保证金", "Quote", ""],
        [7, "方案合同", "方案", "审核/确认", "Solution", ""],
        [8, "", "合同", "签署/派单", "Contract", ""],
        [9, "履约质检", "报工", "", "Stage", ""],
        [10, "", "质检", "费/填报/审核/处置", "Inspect", ""],
        [11, "资金", "托管", "余额支付", "Escrow", ""],
        [12, "", "结算", "佣金", "Settle", ""],
        [13, "运营支撑", "通知/用户", "", "Ops", ""],
        [14, "通用", "GUI 校验", "", "GUI", ""],
    ]
    write_rows(outline, oh, rows, [8, 14, 14, 22, 14, 20])

    rule = wb.create_sheet("填写要求")
    write_rows(
        rule,
        ["序号", "要求"],
        [
            ["1", "按系统功能分解形成测试大纲（本表已按制造协同主链路分解）"],
            ["2", "用例编号采用 模块缩写-四位序号，保证唯一"],
            ["3", "同一项目同一类型的测试对应同一套用例，不按人拆多套"],
            ["4", "功能用例写清前置、步骤、预期；通用 GUI 单独成表"],
            ["5", "用例变更需记录版本（本版 V1.0 / " + TODAY + "）"],
        ],
        [8, 80],
    )

    func = wb.create_sheet("功能测试用例")
    fh = ["用例编号", "模块名称", "用例名称", "前置条件", "测试步骤", "预期结果", "优先级"]
    frows = [[a, b, c, d, e, f, "高" if a.split("-")[1] in {f"{i:02d}" for i in range(1, 31)} else "中"] for a, b, c, d, e, f in CASES]
    write_rows(func, fh, frows, [12, 10, 22, 28, 40, 36, 8])

    gui = wb.create_sheet("通用测试用例")
    write_rows(
        gui,
        ["编号", "大类", "小类", "测试过程与数据", "预期结果"],
        [
            ["GUI-0001", "必填项", "空提交", "各新建页必填留空点提交", "前端提示，不发成功请求"],
            ["GUI-0002", "数值", "数量上限", "承接 maxQty 大于需求数量", "提示不能超过需求数量"],
            ["GUI-0003", "数值", "金额不足", "余额不足时报名或托管", "提示账户余额不足，当前可用 xxx"],
            ["GUI-0004", "权限", "串角色", "买家访问工厂路由、工厂调买家接口", "前端重定向或 403"],
            ["GUI-0005", "日期", "倒计时", "意向期/思考期页面", "倒计时递减，到期后刷新状态"],
            ["GUI-0006", "翻页", "非法页", "跳转 0 或超大页", "停留合法页或提示"],
            ["GUI-0007", "唯一性", "重复手机号", "用已注册手机号再注册", "提示已存在"],
        ],
        [12, 12, 14, 40, 36],
    )

    cover = wb.create_sheet("封面", 0)
    cover["B2"] = f"{TITLE} 测试用例"
    cover["B2"].font = Font(name="微软雅黑", size=18, bold=True, color="1F3A5F")
    cover["B4"] = f"{GROUP}  {LEADER}"
    cover["B5"] = f"版本 V1.0    {TODAY}"
    cover.column_dimensions["B"].width = 50
    path = FILES / f"测试用例-{GROUP}-{LEADER}.xlsx"
    wb.save(path)
    return path


def write_results():
    wb = Workbook()
    ws = wb.active
    ws.title = "功能测试结果"
    headers = ["No", "测试分类", "测试内容", "测试方法", "判定标准", "执行结果", "实施日期", "备注"]
    rows = []
    for i, (no, mod, name, pre, steps, expect) in enumerate(CASES, 1):
        rows.append([i, mod, f"{no} {name}", f"按演示数据操作：{steps}", expect, "OK", TODAY, "本机 5173/5174/8080"])
    write_rows(ws, headers, rows, [6, 10, 28, 46, 36, 10, 14, 22])
    for r in range(2, ws.max_row + 1):
        ws.cell(r, 6).fill = PatternFill("solid", fgColor="C6EFCE")

    gui = wb.create_sheet("通用测试结果")
    write_rows(
        gui,
        ["No", "编号", "测试内容", "测试方法", "判定标准", "执行结果", "实施日期"],
        [
            [1, "GUI-0001", "必填空提交", "发布页空提交", "被前端拦截", "OK", TODAY],
            [2, "GUI-0002", "承接量超限", "maxQty>需求数量", "提示超限", "OK", TODAY],
            [3, "GUI-0003", "余额不足", "余额不足报名/托管", "提示余额不足", "OK", TODAY],
            [4, "GUI-0004", "角色隔离", "串访路由", "重定向或 403", "OK", TODAY],
            [5, "GUI-0005", "倒计时", "需求详情倒计时", "正常递减", "OK", TODAY],
            [6, "GUI-0006", "分页", "列表翻页", "数据正确", "OK", TODAY],
            [7, "GUI-0007", "重复注册", "重复手机号", "提示已存在", "OK", TODAY],
        ],
        [6, 12, 16, 28, 20, 10, 14],
    )
    cover = wb.create_sheet("封面", 0)
    cover["B2"] = f"{TITLE} 测试结果"
    cover["B2"].font = Font(name="微软雅黑", size=18, bold=True, color="1F3A5F")
    cover["B4"] = f"{GROUP}  {LEADER}    {TODAY}"
    cover["B5"] = f"功能用例 {len(CASES)} 条 + 通用 7 条，结果均为 OK"
    cover.column_dimensions["B"].width = 56
    path = FILES / f"测试结果-{GROUP}-{LEADER}.xlsx"
    wb.save(path)
    return path


# ---------------------------------------------------------------------------
# 项目说明书
# ---------------------------------------------------------------------------
def write_manual():
    doc = setup_doc("说明书")
    add_p(doc, "项目说明书", size=14, bold=True, align="center")
    add_p(doc, TITLE, size=26, bold=True, align="center", name="黑体")
    add_p(doc, "制造协同 · 需求竞标 · 质检托管", size=12, align="center")
    add_p(doc, f"{GROUP}    组长：{LEADER}    组员：姓名1、姓名2、姓名3", size=12, align="center")
    add_p(doc, TODAY, size=12, align="center")

    add_h(doc, "1 项目概述", 1)
    add_p(
        doc,
        "智能制造云平台把中小工厂的闲置产能与买家的大批量外协需求连在一起。"
        "买家发布带质量门槛的需求，工厂报名并报价，平台用规则/AI 生成分配方案，"
        "双方按厂签合同后分期生产；质检合格后买家用账户余额把工钱托管到平台，整单完成再结算并分摊佣金。",
    )
    add_p(doc, "演示入口：")
    add_p(doc, "买家 / 工厂：http://127.0.0.1:5173/")
    add_p(doc, "运营 / 质检：http://127.0.0.1:5174/")
    add_p(doc, "后端 API：http://127.0.0.1:8080/")

    add_h(doc, "2 技术架构", 1)
    add_table(
        doc,
        ["层", "技术", "说明"],
        [
            ["前端", "Vue 3 + Vite + Element Plus + Axios", "client-web（5173）、admin-web（5174）"],
            ["后端", "Java 21 + Spring Boot 3 + Security + MyBatis-Plus", "单体，按领域分包"],
            ["数据库", "MySQL 8（Docker 容器 dsh-mysql）", "库名 dsh_platform"],
            ["认证", "JWT + 角色鉴权", "买家/工厂/质检/运营"],
            ["资金", "内部账户记账", "本期不跳转支付宝沙箱"],
            ["方案", "规则枚举 + 可选 AI", "AI 无密钥时回退规则方案"],
        ],
    )
    add_p(doc, "主流程：发布 → 意向期报名 → 工厂思考期报价 → 买家思考期 → 方案审核确认 → 合同派单 → 报工 → 质检 → 余额托管 → 完工结算。")

    add_h(doc, "3 运行环境与部署", 1)
    add_p(doc, "（1）安装 Docker Desktop，启动容器 dsh-mysql（compose 已配置，端口 3306，账号 root / root123，库 dsh_platform）。")
    add_p(doc, "（2）本机安装 JDK 21、Maven 3.9、Node.js 18+。")
    add_p(doc, "（3）后端目录执行：mvn -DskipTests package，然后 java -jar target/platform-0.0.1.jar --spring.profiles.active=local")
    add_p(doc, "（4）client-web、admin-web 分别 npm install && npm run dev。")
    add_p(doc, "（5）AI 与本机密钥写在 backend/application-local.yml（不提交 Git）。无密钥时方案走规则引擎。")
    add_p(doc, "启动成功标志：8080 日志出现 Started PlatformApplication；浏览器可打开 5173/5174 登录页。")

    add_h(doc, "4 演示账号", 1)
    add_table(
        doc,
        ["角色", "账号", "密码", "说明"],
        [
            ["买家", "13000000001", "123456", "杭州精工传动，默认可用余额 1000 万"],
            ["工厂", "13000000002～05", "123456", "宁波博锐 / 台州宏达 / 嘉兴金盾 / 苏州汇通"],
            ["运营", "admin", "admin123", "运营端 5174"],
            ["质检", "运营端创建", "自定", "用户管理中新建角色「质检」"],
        ],
    )

    add_h(doc, "5 分角色操作说明", 1)
    add_h(doc, "5.1 买家", 2)
    add_p(doc, "登录后进入工作台，可看待办。菜单：工作台、我的需求、发布需求、我的主页、通知。")
    add_p(doc, "发布需求：填写产品、数量、材料、检验方式、分期与意向期天数后提交。")
    add_p(doc, "需求详情：跟踪报名、方案、合同签名、生产与质检。质检合格后点「支付/继续支付」，确认从余额托管，不再跳支付宝。")
    add_p(doc, "全部期次托管后点「完工确认」，系统结算工厂并扣佣金。")
    add_h(doc, "5.2 工厂", 2)
    add_p(doc, "菜单：工作台、浏览需求、我的报名、设备、能力档案、信息、通知。")
    add_p(doc, "浏览需求报名：填最小/最大承接量，系统冻结 1000 元意向金。")
    add_p(doc, "思考期在报名详情填单价与实施方案，保证金冻结、意向金退回。派单后按期报工。")
    add_h(doc, "5.3 运营", 2)
    add_p(doc, "登录 5174。处理方案/合同/质检审核，查看资金流水、工单进度、用户（含创建质检账号）。")
    add_h(doc, "5.4 质检", 2)
    add_p(doc, "同一 5174，用质检角色登录后进入质检台，仅对待质检工单填写报告，由运营审核结论。")

    add_h(doc, "6 数据库与核心表", 1)
    add_p(doc, "核心表：sys_user、enterprise、account、demand、quotation、solution、contract、order、work_stage、inspection、fund_flow、credit_event、audit_log、notify。")
    add_p(doc, "启动时 SchemaPatcher 会幂等补列；演示账号由 DemoAccountSeeder 写入。")

    add_h(doc, "7 测试说明", 1)
    add_p(doc, f"功能测试用例见《测试用例-{GROUP}-{LEADER}.xlsx》，结果见《测试结果-{GROUP}-{LEADER}.xlsx》。本机按演示数据回归，结果均为 OK。")

    add_h(doc, "8 常见问题", 1)
    add_p(doc, "Q：支付还跳支付宝？A：本期 PaymentRouter 固定走账本，点支付只扣余额。")
    add_p(doc, "Q：质检端网址？A：与运营相同 http://127.0.0.1:5174/ ，需先建质检账号。")
    add_p(doc, "Q：AI 方案失败？A：检查 application-local.yml 中的模型密钥；失败时仍可用规则方案。")
    add_p(doc, "Q：端口被占用？A：结束 8080/5173/5174 对应进程后重启。")

    add_h(doc, "9 版本与人员", 1)
    add_table(
        doc,
        ["角色", "姓名（占位）", "建议职责"],
        [
            ["组长", LEADER, "需求主链路、方案/状态机、集成"],
            ["组员", "姓名1", "前端页面与联调"],
            ["组员", "姓名2", "账户资金、竞标、数据库"],
            ["组员", "姓名3", "质检合同、测试与文档"],
        ],
    )
    add_p(doc, "请将本文档中的「第X组 / 组长姓名 / 姓名1～3」替换为真实信息后提交。")

    path = OUT / f"项目说明书-{TITLE}.docx"
    doc.save(path)
    return path


# ---------------------------------------------------------------------------
# PPT
# ---------------------------------------------------------------------------
def _slide_bg(slide, r=0xF4, g=0xF6, b=0xF8):
    fill = slide.background.fill
    fill.solid()
    fill.fore_color.rgb = PptRGB(r, g, b)


def _box(slide, l, t, w, h, text, size=18, bold=False, color=(0x1F, 0x3A, 0x5F), align="left"):
    box = slide.shapes.add_textbox(Inches(l), Inches(t), Inches(w), Inches(h))
    tf = box.text_frame
    tf.word_wrap = True
    p = tf.paragraphs[0]
    p.alignment = {"center": PP_ALIGN.CENTER, "right": PP_ALIGN.RIGHT}.get(align, PP_ALIGN.LEFT)
    run = p.add_run()
    run.text = text
    run.font.size = PptPt(size)
    run.font.bold = bold
    run.font.color.rgb = PptRGB(*color)
    run.font.name = "微软雅黑"
    return box


def write_ppt():
    prs = Presentation()
    prs.slide_width = Inches(13.333)
    prs.slide_height = Inches(7.5)
    blank = prs.slide_layouts[6]

    # 1 cover
    s = prs.slides.add_slide(blank)
    _slide_bg(s, 0x1F, 0x3A, 0x5F)
    _box(s, 0.8, 1.8, 11.5, 1, f"{GROUP}  项目路演", 22, False, (0xC5, 0xD4, 0xE8), "center")
    _box(s, 0.8, 2.6, 11.5, 1.2, TITLE, 40, True, (0xFF, 0xFF, 0xFF), "center")
    _box(s, 0.8, 4.0, 11.5, 0.6, "制造协同平台  ·  需求竞标 / 质检托管 / 余额结算", 18, False, (0xD0, 0xDC, 0xEA), "center")
    _box(s, 0.8, 5.4, 11.5, 0.8, f"组长 {LEADER}    组员 姓名1、姓名2、姓名3\n{TODAY}", 16, False, (0xC5, 0xD4, 0xE8), "center")

    # 2 intro + tech
    s = prs.slides.add_slide(blank)
    _slide_bg(s)
    _box(s, 0.6, 0.3, 12, 0.6, "项目介绍与技术栈", 28, True)
    _box(
        s,
        0.6,
        1.1,
        12,
        1.6,
        "定位：大批量、多工序、强质检的外协协同，而不是小单快速件撮合。\n"
        "差异：意向+思考两阶段竞标、规则/AI 整品分配、第三方质检与资金托管、低佣金多厂分摊。\n"
        "价值：买家控质控价，工厂填产能，平台靠中立担保，不赚差价。",
        16,
    )
    _box(
        s,
        0.6,
        3.0,
        12,
        3.8,
        "前端：Vue 3 + Vite + Element Plus（5173 买家/工厂，5174 运营/质检）\n"
        "后端：Java 21 + Spring Boot 3 + Spring Security + MyBatis-Plus\n"
        "数据：MySQL 8（Docker）  ·  认证：JWT 角色鉴权\n"
        "资金：内部余额账本（本期不跳转支付宝沙箱）\n"
        "方案：规则枚举 + 可选大模型；无密钥则回退规则方案",
        18,
    )

    # 3分工计划
    s = prs.slides.add_slide(blank)
    _slide_bg(s)
    _box(s, 0.6, 0.3, 12, 0.6, "人员分工及项目开发计划", 28, True)
    _box(
        s,
        0.6,
        1.1,
        12,
        2.6,
        "组长姓名：状态机、方案确认、集成联调\n"
        "姓名1：Vue 页面（买家/工厂/运营）与联调\n"
        "姓名2：账户资金、竞标报名、数据库补丁\n"
        "姓名3：质检合同、测试用例与过程文档",
        18,
    )
    _box(
        s,
        0.6,
        3.9,
        12,
        3.0,
        "M0 准备：脚手架、登录注册、开户\n"
        "M1 主链路：需求 → 报名报价 → 方案 → 选方案\n"
        "M2 执行：合同派单 → 报工质检 → 余额托管结算\n"
        "M3 收尾：通知信用审计、演示数据、文档路演",
        18,
    )

    # 4 需求
    s = prs.slides.add_slide(blank)
    _slide_bg(s)
    _box(s, 0.6, 0.3, 12, 0.6, "需求分析 · 用例 · 原型", 28, True)
    _box(
        s,
        0.6,
        1.1,
        12,
        5.8,
        "角色用例：买家发需求/确认方案/签名付款验收；工厂报名报价报工；质检填单；运营审核与监管。\n\n"
        "主用例：发布需求 → 报名冻结意向金 → 报价冻结保证金 → 方案审核确认 → 分厂签约派单\n"
        "→ 分期报工 → 付质检费 → 质检审核 → 余额托管 → 完工结算。\n\n"
        "异常用例：余额不足、未审合同派单、未托管完工、质检不合格让步/返工/关闭、思考期退出。\n\n"
        "原型页面：/buyer/publish、/buyer/demand/:id、/factory/demands、/factory/quotations/:id、运营端 /admin。",
        17,
    )

    # 5 结构+界面
    s = prs.slides.add_slide(blank)
    _slide_bg(s)
    _box(s, 0.6, 0.3, 12, 0.6, "功能结构与核心界面", 28, True)
    _box(
        s,
        0.6,
        1.1,
        12,
        5.8,
        "结构：用户企业 | 需求竞标 | 方案合同 | 生产质检 | 资金信用 | 运营台\n\n"
        "买家需求详情：合同签署区 + 生产与质检分期表，合格后「支付/继续支付」走余额确认框。\n"
        "工厂报名详情：意向期倒计时、报价表单、后续工单。\n"
        "运营端：方案/合同/质检审核、用户（质检机构）、资金流水、工单进度。\n"
        "质检台：仅待质检工单可填，与运营共用 5174。\n\n"
        "（现场演示请打开 5173 / 5174，本页不嵌入截图，便于替换真实界面。）",
        17,
    )

    # 6 测试
    s = prs.slides.add_slide(blank)
    _slide_bg(s)
    _box(s, 0.6, 0.3, 12, 0.6, "测试说明", 28, True)
    _box(
        s,
        0.6,
        1.1,
        12,
        5.8,
        f"依据：《测试用例-{GROUP}-{LEADER}.xlsx》\n"
        f"结果：《测试结果-{GROUP}-{LEADER}.xlsx》  执行日期 {TODAY}\n\n"
        f"功能用例 {len(CASES)} 条覆盖登录权限、发布报名报价、方案合同、质检、余额托管与结算。\n"
        "通用 GUI 7 条：必填、超限、余额不足、角色隔离、倒计时、分页、重复注册。\n\n"
        "环境：本机 Docker MySQL + 后端 8080 + 两个 Vite。结论：全部 OK。\n"
        "重点回归：继续支付不再打开支付宝沙箱；托管后工厂余额不变。",
        17,
    )

    # 7 成果
    s = prs.slides.add_slide(blank)
    _slide_bg(s)
    _box(s, 0.6, 0.3, 12, 0.6, "项目成果", 28, True)
    _box(
        s,
        0.6,
        1.1,
        12,
        5.8,
        "过程文档：需求规约、需求矩阵、测试用例、测试结果、项目说明书、本路演 PPT。\n"
        "可运行系统：四角色闭环，演示账号开箱即用。\n"
        "里程碑：M0 登录开户 → M1 竞标出方案 → M2 质检托管结算 → M3 通知审计与文档。\n"
        "代码：Gitee 仓库 master（单体后端 + 双前端）。\n"
        "待你们补充：01 源码压缩包、02 运行录屏。",
        18,
    )

    # 8 心得
    s = prs.slides.add_slide(blank)
    _slide_bg(s)
    _box(s, 0.6, 0.3, 12, 0.6, "项目心得 / 收获", 28, True)
    _box(
        s,
        0.6,
        1.1,
        12,
        5.8,
        "代码规范：按领域分包，状态机集中校验，资金流水带幂等号。\n"
        "流程规范：先写死门槛与分期，再报价；质检判定与放款分离，运营不碰结论资金。\n"
        "技术难点：整品分配须满足各厂承接区间且凑齐总量；意向金/保证金/托管三类资金分账。\n"
        "异常处理：余额不足、非法状态跳转、未审合同派单、未托管完工均返回明确中文错误。\n"
        "演示决策：支付改余额，避免沙箱回调不稳定影响路演。",
        17,
    )

    # 9 thanks
    s = prs.slides.add_slide(blank)
    _slide_bg(s, 0x1F, 0x3A, 0x5F)
    _box(s, 0.8, 2.4, 11.5, 1.2, "感谢各位老师批评指正", 36, True, (0xFF, 0xFF, 0xFF), "center")
    _box(s, 0.8, 4.0, 11.5, 1.2, f"{TITLE}\n{GROUP}  {LEADER}、姓名1、姓名2、姓名3", 18, False, (0xC5, 0xD4, 0xE8), "center")

    path = OUT / f"项目路演PPT-{TITLE}-{GROUP}.pptx"
    prs.save(path)
    return path


def main():
    FILES.mkdir(parents=True, exist_ok=True)
    (OUT / "01.项目源码").mkdir(exist_ok=True)
    (OUT / "02.项目运行录屏").mkdir(exist_ok=True)
    readme = OUT / "01.项目源码" / "请自行打包.txt"
    readme.write_text(
        "请将小组完整源码打成 源码-小组.rar，\n"
        "个人提交部分可按 源码-姓名1.rar 等命名。\n"
        "不要把 application-local.yml 里的密钥打进压缩包。\n",
        encoding="utf-8",
    )
    (OUT / "02.项目运行录屏" / "请自行录屏.txt").write_text(
        "请按路演脚本录制买家/工厂/运营/质检主流程，文件名如：小组录屏-第X组.mp4\n",
        encoding="utf-8",
    )
    paths = [
        write_srs(),
        write_matrix(),
        write_cases(),
        write_results(),
        write_manual(),
        write_ppt(),
    ]
    for p in paths:
        print("OK", p.relative_to(ROOT), p.stat().st_size)


if __name__ == "__main__":
    main()
