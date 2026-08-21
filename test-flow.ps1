$ErrorActionPreference = "Stop"
$base = "http://localhost:8080/api"
$suffix = Get-Random -Minimum 1000 -Maximum 9999

function Post($url, $body, $token) {
    $headers = @{}
    if ($token) { $headers["Authorization"] = "Bearer $token" }
    $json = $body | ConvertTo-Json -Depth 10
    return Invoke-RestMethod -Uri "$base$url" -Method Post -Body $json -ContentType "application/json; charset=utf-8" -Headers $headers
}
function Get($url, $token) {
    $headers = @{}
    if ($token) { $headers["Authorization"] = "Bearer $token" }
    return Invoke-RestMethod -Uri "$base$url" -Method Get -Headers $headers
}

Write-Output "=== 1. 注册买家 ==="
Post "/auth/register" @{phone="buyer$suffix"; password="123456"; companyName="买家$suffix"; creditCode="BUY$suffix"; type="BUYER"; contactName="张三"} $null
Write-Output "  买家注册 OK"

Write-Output "=== 2. 注册工厂1 ==="
Post "/auth/register" @{phone="fac1$suffix"; password="123456"; companyName="精加工厂$suffix"; creditCode="F1$suffix"; type="FACTORY"; contactName="李四"} $null
Write-Output "  工厂1注册 OK"

Write-Output "=== 3. 注册工厂2 ==="
Post "/auth/register" @{phone="fac2$suffix"; password="123456"; companyName="粗加工厂$suffix"; creditCode="F2$suffix"; type="FACTORY"; contactName="王五"} $null
Write-Output "  工厂2注册 OK"

Write-Output "=== 4. 登录三方 ==="
$buyer = (Post "/auth/login" @{phone="buyer$suffix"; password="123456"} $null).data
$fac1  = (Post "/auth/login" @{phone="fac1$suffix"; password="123456"} $null).data
$fac2  = (Post "/auth/login" @{phone="fac2$suffix"; password="123456"} $null).data
$admin = (Post "/auth/login" @{phone="admin"; password="admin123"} $null).data
Write-Output "  买家/工厂1/工厂2/运营 登录 OK"

$bt = $buyer.token; $f1t = $fac1.token; $f2t = $fac2.token; $at = $admin.token

Write-Output "=== 5. 买家发布需求（两工序） ==="
$demandId = (Post "/demand/publish" @{
    title="精密齿轮订单$suffix"; productName="齿轮"; category="精密件"; quantity=1000000
    material="合金钢"; tolerance="±0.01mm"; surfaceTreatment="淬火"; aql="AQL2.5"; certification="ISO9001"
    minYield=0.97; minCreditScore=60; deadlineHard="2026-12-31"; deadlineFlexible=$null
    deliveryAddress="上海"; packaging="木箱"; multiProcess=1; intentionDays=5
    weightJson='{"cost":0.34,"time":0.33,"quality":0.33}'; remark="测试订单"
    processes=@(
        @{processNo=1; processName="粗加工"; quantity=1000000; requirement="去毛刺"},
        @{processNo=2; processName="精加工"; quantity=1000000; requirement="高精度"}
    )
} $bt).data
Write-Output "  需求发布 OK，id=$demandId"

Write-Output "=== 6. 运营审核通过 ==="
Post "/demand/$demandId/audit" @{result="PASS"} $at
Write-Output "  审核通过，进入意向期 OK"

Write-Output "=== 7. 工厂1/工厂2 意向报名 ==="
Post "/bidding/intention" @{demandId=$demandId; processNo=2; intentionPrice=500000; minQty=300000; maxQty=600000; validDays=7} $f1t
Post "/bidding/intention" @{demandId=$demandId; processNo=1; intentionPrice=400000; minQty=300000; maxQty=600000; validDays=7} $f2t
Write-Output "  两家工厂意向报名 OK"

Write-Output "=== 8. 运营结束意向期 ==="
Post "/flow/$demandId/end-intention" $null $at
Write-Output "  意向期结束，进入思考期 OK"

Write-Output "=== 9. 买家思考期继续 ==="
Post "/flow/$demandId/decide?action=CONTINUE" $null $bt
Write-Output "  买家继续，进入保证金期 OK"

Write-Output "=== 10. 工厂锁定报价 ==="
Post "/bidding/lock" @{demandId=$demandId; processNo=2; price=520000; yieldRate=0.98; promisedDays=10; minQty=400000; maxQty=600000; stageCurveJson='[]'} $f1t
Post "/bidding/lock" @{demandId=$demandId; processNo=1; price=430000; yieldRate=0.97; promisedDays=8; minQty=400000; maxQty=600000; stageCurveJson='[]'} $f2t
Write-Output "  两家工厂锁定报价 OK"

Write-Output "=== 11. 运营结束保证金期 ==="
Post "/flow/$demandId/end-locking" $null $at
Write-Output "  保证金期结束 OK"

Write-Output "=== 12. 运营生成方案 ==="
$solutions = (Post "/solution/$demandId/generate" $null $at).data
Write-Output "  生成方案数：$($solutions.Count)"
$solId = $solutions[0].id

Write-Output "=== 13. 买家选方案 ==="
$orderId = (Post "/order/$demandId/select/$solId" $null $bt).data
Write-Output "  订单生成，orderId=$orderId"

Write-Output "=== 14. 买家签约 ==="
Post "/order/$orderId/sign" $null $bt
Write-Output "  签约成功，拆工单 OK"

Write-Output "=== 15. 工厂交付 ==="
$stages = (Get "/order/$orderId/stages" $bt).data
foreach ($s in $stages) {
    Post "/order/stage/$($s.id)/deliver" $null $f1t
    Post "/order/stage/$($s.id)/deliver" $null $f2t
}
Write-Output "  工单交付 OK（工单数：$($stages.Count)）"

Write-Output "=== 16. 运营质检合格 ==="
$stages = (Get "/order/$orderId/stages" $bt).data
foreach ($s in $stages) {
    Post "/order/stage/$($s.id)/inspect?result=PASS" $null $at
}
Write-Output "  质检合格，放款 OK"

Write-Output "=== 17. 买家整体验收 ==="
Post "/order/$orderId/accept" $null $bt
Write-Output "  验收完成，订单完成 OK"

Write-Output ""
Write-Output "========== 全流程跑通！ =========="
