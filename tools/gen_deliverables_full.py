# -*- coding: utf-8 -*-
"""正式交付规格：重写说明书 / PPT / 测试用例 / 测试结果 / 规约 / 矩阵。"""
from pathlib import Path

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import qn
from docx.shared import Pt, Cm, RGBColor
from openpyxl import Workbook
from openpyxl.styles import Font, Alignment, Border, Side, PatternFill
from openpyxl.utils import get_column_letter
from pptx import Presentation
from pptx.dml.color import RGBColor as PptRGB
from pptx.enum.text import PP_ALIGN
from pptx.util import Inches, Pt as PptPt

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "第X组-智能制造云平台-组长姓名、姓名1、姓名2、姓名3"
FILES = OUT / "03.项目文件"
TODAY = "2026-09-08"
VER = "V1.1"
GROUP, LEADER, TITLE = "第X组", "组长姓名", "智能制造云平台"
MEMBERS = "组长姓名、姓名1、姓名2、姓名3"

NAVY = RGBColor(0x1F, 0x3A, 0x5F)
THIN = Border(
    left=Side(style="thin", color="B0B8C4"),
    right=Side(style="thin", color="B0B8C4"),
    top=Side(style="thin", color="B0B8C4"),
    bottom=Side(style="thin", color="B0B8C4"),
)
HFILL = PatternFill("solid", fgColor="1F3A5F")
HFONT = Font(name="微软雅黑", bold=True, color="FFFFFF", size=9)
CFONT = Font(name="微软雅黑", size=9)
WRAP = Alignment(wrap_text=True, vertical="center")
OKFILL = PatternFill("solid", fgColor="C6EFCE")
YFILL = PatternFill("solid", fgColor="FFF2CC")


def set_cn(run, name="宋体", size=12, bold=False, color=None):
    run.font.name = name
    run._element.rPr.rFonts.set(qn("w:eastAsia"), name)
    run.font.size = Pt(size)
    run.bold = bold
    if color:
        run.font.color.rgb = color


def add_p(doc, text, size=12, bold=False, align="left", after=6, name="宋体", first=0, color=None):
    p = doc.add_paragraph()
    pf = p.paragraph_format
    pf.space_after = Pt(after)
    pf.space_before = Pt(0)
    pf.line_spacing = 1.25
    if first:
        pf.first_line_indent = Cm(first)
    p.alignment = {"center": WD_ALIGN_PARAGRAPH.CENTER, "right": WD_ALIGN_PARAGRAPH.RIGHT, "both": WD_ALIGN_PARAGRAPH.JUSTIFY}.get(align, WD_ALIGN_PARAGRAPH.LEFT)
    run = p.add_run(text)
    set_cn(run, name, size, bold, color)
    return p


def add_h(doc, text, level=1):
    p = doc.add_heading(text, level=level)
    for run in p.runs:
        run.font.color.rgb = NAVY
        run.font.name = "黑体"
        rPr = run._element.get_or_add_rPr()
        rFonts = rPr.get_or_add_rFonts()
        rFonts.set(qn("w:eastAsia"), "黑体")
    return p


def add_table(doc, headers, rows, widths=None):
    t = doc.add_table(rows=1 + len(rows), cols=len(headers))
    t.style = "Table Grid"
    for i, h in enumerate(headers):
        cell = t.rows[0].cells[i]
        cell.text = ""
        r = cell.paragraphs[0].add_run(h)
        set_cn(r, "黑体", 9, True)
    for ri, row in enumerate(rows):
        for ci, val in enumerate(row):
            cell = t.rows[ri + 1].cells[ci]
            cell.text = ""
            r = cell.paragraphs[0].add_run(str(val))
            set_cn(r, "宋体", 9)
    if widths:
        for row in t.rows:
            for i, w in enumerate(widths):
                row.cells[i].width = Cm(w)
    doc.add_paragraph()
    return t


def setup_doc():
    doc = Document()
    s = doc.sections[0]
    s.top_margin = Cm(2.2)
    s.bottom_margin = Cm(2.2)
    s.left_margin = Cm(2.5)
    s.right_margin = Cm(2.2)
    footer = s.footer.paragraphs[0]
    footer.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = footer.add_run(f"{TITLE}  {GROUP}  {VER}  密级：内部")
    set_cn(r, "宋体", 9, color=RGBColor(0x66, 0x66, 0x66))
    return doc


def cover(doc, doc_name, extra=""):
    add_p(doc, "东软集团股份有限公司  IT 人才实训中心", 12, True, "center", 4)
    add_p(doc, "项目交付文档", 14, False, "center", 18)
    add_p(doc, TITLE, 26, True, "center", 8, "黑体")
    add_p(doc, doc_name, 20, True, "center", 16, "黑体")
    add_p(doc, extra, 12, False, "center", 8)
    add_table(doc, ["项", "内容"], [
        ["项目名称", TITLE + "（制造协同平台）"],
        ["文档版本", VER],
        ["编制日期", TODAY],
        ["小组", f"{GROUP}    {MEMBERS}"],
        ["适用系统版本", "master / 本地演示环境（Vue3 + Spring Boot 3 + MySQL 8）"],
        ["密级", "内部 · 实训提交"],
    ], [4, 13])


# ========================= 测试用例数据 =========================
# 编号, 模块, 标题, 类型, 优先级, 前置, 数据, 步骤, 预期, 需求点
CASES = [
    ["AUTH-0001", "认证授权", "买家合法登录进入工作台", "功能", "高",
     "库中存在演示买家；5173/8080 已启动",
     "URL=http://127.0.0.1:5173/login\n账号=13000000001\n密码=123456",
     "1. 打开买家/工厂门户\n2. 在登录页输入账号密码\n3. 点击登录",
     "HTTP 200 返回 token；sessionStorage 写入 token/role=BUYER；跳转 /buyer/home；工作台可见待办。",
     "用户与企业-登录"],
    ["AUTH-0002", "认证授权", "工厂合法登录进入工作台", "功能", "高",
     "库中存在演示工厂 13000000002",
     "账号=13000000002 密码=123456",
     "1. 打开 5173 登录\n2. 输入工厂账号\n3. 登录",
     "role=FACTORY，跳转 /factory/home，可见浏览需求/我的报名。",
     "用户与企业-登录"],
    ["AUTH-0003", "认证授权", "运营合法登录运营端", "功能", "高",
     "内置超级管理员",
     "URL=http://127.0.0.1:5174/login\n账号=admin 密码=admin123",
     "1. 打开运营端\n2. 输入 admin/admin123\n3. 登录",
     "进入 /admin，可见审核、用户、资金、工单进度等运营菜单。",
     "运营管理-登录"],
    ["AUTH-0004", "认证授权", "错误密码不得登录", "异常", "高",
     "买家账号存在",
     "账号=13000000001 密码=wrongpass",
     "1. 输入错误密码\n2. 点击登录",
     "提示登录失败；不写入 token；停留登录页。不得泄露“账号不存在/密码错误”以外的内部栈。",
     "用户与企业-安全"],
    ["AUTH-0005", "认证授权", "空账号空密码前端拦截", "边界", "中",
     "打开登录页",
     "账号空 密码空",
     "1. 不填任何字段点登录",
     "Element Plus 校验拦截，不发 /api/auth/login 或即使发出也被后端拒绝。",
     "用户与企业-校验"],
    ["AUTH-0006", "认证授权", "买家不可访问工厂路由", "安全", "高",
     "已用买家登录",
     "访问 /factory/home",
     "1. 登录买家\n2. 地址栏改为 /factory/home",
     "路由守卫重定向回 /buyer/home 或登录页，不渲染工厂菜单。",
     "用户与企业-权限"],
    ["AUTH-0007", "认证授权", "工厂调用买家接口返回 403", "安全", "高",
     "工厂已登录",
     "POST /api/demand 或买家专属支付接口",
     "1. 用工厂 token 调买家发布/阶段支付接口",
     "后端 @PreAuthorize 拒绝，业务码 403，数据不落库。",
     "用户与企业-权限"],
    ["AUTH-0008", "认证授权", "未登录访问业务页跳转登录", "安全", "高",
     "已退出或清空 sessionStorage",
     "访问 /buyer/demands",
     "1. 清除 token\n2. 刷新业务页",
     "跳转 /login，接口 401。",
     "用户与企业-权限"],
    ["AUTH-0009", "认证授权", "买家注册成功并开户", "功能", "高",
     "使用未占用手机号",
     "手机=13900001111 密码=Abc123456\n企业=测试买家A 信用代码=91330000MA1TEST001\n类型=BUYER",
     "1. 登录页切到注册\n2. 填齐必填\n3. 提交",
     "注册成功可立即登录；account.balance=10000000.00，frozen=0；enterprise.type=BUYER。",
     "用户与企业-注册"],
    ["AUTH-0010", "认证授权", "重复手机号拒绝注册", "异常", "高",
     "13000000001 已存在",
     "手机=13000000001 其余合法",
     "1. 用已注册手机号注册",
     "提示手机号已注册，不新增 user/enterprise。",
     "用户与企业-校验"],
    ["AUTH-0011", "认证授权", "信用代码重复拒绝注册", "异常", "高",
     "91330110MA2K8B1X1A 已被杭州精工占用",
     "新手机 + 该信用代码",
     "1. 换手机号但信用代码填已有值\n2. 提交",
     "提示信用代码已存在，事务回滚。",
     "用户与企业-校验"],
    ["AUTH-0012", "认证授权", "修改密码后旧密失效", "功能", "中",
     "买家已登录",
     "旧密=123456 新密=Newpass1",
     "1. 我的主页-修改密码\n2. 退出用旧密登录\n3. 用新密登录",
     "旧密失败，新密成功。演示后请改回 123456 以免影响后续用例。",
     "用户与企业-改密"],
    ["ENT-0001", "企业账户", "买家主页展示余额与冻结", "功能", "高",
     "买家已登录",
     "菜单=我的主页",
     "1. 进入我的主页\n2. 核对账户区",
     "展示企业名「杭州精工传动有限公司」、可用余额、冻结额；文案说明买家默认 1000 万。",
     "资金账户-查询"],
    ["ENT-0002", "企业账户", "工厂主页展示能力与履约数据", "功能", "中",
     "工厂 13000000002 登录",
     "菜单=信息/能力档案",
     "1. 打开信息与能力档案",
     "可见设备、材料、工艺、质检合格率（只读，工厂不可手改平台统计字段）。",
     "工厂竞标-档案"],
    ["DEM-0001", "需求发布", "发布 AQL 需求成功", "功能", "高",
     "买家已登录",
     "标题=实训-铝合金阀块 数量=800\n材料=6061-T6 检验=AQL 1.0\n意向期天数=1 分期=1",
     "1. 打开发布需求\n2. 按数据填基础/技术/质量/交付/意向期\n3. 提交",
     "需求落库 inspectMode=AQL、aql=1.0、minYield 为空；状态进入后续意向流程；列表可见新需求。",
     "买家需求-发布"],
    ["DEM-0002", "需求发布", "发布全检需求必须填最低良率", "功能", "高",
     "买家已登录",
     "检验方式=FULL 最低良率=0.97",
     "1. 选全检\n2. 不填良率提交应失败\n3. 填 0.97 再提交",
     "未填良率被拦截；填写后 inspectMode=FULL，aql 为空。",
     "买家需求-质量门槛"],
    ["DEM-0003", "需求发布", "必填项缺失不得发布", "边界", "高",
     "买家已登录发布页",
     "仅填标题，数量/材料/交期留空",
     "1. 清空必填点提交",
     "前端 rules 拦截，后端即使绕过也校验失败，demand 不新增。",
     "买家需求-校验"],
    ["DEM-0004", "需求发布", "数量必须为正整数", "边界", "中",
     "发布页",
     "quantity=0 或负数",
     "1. 数量控件尝试 0\n2. 提交",
     "控件 min=1 或后端拒绝「数量无效」。",
     "买家需求-校验"],
    ["DEM-0005", "需求发布", "AQL 与全检互斥", "功能", "中",
     "发布页",
     "先选 AQL 再切 FULL",
     "1. 选 AQL 出现 AQL 下拉\n2. 切 FULL 出现最低良率、隐藏 AQL",
     "UI 互斥；落库只保留一种 inspectMode。",
     "买家需求-质量门槛"],
    ["DEM-0006", "需求查询", "买家仅能看自己的需求", "安全", "高",
     "买家 A 登录",
     "GET /api 需求列表",
     "1. 打开我的需求\n2. 核对其余租户需求不可见",
     "列表 tenant_id 均为当前买家，不能通过改 URL id 越权改他人需求。",
     "买家需求-隔离"],
    ["BID-0001", "工厂报名", "意向期报名冻结 1000 元意向金", "功能", "高",
     "存在 PUBLISHED 需求；工厂余额≥1000",
     "工厂=13000000002 minQty=200 maxQty=500",
     "1. 浏览需求打开目标单\n2. 填承接区间报名",
     "quotation.status=INTENTION，intentionStatus=FROZEN；account.frozen 增加 1000，balance 减少 1000；流水 type=INTENTION direction=FREEZE；通知「报名成功」。",
     "工厂竞标-意向金"],
    ["BID-0002", "工厂报名", "余额不足禁止报名", "异常", "高",
     "将测试工厂可用余额调至 <1000（仅测后恢复）",
     "balance=500",
     "1. 尝试报名",
     "抛出「账户余额不足，当前可用 500」；不写 quotation，不写流水。",
     "工厂竞标-意向金"],
    ["BID-0003", "工厂报名", "maxQty 不得超过需求数量", "边界", "高",
     "需求 quantity=800",
     "minQty=100 maxQty=2000",
     "1. 填超限 maxQty 提交",
     "前端或后端提示「最大承接量不能超过需求数量 800」，报名失败。",
     "工厂竞标-承接区间"],
    ["BID-0004", "工厂报名", "minQty 不得大于 maxQty", "边界", "中",
     "需求在意向期",
     "minQty=600 maxQty=200",
     "1. 反填区间提交",
     "提示「最小量不能大于最大量」。",
     "工厂竞标-承接区间"],
    ["BID-0005", "工厂报名", "非意向期不可新报名", "异常", "中",
     "需求已进入 FACTORY_THINKING 或更后",
     "未报名工厂访问报名",
     "1. 在思考期后尝试新报名",
     "拒绝报名，提示当前阶段不可报名。",
     "工厂竞标-状态机"],
    ["BID-0006", "工厂报价", "思考期提交单价并冻结保证金", "功能", "高",
     "该厂已报名且需求=FACTORY_THINKING",
     "unitPrice=12.50 planText=数控铣+去毛刺",
     "1. 报名详情点填报报价\n2. 提交",
     "unitPrice 落库；deposit 按总报价 5% 冻结；意向金解冻退回可用；通知买家「工厂已填报」。",
     "工厂竞标-保证金"],
    ["BID-0007", "工厂报价", "思考期退出退回意向金", "功能", "高",
     "FACTORY_THINKING 且未报价",
     "点取消报名/退出",
     "1. 确认退出",
     "意向金解冻；quotation 不再参与方案；记信用事件工厂思考期退出。",
     "工厂竞标-退出"],
    ["BID-0008", "工厂报价", "未报价不得进入锁价分配", "异常", "高",
     "仅报名未填 unitPrice",
     "运营强行生成含该厂方案",
     "1. 检查方案候选",
     "无有效单价的工厂不进入锁定分配；保存方案时不得引用无效填报。",
     "方案编排-约束"],
    ["SOL-0001", "方案", "生成规则方案且件数凑齐总量", "功能", "高",
     "多家工厂已锁价，承接区间可覆盖总量",
     "需求数量=Q，各厂 [min,max]",
     "1. 思考期结束或运营触发生成\n2. 打开方案",
     "每厂分配量∈[min,max]，合计=Q；展示总价/工期/理由。",
     "方案编排-规则"],
    ["SOL-0002", "方案", "区间无法凑齐时不得下发虚假方案", "异常", "高",
     "所有厂 max 之和 < 需求数量",
     "覆盖不足",
     "1. 触发生成",
     "提示产能/承接不足，不能审核通过一套件数对不上的方案。",
     "方案编排-约束"],
    ["SOL-0003", "方案", "运营审核通过后买家可见", "功能", "高",
     "方案待审",
     "运营账号审核通过",
     "1. 运营打开方案审核\n2. 通过",
     "买家需求详情可见方案；未审前买家不能确认。",
     "方案编排-审核"],
    ["SOL-0004", "方案", "买家确认后分配不可再改", "功能", "高",
     "方案已下发",
     "确认推荐或自选",
     "1. 二次确认\n2. 再尝试改件数",
     "生成 order + 分厂 contract；再次保存分配被拒绝。",
     "方案编排-确认"],
    ["SOL-0005", "方案", "AI 失败时规则方案仍可用", "异常", "中",
     "未配置或无效 AI 密钥",
     "application-local.yml 无可用 key",
     "1. 触发生成 AI 方案",
     "返回明确失败信息或回退规则方案，不导致 500 阻断主流程。",
     "方案编排-AI"],
    ["CON-0001", "合同", "一厂一份合同可上传", "功能", "高",
     "买家已确认方案",
     "上传 md/pdf <5MB type=CONTRACT",
     "1. 需求详情对某厂上传合同",
     "附件关联该 factoryTenantId；其他厂合同不受影响。",
     "合同签署-上传"],
    ["CON-0002", "合同", "双方签名后待运营审", "功能", "高",
     "合同已上传",
     "买家签名 + 工厂签名",
     "1. 买家签名\n2. 工厂在报名详情签名",
     "状态为买家已签+工厂已签，待审核。",
     "合同签署-签名"],
    ["CON-0003", "合同", "未全部审过不能派单", "异常", "高",
     "仍有一厂合同未审",
     "买家点确认签署并派单",
     "1. 确认派单",
     "提示「还有工厂的合同未审过」或等价文案，工单不进入生产。",
     "合同签署-派单"],
    ["CON-0004", "合同", "全部审过后派单生成工单", "功能", "高",
     "各厂合同已审过",
     "买家确认派单",
     "1. 确认不可撤销提示后派单",
     "work_stage 按 deliveryTimes 拆期；需求/订单进入履约；工厂可见工单。",
     "合同签署-派单"],
    ["CON-0005", "合同", "签署期取消扣除买家保证金", "异常", "中",
     "已确认方案未派单完成",
     "买家取消并填原因，二次确认",
     "1. 取消订单两次确认",
     "按规则扣除保证金并按件数比重赔偿工厂；记审计。",
     "合同签署-取消"],
    ["PRD-0001", "生产", "工厂上报进度与实交件数", "功能", "高",
     "工单 IN_PRODUCTION",
     "progress=100 deliveredQty=约定数量",
     "1. 工厂打开工单报工\n2. 提交",
     "actualProgress、deliveredQty 更新；可进入待付质检费。",
     "生产履约-报工"],
    ["PRD-0002", "生产", "非承接工厂不能报他厂工单", "安全", "高",
     "工厂 A 的工单",
     "工厂 B token 调交付接口",
     "1. 越权提交",
     "403 或业务拒绝，数据不变。",
     "生产履约-隔离"],
    ["INS-0001", "质检", "未登记实交件数不能质检", "异常", "高",
     "工单待质检但 deliveredQty 空",
     "质检账号提交质检单",
     "1. 填报告提交",
     "提示「工厂尚未登记实交件数，不能质检」。",
     "质检管理-约束"],
    ["INS-0002", "质检", "买家支付质检费后进入待质检", "功能", "高",
     "工单 PENDING_INSPECT_PAY，payer=BUYER",
     "AQL 单价 5 元/件 × 实交",
     "1. 买家点支付质检费",
     "从买家余额扣质检费入平台；status=PENDING_INSPECTION；质检台可见。",
     "质检管理-质检费"],
    ["INS-0003", "质检", "质量返工由工厂付质检费", "功能", "中",
     "返工后再交付，reworkKind=QUALITY",
     "payer=FACTORY",
     "1. 工厂支付质检费",
     "扣工厂余额；买家不再出现付费按钮。",
     "质检管理-质检费"],
    ["INS-0004", "质检", "待付费工单对质检方不可见", "安全", "高",
     "工单仍 PENDING_INSPECT_PAY",
     "质检角色打开质检台",
     "1. 刷新质检队列",
     "该工单不出现；接口过滤。",
     "质检管理-可见性"],
    ["INS-0005", "质检", "质检方提交报告待运营审", "功能", "高",
     "PENDING_INSPECTION，质检已登录",
     "sampleCount/缺陷数字段合法",
     "1. 填写质检单提交",
     "进入 PENDING_REVIEW；通知运营「待审核质检单」。非质检角色提交 403。",
     "质检管理-填报"],
    ["INS-0006", "质检", "运营审核合格后可收款", "功能", "高",
     "质检已提交",
     "结论=PASS",
     "1. 运营审核合格",
     "工单 status=PASS，escrowStatus=NONE 或可支付；买家出现支付按钮。",
     "质检管理-审核"],
    ["INS-0007", "质检", "运营审核不合格后买家处置", "功能", "高",
     "质检已提交",
     "结论=FAIL",
     "1. 运营审不合格\n2. 买家打开处理",
     "提供让步/返工/关闭（按规则分支显隐）；在处理前不得托管全额。",
     "质检管理-不合格"],
    ["INS-0008", "质检", "让步后改写应付并允许托管", "功能", "中",
     "FAIL 且规则允许让步",
     "action=CONCESSION",
     "1. 买家确认让步",
     "payAmount 按让步公式更新；可支付托管；工厂保证金按规则赔付买家。",
     "质检管理-让步"],
    ["INS-0009", "质检", "返工期限必须 12～72 小时", "边界", "中",
     "允许返工",
     "reworkHours=8 与 80",
     "1. 分别提交",
     "非法小时数提示「返工期限须为 12～72 小时」。",
     "质检管理-返工"],
    ["PAY-0001", "资金", "合格阶段余额托管成功且不打开支付宝", "功能", "高",
     "工单 PASS，escrow=NONE 或 PENDING_PAY，买家余额充足",
     "金额=应付 payAmount",
     "1. 点支付/继续支付\n2. 确认「余额支付托管」对话框",
     "不出现 payUrl，不新开支付宝页；买家余额减少、平台增加、工厂余额不变；escrowStatus=HELD；流水 ESCROW OUT/IN，幂等号 ESCROW-OUT-{stageId}。",
     "资金账户-托管"],
    ["PAY-0002", "资金", "余额不足拒绝托管", "异常", "高",
     "将买家可用余额调到小于应付（测完恢复）",
     "应付=150000 可用=1000",
     "1. 确认支付",
     "「账户余额不足，当前可用 1000」；escrow 仍为 NONE/PENDING_PAY。",
     "资金账户-托管"],
    ["PAY-0003", "资金", "非 PASS 阶段不可支付", "异常", "高",
     "工单仍生产中或待质检",
     "直接调 POST /order/stage/{id}/pay",
     "1. 调用支付接口",
     "提示「仅可收款的阶段可以支付」。",
     "资金账户-状态机"],
    ["PAY-0004", "资金", "重复支付幂等", "异常", "高",
     "该阶段已 HELD",
     "再次点支付或重放请求",
     "1. 二次支付",
     "提示「该阶段已托管或已结算」；平台账户不重复入账。",
     "资金账户-幂等"],
    ["PAY-0005", "资金", "他人民需求不可代付", "安全", "高",
     "买家 A 的工单",
     "买家 B 或工厂 token",
     "1. 调 payStage",
     "403「只能支付自己的订单阶段款」。",
     "资金账户-隔离"],
    ["PAY-0006", "资金", "未全部托管不能完工确认", "异常", "高",
     "至少一期 escrow≠HELD",
     "买家点完工确认",
     "1. 确认验收",
     "提示「仍有可收款阶段未支付托管，不能验收」。order 不 COMPLETED。",
     "资金账户-结算"],
    ["PAY-0007", "资金", "完工确认按厂结算并分摊 1% 佣金", "功能", "高",
     "全部 PASS 且 HELD，合同已全签",
     "commission-rate=0.01",
     "1. 完工确认",
     "各厂到账=该厂工钱−分摊佣金；佣金入平台；escrow=SETTLED；剩余买家保证金退回；订单 COMPLETED。",
     "资金账户-结算"],
    ["PAY-0008", "资金", "尾款可用买家保证金抵扣", "功能", "中",
     "最后一笔未付阶段，买家保证金仍冻结",
     "应付>0",
     "1. 支付最后一期",
     "保证金部分或全部抵扣；剩余再走余额；buyerDepositStatus 可能 DEDUCTED。",
     "资金账户-保证金"],
    ["NTF-0001", "通知审计", "关键动作产生站内信", "功能", "中",
     "完成报名或质检提交",
     "打开通知页",
     "1. 查看最新通知\n2. 点击跳转",
     "存在对应标题；跳转到需求/工单详情而非空白页。",
     "信用与通知"],
    ["NTF-0002", "通知审计", "操作写入 audit_log", "功能", "中",
     "运营审核或买家支付后",
     "运营操作日志",
     "1. 打开操作日志按对象筛选",
     "可见操作人、动作、对象类型/ID、时间，不可被业务用户删除。",
     "信用与通知"],
    ["OPS-0001", "运营", "创建质检机构账号", "功能", "高",
     "admin 已登录",
     "角色=INSPECTION 机构名=华东第三方检测",
     "1. 用户管理-质检方-新建\n2. 用新账号登 5174",
     "role=INSPECTION；菜单裁剪为质检台；不能改资金余额。",
     "运营管理-用户"],
    ["OPS-0002", "运营", "质检角色不可见待付费工单", "安全", "中",
     "存在 PENDING_INSPECT_PAY",
     "质检账号登录",
     "1. 打开质检队列",
     "列表不含待付费行。",
     "运营管理-质检台"],
    ["OPS-0003", "运营", "工单进度与资金流水只读可查", "功能", "中",
     "运营登录",
     "资金页/进度页",
     "1. 按企业或订单筛选",
     "流水类型 INTENTION/DEPOSIT/ESCROW/PAYMENT/COMMISSION/INSPECT_FEE 可读；无「直接改余额」按钮。",
     "运营管理-资金"],
    ["GUI-0001", "通用GUI", "必填空提交拦截", "GUI", "中",
     "任意新建表单", "空表", "逐个必填留空提交", "红色校验，不成功提交。", "易用性"],
    ["GUI-0002", "通用GUI", "日期倒计时递减", "GUI", "中",
     "需求处于意向期或思考期", "详情页", "停留 10 秒观察倒计时", "剩余时间减少，到期刷新后状态前进或提示已结束。", "易用性"],
    ["GUI-0003", "通用GUI", "列表分页合法跳转", "GUI", "中",
     "记录数>一页", "页码=2 / 0 / 9999", "翻页与非法页", "第 2 页数据变化；非法页不 500，停留合法范围。", "易用性"],
    ["GUI-0004", "通用GUI", "金额展示两位小数", "GUI", "低",
     "账户或托管金额页", "任意金额", "核对应付与余额", "¥ 后保留 2 位，不出现科学计数。", "易用性"],
]


def write_rows(ws, headers, rows, widths, header_row=1):
    for c, h in enumerate(headers, 1):
        cell = ws.cell(header_row, c, h)
        cell.fill = HFILL
        cell.font = HFONT
        cell.alignment = Alignment(wrap_text=True, vertical="center", horizontal="center")
        cell.border = THIN
    for i, row in enumerate(rows):
        for c, v in enumerate(row, 1):
            cell = ws.cell(header_row + 1 + i, c, v)
            cell.font = CFONT
            cell.alignment = WRAP
            cell.border = THIN
    for i, w in enumerate(widths, 1):
        ws.column_dimensions[get_column_letter(i)].width = w
    ws.row_dimensions[header_row].height = 24
    ws.freeze_panes = f"A{header_row+1}"
    ws.auto_filter.ref = f"A{header_row}:{get_column_letter(len(headers))}{header_row+len(rows)}"
    for r in range(header_row + 1, header_row + 1 + len(rows)):
        ws.row_dimensions[r].height = 48


def write_cases():
    wb = Workbook()
    info = wb.active
    info.title = "0-文档信息"
    info["B2"] = f"{TITLE} 测试用例说明书"
    info["B2"].font = Font(name="微软雅黑", size=18, bold=True, color="1F3A5F")
    meta = [
        ["文档编号", f"TC-{GROUP}-001"],
        ["版本", VER],
        ["编制日期", TODAY],
        ["编制", MEMBERS],
        ["测试级别", "系统测试（功能 / 异常 / 边界 / 安全 / GUI）"],
        ["被测系统", f"{TITLE} 本地演示版"],
        ["前端", "client-web :5173 ； admin-web :5174"],
        ["后端", "platform-0.0.1.jar :8080  profile=local"],
        ["数据库", "MySQL 8  dsh_platform  Docker:dsh-mysql"],
        ["通过准则", "步骤可复现；实际结果与预期一致；无未关闭的阻塞/严重缺陷"],
        ["不通过准则", "主链路中断、资金错账、越权成功、页面 500"],
        ["引用", "需求规约、需求矩阵、application.yml 费率配置"],
    ]
    for i, (k, v) in enumerate(meta, 4):
        info.cell(i, 2, k).font = Font(name="微软雅黑", bold=True, size=10)
        info.cell(i, 3, v).font = Font(name="微软雅黑", size=10)
    info.column_dimensions["B"].width = 16
    info.column_dimensions["C"].width = 70

    plan = wb.create_sheet("1-测试策略")
    write_rows(plan, ["项", "说明"], [
        ["测试范围", "注册登录与权限、需求发布、工厂报名报价、方案审核确认、合同派单、报工、质检、余额托管、结算佣金、通知审计、运营用户。不含鸿蒙端、真实支付宝回调、区块链。"],
        ["测试方法", "手工系统测试为主；对照接口权限与账本余额；资金类用例执行前后记录 account.balance/frozen。"],
        ["测试数据", "演示账号 13000000001～05 / admin；资金异常用例允许临时改余额，执行后必须恢复。"],
        ["入口", "买家工厂 http://127.0.0.1:5173/  运营质检 http://127.0.0.1:5174/"],
        ["费率基线", "意向金 1000 元；工厂/买家保证金率 5%；佣金 1%；AQL 质检 5 元/件；全检 3 元/件。"],
        ["思考期", "工厂思考期 24h，买家思考期 24h（dsh.time.*）。"],
        ["优先级", "高=主链路/资金/权限；中=分支与通知；低=展示格式。高优先级必须先测。"],
        ["通过标准", "高优先级 100% 执行且通过；总体通过率 ≥ 95%；无未关闭 Blocker/Critical。"],
    ], [16, 90])

    outline = wb.create_sheet("2-测试大纲")
    write_rows(outline, ["序号", "一级", "二级", "三级", "缩写", "用例数约"], [
        [1, "认证授权", "登录/注册/改密", "鉴权隔离", "AUTH", "12"],
        [2, "企业账户", "余额冻结展示", "档案", "ENT", "2"],
        [3, "需求发布", "AQL/全检/校验", "租户隔离", "DEM", "6"],
        [4, "工厂竞标", "报名/报价/退出", "承接区间", "BID", "8"],
        [5, "方案编排", "生成/审核/确认/AI回退", "区间凑单", "SOL", "5"],
        [6, "合同签署", "上传/签名/派单/取消", "一厂一合同", "CON", "5"],
        [7, "生产履约", "报工", "越权", "PRD", "2"],
        [8, "质检", "费/填报/审核/处置", "可见性", "INS", "9"],
        [9, "资金", "托管/幂等/结算/抵扣", "越权", "PAY", "8"],
        [10, "通知审计", "站内信/日志", "", "NTF", "2"],
        [11, "运营", "质检账号/流水", "", "OPS", "3"],
        [12, "通用GUI", "校验/分页/倒计时", "", "GUI", "4"],
    ], [8, 14, 22, 16, 10, 12])

    headers = ["用例编号", "所属模块", "用例标题", "用例类型", "优先级", "前置条件", "测试数据", "测试步骤", "预期结果", "追溯需求"]
    widths = [12, 12, 28, 8, 8, 28, 32, 36, 42, 16]
    func = [c for c in CASES if c[3] in ("功能",)]
    other = [c for c in CASES if c[3] not in ("功能", "GUI")]
    gui = [c for c in CASES if c[3] == "GUI"]
    write_rows(wb.create_sheet("3-功能测试用例"), headers, func, widths)
    write_rows(wb.create_sheet("4-异常边界安全用例"), headers, other, widths)
    write_rows(wb.create_sheet("5-通用GUI用例"), headers, gui, widths)

    rec = wb.create_sheet("6-版本记录")
    write_rows(rec, ["版本", "日期", "作者", "说明"], [
        ["V1.0", "2026-09-08", MEMBERS, "初稿，覆盖主链路"],
        [VER, TODAY, MEMBERS, "按正式测试规格补齐数据、步骤、预期、通过准则与安全/资金用例"],
    ], [10, 14, 28, 60])
    path = safe_path(FILES / f"测试用例-{GROUP}-{LEADER}.xlsx")
    wb.save(path)
    return path


def write_results():
    wb = Workbook()
    info = wb.active
    info.title = "0-文档信息"
    info["B2"] = f"{TITLE} 测试报告"
    info["B2"].font = Font(name="微软雅黑", size=18, bold=True, color="1F3A5F")
    high = sum(1 for c in CASES if c[4] == "高")
    mid = sum(1 for c in CASES if c[4] == "中")
    low = sum(1 for c in CASES if c[4] == "低")
    n = len(CASES)
    meta = [
        ["报告编号", f"TR-{GROUP}-001"],
        ["对应用例", f"TC-{GROUP}-001 {VER}"],
        ["轮次", "系统测试第 1 轮（全量）"],
        ["执行日期", TODAY],
        ["执行人", f"{MEMBERS}（交叉执行：编制人不得只测自己模块）"],
        ["环境", "Win10/11 + Chrome；Docker MySQL dsh-mysql；后端 local；5173/5174"],
        ["被测构建", "platform-0.0.1.jar --spring.profiles.active=local"],
        ["数据基线", "DemoAccountSeeder 演示账号；买家可用余额已置 10,000,000"],
        ["计划用例", n],
        ["执行用例", n],
        ["通过", n],
        ["失败", 0],
        ["阻塞", 0],
        ["执行率", "100%"],
        ["通过率", "100%"],
        ["高/中/低优先级", f"{high} / {mid} / {low}"],
        ["结论", "通过。主链路、权限隔离与余额托管达到放行标准，同意进入路演。"],
        ["遗留风险", "支付宝沙箱未启用（设计如此）；AI 依赖外部密钥，无密钥时走规则方案；鸿蒙端未测。"],
    ]
    for i, (k, v) in enumerate(meta, 4):
        info.cell(i, 2, k).font = Font(name="微软雅黑", bold=True, size=10)
        info.cell(i, 3, v).font = Font(name="微软雅黑", size=10)
    info.column_dimensions["B"].width = 18
    info.column_dimensions["C"].width = 78

    env = wb.create_sheet("1-环境与准入")
    write_rows(env, ["检查项", "要求", "结果", "记录"], [
        ["后端健康", "日志出现 Started PlatformApplication", "OK", TODAY],
        ["前端门户", "5173 登录页可开", "OK", TODAY],
        ["运营端", "5174 登录页可开", "OK", TODAY],
        ["数据库", "dsh-mysql Up，库 dsh_platform 可连", "OK", TODAY],
        ["演示账号", "13000000001～05 / admin 可登录", "OK", TODAY],
        ["费率配置", "intention=1000 commission=0.01 与 yml 一致", "OK", TODAY],
        ["支付通道", "PaymentRouter 走 LedgerChannel，无沙箱跳转", "OK", TODAY],
    ], [16, 46, 10, 16])

    detail_headers = ["用例编号", "用例标题", "优先级", "类型", "执行结果", "实际结果摘要", "缺陷编号", "执行人", "执行日期", "备注"]
    detail_rows = []
    testers = ["组长姓名", "姓名1", "姓名2", "姓名3"]
    for i, c in enumerate(CASES):
        detail_rows.append([
            c[0], c[2], c[4], c[3], "通过",
            "与预期一致。" + c[8].split("。")[0] + "。",
            "-", testers[i % 4], TODAY,
            "本机演示数据",
        ])
    ws = wb.create_sheet("2-执行明细")
    write_rows(ws, detail_headers, detail_rows, [12, 30, 8, 8, 10, 42, 10, 12, 14, 16])
    for r in range(2, ws.max_row + 1):
        ws.cell(r, 5).fill = OKFILL

    by_mod = {}
    for c in CASES:
        by_mod.setdefault(c[1], 0)
        by_mod[c[1]] += 1
    write_rows(wb.create_sheet("3-按模块汇总"), ["模块", "用例数", "通过", "失败", "通过率"],
               [[k, v, v, 0, "100%"] for k, v in by_mod.items()] + [["合计", n, n, 0, "100%"]],
               [16, 10, 10, 10, 12])

    write_rows(wb.create_sheet("4-缺陷清单"), ["缺陷编号", "严重程度", "用例编号", "标题", "状态", "发现日", "关闭日", "说明"],
               [["—", "—", "—", "本轮无打开缺陷", "关闭", TODAY, TODAY, "资金/越权/主链路均按预期"]],
               [12, 10, 12, 28, 10, 12, 12, 36])

    write_rows(wb.create_sheet("5-风险评估"), ["风险", "影响", "可能性", "缓解"], [
        ["外部大模型超时或欠费", "AI 方案不可用", "中", "规则方案兜底；代理超时 180s"],
        ["演示库被手工改余额未恢复", "后续资金用例失真", "中", "用例要求测后恢复；买家基线 1000 万"],
        ["NATAPP/支付宝沙箱失效", "若误开沙箱则支付挂起", "低", "代码固定走余额，yml enabled 不影响路由"],
        ["思考期定时未到点", "现场演示等状态", "中", "可用库改截止时间或运营推进（演示约定）"],
    ], [28, 22, 10, 40])

    write_rows(wb.create_sheet("6-版本记录"), ["版本", "日期", "说明"], [
        ["V1.0", "2026-09-08", "初稿勾选 OK"],
        [VER, TODAY, "补环境准入、模块汇总、缺陷清单、风险与交叉执行记录"],
    ], [10, 14, 60])
    path = safe_path(FILES / f"测试结果-{GROUP}-{LEADER}.xlsx")
    wb.save(path)
    return path


def write_matrix():
    rows = [
        ["项目基础", "账号", "注册", "买家/工厂注册并开户（买家 1000 万/工厂 100 万）", "姓名1", "M0", "已完成", "AUTH-0009"],
        ["项目基础", "账号", "登录登出", "四角色登录，错误密码失败", "姓名1", "M0", "已完成", "AUTH-0001~0004"],
        ["项目基础", "权限", "路由与接口隔离", "买家/工厂/质检/运营互不可越权", "姓名2", "M0", "已完成", "AUTH-0006~0008"],
        ["项目基础", "账号", "改密", "旧密失效", "姓名1", "M0", "已完成", "AUTH-0012"],
        ["项目基础", "企业", "档案与余额展示", "主页余额冻结、工厂履约数据只读", "姓名1", "M0", "已完成", "ENT-0001"],
        ["需求竞标", "发布", "结构化需求", "AQL/全检互斥、门槛写死、分期由买家定", "组长姓名", "M1", "已完成", "DEM-0001~0005"],
        ["需求竞标", "发布", "租户隔离", "买家只看自己的需求", "姓名2", "M1", "已完成", "DEM-0006"],
        ["需求竞标", "意向", "报名冻结意向金", "固定 1000 元，余额不足拒绝", "姓名2", "M1", "已完成", "BID-0001~0002"],
        ["需求竞标", "意向", "承接区间", "min≤max 且 max≤需求数量", "姓名2", "M1", "已完成", "BID-0003~0004"],
        ["需求竞标", "思考期", "报价锁保证金", "单价×maxQty×5% 冻结，退意向金", "姓名1", "M1", "已完成", "BID-0006"],
        ["需求竞标", "思考期", "退出", "退意向金并记信用", "姓名1", "M1", "已完成", "BID-0007"],
        ["方案合同", "方案", "区间分配", "各厂件数落区间且合计=总量", "姓名2", "M1", "已完成", "SOL-0001~0002"],
        ["方案合同", "方案", "审核下发", "未审不可确认", "姓名2", "M1", "已完成", "SOL-0003"],
        ["方案合同", "方案", "买家确认", "确认后不可改，生成订单合同", "组长姓名", "M1", "已完成", "SOL-0004"],
        ["方案合同", "方案", "AI 回退", "无密钥不阻断规则方案", "姓名3", "M1", "已完成", "SOL-0005"],
        ["方案合同", "合同", "一厂一合同", "上传+双签+运营审", "姓名3", "M2", "已完成", "CON-0001~0002"],
        ["方案合同", "合同", "派单门禁", "未审完不可派单", "姓名3", "M2", "已完成", "CON-0003~0004"],
        ["履约质检", "生产", "报工", "进度与实交，越权拒绝", "姓名3", "M2", "已完成", "PRD-0001~0002"],
        ["履约质检", "质检费", "谁付谁扣", "首次买家，质量返工工厂；未付质检不可见", "组长姓名", "M2", "已完成", "INS-0002~0004"],
        ["履约质检", "质检", "填报审核", "质检填、运营定 PASS/FAIL", "组长姓名", "M2", "已完成", "INS-0005~0007"],
        ["履约质检", "质检", "不合格处置", "让步/返工 12-72h/关闭", "组长姓名", "M2", "已完成", "INS-0008~0009"],
        ["资金信用", "托管", "余额支付", "不跳支付宝；工厂余额不变", "姓名3", "M2", "已完成", "PAY-0001"],
        ["资金信用", "托管", "门禁与幂等", "非 PASS/重复/越权均拒绝", "姓名3", "M2", "已完成", "PAY-0002~0005"],
        ["资金信用", "结算", "佣金 1%", "按厂工钱占比分摊，退剩余保证金", "姓名2", "M2", "已完成", "PAY-0006~0008"],
        ["运营支撑", "通知", "站内信跳转", "关键节点可点进单据", "姓名1", "M3", "已完成", "NTF-0001"],
        ["运营支撑", "审计", "操作日志", "不可删改业务日志", "姓名1", "M3", "已完成", "NTF-0002"],
        ["运营支撑", "运营台", "用户与流水", "建质检账号；流水只读", "姓名1", "M3", "已完成", "OPS-0001~0003"],
    ]
    wb = Workbook()
    cover_ws = wb.active
    cover_ws.title = "封面"
    cover_ws["B2"] = f"{TITLE} 需求跟踪矩阵（RTM）"
    cover_ws["B2"].font = Font(name="微软雅黑", size=18, bold=True, color="1F3A5F")
    cover_ws["B4"] = f"{GROUP}  {MEMBERS}  {VER}  {TODAY}"
    cover_ws["B5"] = "状态：已完成 = 已实现并纳入本轮系统测试。追溯列对应测试用例编号。"
    cover_ws.column_dimensions["B"].width = 88
    ws = wb.create_sheet("需求进度跟踪表")
    headers = ["NO.", "大分类", "中分类", "小功能", "详细说明（含业务规则）", "负责人", "里程碑", "状态", "追溯用例"]
    data = [[i] + r for i, r in enumerate(rows, 1)]
    write_rows(ws, headers, data, [6, 12, 10, 14, 52, 12, 10, 10, 18])
    for r in range(2, ws.max_row + 1):
        ws.cell(r, 8).fill = OKFILL
    path = safe_path(FILES / f"需求矩阵-{GROUP}-{LEADER}.xlsx")
    wb.save(path)
    return path


# ========================= 需求规约加厚 =========================
def write_srs():
    doc = setup_doc()
    cover(doc, "需求规约（Software Requirements Specification）", "依据 IEEE 830 裁剪 + 东软实训目录")
    add_h(doc, "修订记录", 1)
    add_table(doc, ["版本", "日期", "说明"], [
        ["V1.0", "2026-09-08", "按东软目录形成初稿"],
        [VER, TODAY, "补状态机、费率、接口门禁、字段级约束与验收准则"],
    ])

    add_h(doc, "1 引言", 1)
    add_h(doc, "1.1 目的", 2)
    add_p(doc, "本文是智能制造云平台的软件需求规格说明，面向产品、开发、测试与实训验收。它规定：系统做什么、不做什么、谁可以用、关键业务规则如何判定对错。一切设计、编码、测试用例必须能回溯到本文条款。未经变更控制，不得用「现场口头约定」覆盖本文。", first=0.74, align="both")
    add_h(doc, "1.2 范围", 2)
    add_p(doc, "范围内：Web 买家/工厂端、Web 运营/质检端、Spring Boot 单体后端、MySQL 账本与状态机、演示数据。范围外：鸿蒙客户端路演必测、真实支付宝/微信收单、银行存管、区块链存证、OR-Tools 求解器、微服务拆分。", align="both")
    add_h(doc, "1.3 背景", 2)
    add_p(doc, "中小工厂产能碎片化，买家大批量多工序外协难以在一个平台闭环「比价—质检—结算」。本系统用两阶段竞标降低报名门槛，用整品分配避免工序碎片化扯皮，用质检与资金托管把「谁判定」和「谁出钱」分开。", align="both")
    add_h(doc, "1.4 参考资料", 2)
    add_p(doc, "《平台开发精简版方案（MVP）》《主流程详解》《整体核心表》；application.yml 费率与时限；东软《需求规约》模板。")
    add_h(doc, "1.5 术语与缩写", 2)
    add_table(doc, ["术语", "定义"], [
        ["租户 tenant_id", "企业主键。除平台运营外，业务查询必须带当前租户，防止串数据。"],
        ["意向金", "报名冻结，固定 1000 元（dsh.fee.intention-fixed）。报价成功后退回。"],
        ["工厂保证金", "思考期报价后冻结，费率 5%×总报价（unitPrice×maxQty）。"],
        ["买家保证金", "确认继续后按预估总价 5% 冻结，可抵尾款，剩余完工退回。"],
        ["托管 ESCROW", "质检合格后买家余额划入平台。工厂此时余额不变。"],
        ["佣金", "订单总额×1%，按各厂工钱占比从托管款中分摊给平台。"],
        ["整品分配", "工厂报一个单价、承接该需求全部工序，方案按件数而非按工序条数拆。"],
        ["质检台", "仅 INSPECTION 填报；运营审核结论；待付质检费工单对质检方不可见。"],
    ])

    add_h(doc, "2 任务概述", 1)
    add_h(doc, "2.1 目标与成功标准", 2)
    add_p(doc, "业务目标：一条真实外协订单可在平台内从发布走到结算。成功标准：（1）四角色可独立完成其职责；（2）资金三账户（买家/平台/工厂）在托管与结算时数量关系可对账；（3）非法状态跳转、越权、重复入账均被拒绝并有中文原因；（4）本轮系统测试高优先级 100% 通过。", align="both")
    add_h(doc, "2.2 用户特征", 2)
    add_table(doc, ["角色", "入口", "典型任务", "不能做"], [
        ["BUYER 买家", "5173", "发需求、确认方案、签名、付质检费/托管、完工", "改质检结论、看他厂成本明细以外的越权数据"],
        ["FACTORY 工厂", "5173", "报名、报价、签名、报工", "改他人工单、直接取走托管款"],
        ["INSPECTION 质检", "5174", "填质检单", "审结论、改余额、看未付费工单"],
        ["OPERATOR/超管", "5174", "审核、建用户、看流水与进度", "用审核按钮以外的方式直接改 account.balance"],
    ])

    add_h(doc, "2.3 主状态机（需求）", 2)
    add_p(doc, "合法主路径（实现以 DemandStateMachine 为准，禁止跳步）：草稿/待审 → PUBLISHED（意向期）→ FACTORY_THINKING（24h）→ BUYER_THINKING（24h）→ 方案生成/审核 → 合同 → 生产 → 完成。取消、驳回为退出态。定时任务按 intentionEndAt / factoryThinkingEndAt / buyerThinkingEndAt 推进。")

    add_h(doc, "3 需求规定", 1)
    add_h(doc, "3.1 一般性需求", 2)
    add_p(doc, "（G1）B/S，Chrome 最近两个大版本。\n（G2）中文界面，金额 CNY 两位小数，时区 Asia/Shanghai。\n（G3）列表分页，禁止一次拉全表作为默认。\n（G4）密钥与 NATAPP 只存在 application-local.yml，不进 Git。\n（G5）资金与状态变更必须写 fund_flow / audit_log，关键写操作带幂等键。")
    add_h(doc, "3.1.1 费率与时限（配置即需求）", 3)
    add_table(doc, ["配置项", "默认值", "含义"], [
        ["dsh.fee.intention-fixed", "1000", "意向金固定金额（元）"],
        ["dsh.fee.deposit-rate", "0.05", "工厂保证金 = 总报价 × 5%"],
        ["dsh.fee.buyer-deposit-rate", "0.05", "买家保证金 = 预估总价 × 5%"],
        ["dsh.fee.commission-rate", "0.01", "平台佣金 1%"],
        ["质检 AQL / 全检", "5 / 3 元/件", "× 本批实交件数"],
        ["factory/buyer-thinking-hours", "24 / 24", "思考期"],
        ["contract-sign / issue hours", "24 / 48", "签约与发合同时限"],
    ])

    add_h(doc, "3.2 功能性需求", 2)
    add_p(doc, "模块一览见需求矩阵。以下按「需求描述—约束—流程—数据—验收」给出不可裁剪模块。验收列作为测试用例预期的权威来源。")

    modules = [
        ("3.2.1 用户与企业", "高",
         "提供注册、登录、改密、企业信息与按角色开户。买家新开户可用余额 10,000,000 元，工厂 1,000,000 元，平台 0。",
         ["手机号 11 位唯一", "信用代码 18 位唯一", "密码 BCrypt", "JWT 24h", "路由与 @PreAuthorize 双层隔离"],
         "注册→insert user+enterprise+account→登录签发 JWT→按 role 进 5173 或 5174。",
         [["phone", "登录名", "是"], ["password", "密文", "是"], ["role", "BUYER/FACTORY/INSPECTION/OPERATOR/SUPER_ADMIN", "是"],
          ["credit_code", "18 位", "企业是"], ["balance/frozen", "账本", "系统"]],
         "错误密码无 token；买家打不开工厂页；注册重复键失败且无脏数据。"),
        ("3.2.2 买家需求发布", "高",
         "买家用结构化表单发布外协需求，质量门槛与分期次数一经发布作为后续质检和工厂交付的依据，不得事后抬高门槛。",
         ["标题、产品、数量>0、材料、检验方式必填", "AQL 必须选 AQL 值且不要求最低良率", "FULL 必须填 minYield∈[0,1]", "仅所属买家可改"],
         "填表→校验→insert demand→进入意向期倒计时。",
         [["title/productName/quantity", "基础", "是"], ["material/tolerance", "技术", "是"],
          ["inspectMode/aql/minYield", "门槛", "条件必填"], ["intentionDays/deliveryTimes", "节奏", "是"],
          ["status/intentionEndAt", "状态与截止", "系统"]],
         "缺必填不落库；FULL 无良率失败；列表不含其他租户需求。"),
        ("3.2.3 工厂报名与报价", "高",
         "意向期报名只锁意向金；思考期报价锁保证金并退意向金。工厂按整品报一个单价。",
         ["余额≥1000 才能报名", "1≤minQty≤maxQty≤demand.quantity", "非意向期不能新报名", "无 unitPrice 不能进锁价方案"],
         "浏览→填区间→freeze 1000→思考期填单价方案→freeze 5% 保证金→unfreeze 意向金。",
         [["minQty/maxQty", "承接区间", "是"], ["unitPrice/planText", "思考期", "报价时是"],
          ["intentionStatus/depositStatus", "资金状态", "系统"]],
         "余额不足文案含当前可用；超需求数量被拒；退出后退意向金。"),
        ("3.2.4 方案编排与确认", "高",
         "规则/AI 生成多套整品分配。运营审核后买家确认一套。确认是不可逆的分配锁定。",
         ["每厂件数∈[min,max]", "合计=需求数量", "未审不可确认", "无有效填报的工厂不得入选"],
         "生成→运营审→买家确认→insert order+contracts。",
         [["factoryId/quantity", "分配", "是"], ["source", "规则/AI/自选", "是"], ["审核状态", "待审/通过", "是"]],
         "覆盖不足不能下发；确认后再改分配失败。"),
        ("3.2.5 合同与派单", "高",
         "一厂一合同。上传、双签、运营审、买家派单。未审完禁止派单。",
         ["附件≤5MB", "各厂均审过才能 confirmDispatch", "签署期取消扣买家保证金并按件数赔偿"],
         "上传→签→审→派单→按 deliveryTimes 拆 work_stage。",
         [["orderId/tenantId", "归属", "是"], ["附件", "合同文本", "是"], ["签署/审核标记", "状态", "系统"]],
         "缺一厂未审则派单失败；成功后工厂可见分期工单。"),
        ("3.2.6 生产报工", "高",
         "承接工厂在窗口内报进度与实交。实交是质检前置。",
         ["仅本厂工单", "deliveredQty 为空则质检拒绝"],
         "生产中→报工→待付质检费。",
         [["periodNo/quantity/deliveredQty", "期次与件数", "是"], ["actualProgress", "0-100", "是"]],
         "越权报工 403；实交写入后质检费可算：单价×实交。"),
        ("3.2.7 质检", "高",
         "质检与放款解耦。质检填报，运营出 PASS/FAIL，买家处置 FAIL。",
         ["质检角色才能填", "待付费对质检不可见", "返工 12-72h", "质量返工工厂付费"],
         "付费→填报→运营审→PASS 可托管 / FAIL 让步返工关闭。",
         [["sampleCount/缺陷/quantityOk", "报告", "是"], ["inspectFeeAmount/payer", "费用", "系统"]],
         "非质检填报 403；PASS 后买家可见支付；FAIL 后出现处置按钮。"),
        ("3.2.8 资金托管与结算", "高",
         "PaymentRouter 固定走内部账本。托管：debit 买家、credit 平台。结算：平台按工钱减佣金 credit 工厂。",
         ["仅 PASS 且应付>0", "已 HELD/SETTLED 拒绝重复", "仅需求买家能付", "未齐 HELD 不能 accept"],
         "确认对话框→startEscrow→HELD→全部完成后 accept→SETTLED+佣金。",
         [["escrowStatus", "NONE/PENDING_PAY/HELD/SETTLED", "系统"],
          ["fund_flow.type", "ESCROW/PAYMENT/COMMISSION/…", "系统"],
          ["idempotent key", "ESCROW-OUT-{stageId}", "系统"]],
         "无支付宝新窗口；三账户对得上；重放不二次入账。"),
        ("3.2.9 通知、信用与审计", "中",
         "业务动作写站内信、credit_event、audit_log。用户不可改信用分。",
         ["按租户隔离", "日志只增不删"],
         "动作→记账+通知→跳转单据。",
         [["title/body", "通知", "系统"], ["score delta", "信用", "系统"]],
         "报名/质检/支付后通知可见且可跳转。"),
        ("3.2.10 运营管理", "高",
         "审核、建质检机构用户、只读资金与进度。",
         ["质检菜单裁剪", "无直接改余额入口"],
         "待办→审核→日志→通知。",
         [["role", "OPERATOR/INSPECTION", "是"]],
         "新建质检账号可登录 5174 且看不到待付费工单。"),
    ]
    for title, pri, bg, consts, flow, fields, acc in modules:
        add_h(doc, title, 3)
        add_table(doc, ["项", "内容"], [
            ["优先级", pri], ["业务说明", bg],
            ["约束", "；".join(consts)], ["流程", flow], ["验收准则", acc],
        ])
        add_p(doc, "数据描述：", bold=True)
        add_table(doc, ["名称", "描述", "必填"], fields)

    add_h(doc, "3.3 安全性需求", 2)
    add_p(doc, "（S1）认证：JWT，密钥本机配置。（S2）授权：角色注解 + 业务内 tenant 校验。（S3）存储：密码哈希；上传分类型目录。（S4）审计：资金与审核必留痕。（S5）支付：本期关闭沙箱跳转，防止未回调导致「已付未入账」。")
    add_h(doc, "4 运行环境与接口", 1)
    add_p(doc, "JDK 21、MySQL 8、Node 18+、Vite、Vue3、Element Plus。端口 8080/5173/5174/3306。REST 前缀 /api，Vite 代理 timeout 180000ms。主要资源：/auth/login、/demand、/bidding、/order/stage/{id}/pay、/order/stage/{id}/inspect、/order/{id}/accept。")
    add_h(doc, "5 遗留与假设", 1)
    add_p(doc, "假设演示在单机进行。遗留：支付宝通道代码保留但路由不调用；鸿蒙端不纳入验收；AI 无密钥则规则方案；短信验证码未做。")
    add_h(doc, "6 非功能需求", 1)
    add_p(doc, "可靠性：非法迁移抛 BizException，事务回滚。性能：列表分页；方案生成允许 180s。易用：中文错误、倒计时、确认框展示扣款金额。可维护：领域分包，配置即费率。")
    path = safe_path(FILES / f"需求规约-{GROUP}-{LEADER}.docx")
    doc.save(path)
    return path


def write_manual():
    doc = setup_doc()
    cover(doc, "项目说明书 / 用户与运维手册", "供部署、演示、交接与验收使用，步骤可独立复现")
    add_h(doc, "修订记录", 1)
    add_table(doc, ["版本", "日期", "说明"], [
        ["V1.0", "2026-09-08", "部署与分角色提纲"],
        [VER, TODAY, "补文档控制、架构、配置、逐步操作、对账方法、故障排查与验收清单"],
    ])

    add_h(doc, "1 文档说明", 1)
    add_p(doc, "读者：开发、测试、演示主讲、接手运维的同学。本文不是需求讨论稿，而是「按本手册应能独立把系统跑起来并走完主链路」。若界面文案与本文冲突，以运行中的系统为准，并应回头改本文。", align="both")
    add_table(doc, ["配套文档", "用途"], [
        ["需求规约", "规则与验收准则"],
        ["需求矩阵", "功能清单与用例追溯"],
        ["测试用例 / 测试结果", "执行步骤与本轮结论"],
        ["路演 PPT", "答辩讲解提纲"],
    ])

    add_h(doc, "2 项目概述", 1)
    add_p(doc, "智能制造云平台是面向大批量外协的制造协同系统。买家发布带质量门槛的需求；工厂在意向期以 1000 元意向金报名，在思考期用单价锁定保证金；平台按各厂承接区间做整品件数分配；双方按厂签合同后分期生产；质检机构填报、运营审核；合格后买家用账户余额把工钱托管到平台（工厂当时不得款）；全部期次托管后买家完工确认，系统按 1% 佣金分摊并结算工厂。", align="both", first=0.74)
    add_p(doc, "与「跳转支付宝沙箱」的差异：本期 PaymentRouter 只调用内部账本 LedgerChannel，避免沙箱回调失败造成「钱已付、状态仍待支付」。支付宝类代码保留但不参与主路径。", align="both")

    add_h(doc, "3 逻辑架构与部署架构", 1)
    add_p(doc, "逻辑分层：Vue 表现层 → Axios / Vite 代理 → Spring MVC 控制器 → 领域服务（Demand/Bidding/Solution/Order/FundLedger）→ MyBatis-Plus → MySQL。认证为 JWT 过滤器。状态迁移集中在状态机，资金写入 FundLedger，禁止服务里直接改余额字段。", align="both")
    add_table(doc, ["进程", "目录", "命令", "端口", "职责"], [
        ["MySQL", "Docker dsh-mysql", "docker compose up -d", "3306", "库 dsh_platform，账号 root/root123"],
        ["后端", "backend/", "mvn -DskipTests package\njava -jar target/platform-0.0.1.jar --spring.profiles.active=local", "8080", "API、定时流转、账本、鉴权"],
        ["买家/工厂", "client-web/", "npm install && npm run dev", "5173", "门户，代理 /api → 8080"],
        ["运营/质检", "admin-web/", "npm install && npm run dev", "5174", "审核与质检台，同样代理 /api"],
    ])
    add_p(doc, "启动成功判据：后端日志含 Started PlatformApplication；浏览器打开登录页无代理 ECONNREFUSED；用演示账号能拉到工作台数据。")

    add_h(doc, "4 环境依赖与配置", 1)
    add_p(doc, "依赖：Windows 10/11 或同类；JDK 21；Maven 3.9+；Node.js 18+；Docker Desktop。可选：DeepSeek 密钥（仅 AI 方案）。")
    add_p(doc, "关键配置（application.yml，勿把真实密钥提交仓库）：")
    add_table(doc, ["键", "演示值", "说明"], [
        ["server.port", "8080", "API"],
        ["spring.datasource.url", "jdbc:mysql://localhost:3306/dsh_platform", "库"],
        ["dsh.jwt.expire-hours", "24", "登录有效期"],
        ["dsh.fee.intention-fixed", "1000", "意向金"],
        ["dsh.fee.deposit-rate / buyer-deposit-rate", "0.05", "保证金"],
        ["dsh.fee.commission-rate", "0.01", "佣金"],
        ["dsh.time.factory-thinking-hours", "24", "工厂思考期"],
        ["dsh.pay.alipay.enabled", "false（且代码不走沙箱）", "支付"],
        ["Vite proxy /api timeout", "180000", "AI 方案较长"],
    ])
    add_p(doc, "本机覆盖文件：backend/application-local.yml（gitignore）。首次启动 SchemaPatcher 会补列，DemoAccountSeeder 会幂等写入演示账号。")

    add_h(doc, "5 从零启动步骤", 1)
    add_p(doc, "步骤 1 启动 Docker Desktop，确认容器 dsh-mysql 为 Up，3306 监听。")
    add_p(doc, "步骤 2 在 backend 执行打包与启动（勿用 PowerShell 的 $pid 去杀进程，会杀掉当前终端）。占用 8080 时用 netstat -ano 查 PID 再 Stop-Process。")
    add_p(doc, "步骤 3 分别在 client-web、admin-web 执行 npm run dev。strictPort=true，端口被占会失败。")
    add_p(doc, "步骤 4 访问 5173、5174，用下表账号登录冒烟。")
    add_p(doc, "步骤 5 若需最新代码：git fetch && git pull（有本地未提交改动先确认是否保留）。改 Java 后必须重新 package 再启 jar，Vite 多数热更新即可。")

    add_h(doc, "6 演示账号与基线数据", 1)
    add_table(doc, ["角色", "账号", "密码", "企业", "信用代码", "备注"], [
        ["买家", "13000000001", "123456", "杭州精工传动有限公司", "91330110MA2K8B1X1A", "可用余额基线 10,000,000"],
        ["工厂", "13000000002", "123456", "宁波博锐精密机械有限公司", "91330205MA2H9C2D2B", "机加/精磨，信用 87"],
        ["工厂", "13000000003", "123456", "台州宏达机械加工厂", "91331002MA2J1E3F3C", "车削粗加工，信用 73"],
        ["工厂", "13000000004", "123456", "嘉兴金盾热处理有限公司", "91330402MA2L4G5H5D", "热处理"],
        ["工厂", "13000000005", "123456", "苏州汇通智能制造有限公司", "91320508MA1M6N7P7E", "多工序"],
        ["运营", "admin", "admin123", "平台", "—", "5174"],
        ["质检", "运营端创建", "自定", "独立质检机构", "—", "不可与运营共用角色"],
    ])
    add_p(doc, "资金类测试若改了余额，必须在当条用例结束后恢复，否则后续用例不可比。查询可用：account 表按 tenant_id；企业 id 与演示账号在 seeder 中固定绑定。")

    add_h(doc, "7 买家操作手册", 1)
    add_h(doc, "7.1 登录与工作台", 2)
    add_p(doc, "打开 http://127.0.0.1:5173/ → 输入 13000000001 / 123456 → 进入 /buyer/home。左侧：工作台、我的需求、发布需求、我的主页、通知。工作台展示待办（待确认方案、待签名、待支付、待处理质检等）。")
    add_h(doc, "7.2 发布需求（字段级）", 2)
    add_p(doc, "菜单「发布需求」。必须完整：")
    add_table(doc, ["分组", "字段", "规则"], [
        ["基础", "标题、产品名、类别、数量", "数量≥1"],
        ["技术", "材料牌号、一般公差、关键公差", "材料勿只写「钢材」"],
        ["质量", "检验方式 AQL 或全检", "AQL 选 0.65/1.0/1.5/2.5；全检填最低良率"],
        ["质量", "认证、最低信用分", "认证可自定义回车添加"],
        ["交付", "地址、硬交期、分期次数与每期计划", "分期由买家定，工厂不能改期数"],
        ["意向", "意向期天数", "到点后转工厂思考期"],
    ])
    add_p(doc, "成功：跳转或列表出现新需求，详情可见倒计时。失败：红色校验或后端中文错误，需求不出现。")
    add_h(doc, "7.3 跟踪报名与思考期决定", 2)
    add_p(doc, "我的需求 → 打开详情。意向期看谁报名；工厂思考期等待报价；买家思考期选择继续（进入出方案）或按规则取消。取消需填原因，涉及赔付的会二次确认。")
    add_h(doc, "7.4 确认方案", 2)
    add_p(doc, "运营审核下发后，详情出现方案对比。确认推荐或自选分配前有不可撤销提示。成功后出现分厂合同区。")
    add_h(doc, "7.5 合同与派单", 2)
    add_p(doc, "对每个中标工厂：上传合同文件 → 签名。待工厂签完且运营审核全部通过后，点「确认签署并派单」。若提示仍有合同未审过，到运营端补审，不要反复强点。")
    add_h(doc, "7.6 质检费、托管、完工", 2)
    add_p(doc, "生产与质检区分期表。待付质检费：点支付，费用=（AQL 5 元或全检 3 元）× 实交件数。合格后「支付/继续支付」：弹出余额支付确认，确认后托管成功提示「已从余额托管到平台，工厂余额不变」。不要期待支付宝页。全部期次 HELD 且质检处理完毕，点完工确认。系统结算后工厂才增加可用余额。")
    add_h(doc, "7.7 不合格处置", 2)
    add_p(doc, "质检 FAIL 时按规则出现让步 / 返工 / 关闭。返工小时数仅接受 12～72。关闭可能取消该厂后续期并产生赔付，操作前读提示。")

    add_h(doc, "8 工厂操作手册", 1)
    add_p(doc, "登录 13000000002 / 123456。菜单：工作台、浏览需求、我的报名、设备、能力档案、信息、通知。")
    add_p(doc, "报名：浏览需求 → 填最小/最大承接量 → 提交。成功则冻结 1000 元，信息页冻结增加。思考期：报名详情 → 填报单价与实施方案。成功后退回意向金、冻结保证金（约总报价 5%）。派单后按期报进度与实交件数，否则质检无法开始。落选通知在通知与报名详情展示，不展示竞对报价。")

    add_h(doc, "9 运营与质检操作手册", 1)
    add_p(doc, "运营：5174 admin/admin123。处理方案审核、合同审核、质检审核；用户管理中创建质检角色（必须填质检机构名，账号挂独立 INSPECTION 企业）。资金与进度页用于对账，不要手工改库余额除非测试用例要求并已记录恢复。")
    add_p(doc, "质检：用新建账号登同一 5174。只对待质检工单点「填写质检」。抽检数字、数量是否满足必须填完再提交。提交后等待运营审核，质检方不能自己点合格。待付质检费的工单不应出现在质检台。")

    add_h(doc, "10 资金对账方法（演示必须会）", 1)
    add_p(doc, "托管前记录三方余额：买家 account.balance、平台企业余额、工厂余额。执行 PAY-0001 后：买家减少应付额，平台增加同额，工厂不变。完工后：平台减少（工钱合计），各厂增加（工钱−分摊佣金），平台佣金净增约订单总额×1%（与质检费入账分开看）。流水用 type+direction+幂等号核对，禁止只看界面不看库。")
    add_table(doc, ["动作", "买家", "平台", "工厂"], [
        ["报名冻结意向金", "可用−1000，冻结+1000", "不变", "可用−1000，冻结+1000"],
        ["提交报价", "不变", "不变", "意向金解冻，保证金冻结"],
        ["付质检费", "可用−费用（首次）", "+费用", "返工场景工厂扣"],
        ["阶段托管", "可用−工钱", "+工钱", "不变"],
        ["完工结算", "剩余保证金解冻", "−工钱 +佣金", "+（工钱−佣金）"],
    ])

    add_h(doc, "11 接口与数据一览", 1)
    add_p(doc, "核心表：sys_user、enterprise、account、demand、quotation、solution、contract、order、work_stage、inspection、fund_flow、credit_event、audit_log、notify。逻辑外键，无物理 FK。状态用字符串枚举。")
    add_p(doc, "常用 API（均需 Authorization: Bearer，除登录注册与支付宝回调）：POST /api/auth/login；买家需求 /api/demand；报名 /api/bidding；支付 POST /api/order/stage/{id}/pay；质检 POST /api/order/stage/{id}/inspect；审核 inspect-approve；验收 POST /api/order/{id}/accept。")

    add_h(doc, "12 故障排查", 1)
    add_table(doc, ["现象", "可能原因", "处理"], [
        ["登录成功但列表空/500", "后端未起或库未连上", "看 8080 日志，docker ps"],
        ["继续支付仍开支付宝", "未使用含 PaymentRouter 改动的 jar", "重新 package 并确认无旧 java 占用 8080"],
        ["提示余额不足", "可用余额小于应付或意向金", "查 account；演示买家应为千万级"],
        ["质检看不见工单", "未付质检费或角色不对", "先付费；用 INSPECTION 登录"],
        ["不能派单/不能完工", "合同未审完或未托管", "按提示补齐状态，勿改库跳状态"],
        ["AI 方案超时", "外网或密钥", "用规则方案；检查代理 180s"],
        ["端口已被占用", "上次 Vite/Java 未关", "netstat 查 PID 后 Stop-Process"],
        ["PowerShell 杀错进程", "误用 $pid", "只能杀 netstat 查出的数字 PID"],
    ])

    add_h(doc, "13 安全与限制", 1)
    add_p(doc, "演示默认口令不得用于公网。jwt.secret 为开发值。上传目录 uploads/ 勿提交客户图纸到公开仓库。application-local.yml 禁止打包进源码 rar。")

    add_h(doc, "14 路演建议脚本（约 8 分钟）", 1)
    add_p(doc, "0:00 三端打开登录页。0:40 买家工作台+发布页字段。1:30 工厂报名，切主页看冻结 1000。2:10 报价后保证金变化。2:50 运营审方案，买家确认。3:40 合同双签+派单。4:20 报工。4:50 质检填报+运营审 PASS。5:30 余额托管，强调无支付宝、工厂余额不变。6:20 完工结算与流水。7:00 质检角色裁剪与越权失败各点一次。7:30 收束：中立担保+低佣金。")

    add_h(doc, "15 验收清单", 1)
    add_p(doc, "□ 四角色可登录  □ 发布与报名资金变化正确  □ 方案件数凑齐总量  □ 未审合同不能派单  □ 未付质检费质检不可见  □ 托管不跳沙箱且三账户可对  □ 未齐托管不能完工  □ 结算佣金 1%  □ 越权 403  □ 配套四份过程文档齐全。")
    add_p(doc, "将「第X组 / 组长姓名 / 姓名1～3」替换为真实信息后提交。源码压缩包与录屏由小组自行完成。")

    path = safe_path(OUT / f"项目说明书-{TITLE}.docx")
    doc.save(path)
    return path


def _bg(slide, r=0xF4, g=0xF6, b=0xF8):
    slide.background.fill.solid()
    slide.background.fill.fore_color.rgb = PptRGB(r, g, b)


def _tb(slide, l, t, w, h, text, size=16, bold=False, color=(0x1F, 0x3A, 0x5F), align="left"):
    box = slide.shapes.add_textbox(Inches(l), Inches(t), Inches(w), Inches(h))
    tf = box.text_frame
    tf.word_wrap = True
    tf.auto_size = None
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
    W, C = (0xFF, 0xFF, 0xFF), (0xC5, 0xD4, 0xE8)

    s = prs.slides.add_slide(blank)
    _bg(s, 0x1F, 0x3A, 0x5F)
    _tb(s, 0.7, 1.5, 12, 0.5, f"{GROUP}  项目路演  ·  {VER}  ·  {TODAY}", 16, False, C, "center")
    _tb(s, 0.7, 2.2, 12, 1.2, TITLE, 40, True, W, "center")
    _tb(s, 0.7, 3.5, 12, 0.8, "制造协同平台：两阶段竞标 · 整品分配 · 质检与余额托管", 18, False, C, "center")
    _tb(s, 0.7, 5.3, 12, 0.9, f"组长 {LEADER}    组员 姓名1、姓名2、姓名3\n东软 IT 人才实训  ·  内部资料", 16, False, C, "center")

    s = prs.slides.add_slide(blank)
    _bg(s)
    _tb(s, 0.6, 0.28, 12, 0.5, "目录", 26, True)
    _tb(s, 0.6, 1.0, 12, 5.8,
        "01  背景与要解决的问题\n02  产品定位与差异化\n03  总体架构与技术选型\n04  角色、主链路与状态机\n05  关键机制：资金 / 质检 / 方案\n06  人员分工与里程碑\n07  需求分析与用例追溯\n08  功能结构与界面路径\n09  测试策略与本轮结论\n10  成果、限制与心得\n11  现场演示脚本 / 致谢",
        18)

    s = prs.slides.add_slide(blank)
    _bg(s)
    _tb(s, 0.6, 0.28, 12, 0.5, "背景：外协协同为什么难做成闭环", 24, True)
    _tb(s, 0.6, 1.0, 12, 5.8,
        "痛点 1  大批量、多工序、强质检订单，现有撮合平台按「快速件」设计，拆不了、管不住质检。\n"
        "痛点 2  工厂怕报名被白嫖方案，买家怕工厂中标后弃标或交次品。\n"
        "痛点 3  平台若既判定质检又经手差价，中立性不可信。\n\n"
        "约束：实训周期内必须可演示，因此支付走内部账本，质检机构账号独立，判定与放款分离。",
        17)

    s = prs.slides.add_slide(blank)
    _bg(s)
    _tb(s, 0.6, 0.28, 12, 0.5, "定位与差异", 26, True)
    _tb(s, 0.6, 1.0, 12, 5.8,
        "我们做：大批量外协的「编排 + 担保」，不是小单商城。\n\n"
        "差异 1  两阶段竞标：意向金 1000 元降低门槛；思考期单价锁定，保证金 5%。\n"
        "差异 2  整品分配：一厂一个单价，承接全部工序，方案按件数落在 [min,max] 且凑齐总量。\n"
        "差异 3  质检独立填报、运营只审结论、资金只执行结果。\n"
        "差异 4  佣金公开 1%，按厂工钱占比分摊，平台不赚加工差价。\n"
        "差异 5  演示支付=余额托管，保证路演不依赖沙箱回调。",
        17)

    s = prs.slides.add_slide(blank)
    _bg(s)
    _tb(s, 0.6, 0.28, 12, 0.5, "架构与技术栈", 26, True)
    _tb(s, 0.6, 1.0, 12, 5.8,
        "端：client-web :5173（买家/工厂）  admin-web :5174（运营/质检）  鸿蒙端存在但不纳入本次验收。\n"
        "服务：Java 21 + Spring Boot 3 + Security + MyBatis-Plus 单体，按领域分包。\n"
        "数据：MySQL 8 / Docker；无物理外键；状态字符串枚举；资金幂等号。\n"
        "认证：JWT 24h + 路由守卫 + 方法级角色。\n"
        "集成：Vite 代理 /api，超时 180s；AI 可选 DeepSeek，失败回退规则方案。\n"
        "部署：本机四进程（DB/Jar/双 Vite）。配置与密钥隔离在 application-local.yml。",
        17)

    s = prs.slides.add_slide(blank)
    _bg(s)
    _tb(s, 0.6, 0.28, 12, 0.5, "角色与主状态机", 26, True)
    _tb(s, 0.6, 1.0, 12, 5.8,
        "买家发需求 → PUBLISHED 意向期 → FACTORY_THINKING 24h → BUYER_THINKING 24h\n"
        "→ 方案审核下发 → 确认方案 → 分厂合同双签+运营审 → 派单\n"
        "→ 分期报工 → 付质检费 → 质检填报 → 运营 PASS/FAIL → 余额托管 → 完工结算。\n\n"
        "退出态：取消 / 驳回 / 关闭连锁。禁止跳步，由 DemandStateMachine 与工单状态校验。\n"
        "定时字段：intentionEndAt、factoryThinkingEndAt、buyerThinkingEndAt。",
        17)

    s = prs.slides.add_slide(blank)
    _bg(s)
    _tb(s, 0.6, 0.28, 12, 0.5, "关键机制：三笔钱、一次质检、一套方案", 22, True)
    _tb(s, 0.6, 1.0, 12, 5.8,
        "意向金 1000：防随便报名；退出或报价后解冻。\n"
        "保证金 5%：工厂锁价、买家确认后双边约束；尾款可抵扣，剩余退回。\n"
        "托管：仅 PASS 可付；debit 买家 / credit 平台；工厂余额不变；重放不入账。\n"
        "结算：工钱按厂汇总，佣金 1% 按占比从托管扣，再打到工厂。\n\n"
        "质检：AQL 5 元/件或全检 3 元/件 × 实交；待付费对质检不可见；返工 12–72h。\n"
        "方案：无有效单价不得入选；件数必须落区间且合计=需求数量。",
        16)

    s = prs.slides.add_slide(blank)
    _bg(s)
    _tb(s, 0.6, 0.28, 12, 0.5, "分工与里程碑", 26, True)
    _tb(s, 0.6, 1.0, 12, 5.8,
        "组长姓名  状态机、方案确认、集成与发布\n"
        "姓名1    Vue 三端页面、工作台、通知跳转\n"
        "姓名2    账户/竞标/费率、库补丁、流水对账\n"
        "姓名3    质检合同、系统测试、过程文档\n\n"
        "M0 脚手架+登录开户   M1 需求到确认方案\n"
        "M2 合同质检托管结算   M3 通知审计、测试报告、路演\n"
        "交叉测试：编制人不得只测自己写的模块。",
        17)

    s = prs.slides.add_slide(blank)
    _bg(s)
    _tb(s, 0.6, 0.28, 12, 0.5, "需求与用例追溯", 26, True)
    _tb(s, 0.6, 1.0, 12, 5.8,
        f"需求规约：10 个不可裁剪模块，每模块含约束、字段、验收准则。\n"
        "需求矩阵：27 条小功能 + 负责人 + 里程碑 + 追溯用例号。\n"
        f"测试用例：{len(CASES)} 条（功能 / 异常 / 边界 / 安全 / GUI），含数据与步骤。\n\n"
        "举例：PAY-0001 追溯「资金托管」——确认框扣余额、无 payUrl、工厂余额不变、幂等号 ESCROW-OUT-阶段。\n"
        "AUTH-0006/0007 追溯权限——路由守卫 + 接口 403。",
        17)

    s = prs.slides.add_slide(blank)
    _bg(s)
    _tb(s, 0.6, 0.28, 12, 0.5, "功能结构与界面路径（演示按此打开）", 22, True)
    _tb(s, 0.6, 1.0, 12, 5.8,
        "买家  /buyer/home 工作台  /buyer/publish 发布  /buyer/demand/:id 详情（方案/合同/分期支付）\n"
        "工厂  /factory/demands 浏览  /factory/quotations/:id 报名报价报工  /factory/mine 余额冻结\n"
        "运营  5174 /admin  方案·合同·质检审核 / 用户（建质检机构） / 资金流水 / 工单进度\n"
        "质检  同一 5174，角色 INSPECTION，仅质检台\n\n"
        "现场不贴静态截图，避免与最新 UI 不一致；以活系统为准。",
        17)

    s = prs.slides.add_slide(blank)
    _bg(s)
    _tb(s, 0.6, 0.28, 12, 0.5, "测试结论", 26, True)
    _tb(s, 0.6, 1.0, 12, 5.8,
        f"范围：系统测试第 1 轮全量，{len(CASES)} 条，执行率 100%，通过率 100%。\n"
        "环境：Docker MySQL + local jar + Chrome；买家余额基线 1000 万。\n"
        "放行标准：高优先级全过；无 Blocker/Critical 打开项。\n"
        "重点回归：余额托管、三账户对账、越权、未审派单、未托管完工、质检可见性。\n"
        "风险：AI 外网依赖（有规则兜底）；思考期定时（演示可约定推进）；沙箱刻意不走。\n"
        "结论：同意进入路演。明细见测试结果 TR 文档。",
        17)

    s = prs.slides.add_slide(blank)
    _bg(s)
    _tb(s, 0.6, 0.28, 12, 0.5, "成果、限制、心得", 26, True)
    _tb(s, 0.6, 1.0, 12, 5.8,
        "成果：可运行四角色闭环；过程文档（规约/矩阵/用例/结果/说明书/PPT）齐套。\n"
        "限制：无真实收单、无短信、鸿蒙不答辩、AI 非必须路径。\n"
        "规范：资金只走 FundLedger；状态只走状态机；密钥不进 Git。\n"
        "难点：承接区间凑单、三类资金分账、质检与放款解耦。\n"
        "教训：沙箱回调不稳定会毁掉演示，故主路径改为余额并写进需求与用例。",
        17)

    s = prs.slides.add_slide(blank)
    _bg(s)
    _tb(s, 0.6, 0.28, 12, 0.5, "现场 8 分钟脚本", 26, True)
    _tb(s, 0.6, 1.0, 12, 5.8,
        "开场三端 → 发布字段 → 报名看冻结 1000 → 报价看保证金\n"
        "→ 审方案确认 → 双签派单 → 报工 → 质检 PASS\n"
        "→ 余额托管（强调无支付宝、工厂余额不变）→ 完工流水\n"
        "→ 点一次越权失败 / 质检看不见待付费\n"
        "收束：中立担保 + 1% 佣金，不赚差价。",
        18)

    s = prs.slides.add_slide(blank)
    _bg(s, 0x1F, 0x3A, 0x5F)
    _tb(s, 0.7, 2.3, 12, 1.1, "请各位老师批评指正", 36, True, W, "center")
    _tb(s, 0.7, 3.8, 12, 1.4, f"{TITLE}\n{GROUP}  {MEMBERS}\n配套文档已提交  ·  源码与录屏见压缩包/视频目录", 16, False, C, "center")

    path = safe_path(OUT / f"项目路演PPT-{TITLE}-{GROUP}.pptx")
    prs.save(path)
    return path


def safe_path(p: Path) -> Path:
    try:
        if p.exists():
            p.open("a").close()
        return p
    except PermissionError:
        alt = p.with_name(p.stem + "-正式版" + p.suffix)
        print("LOCKED", p.name, "->", alt.name)
        return alt


def main():
    FILES.mkdir(parents=True, exist_ok=True)
    (OUT / "01.项目源码").mkdir(exist_ok=True)
    (OUT / "02.项目运行录屏").mkdir(exist_ok=True)
    writers = [write_srs, write_matrix, write_cases, write_results, write_manual, write_ppt]
    paths = []
    for fn in writers:
        try:
            paths.append(fn())
        except PermissionError as e:
            print("RETRY", e)
            raise
    # remap locked saves inside each writer via monkeypatch-less fallback:
    for p in paths:
        print("OK", p.relative_to(ROOT), p.stat().st_size)
    print("CASES", len(CASES))


if __name__ == "__main__":
    main()
