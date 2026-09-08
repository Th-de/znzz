package com.dsh.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsh.platform.common.BizException;
import com.dsh.platform.entity.Attachment;
import com.dsh.platform.entity.Contract;
import com.dsh.platform.entity.Demand;
import com.dsh.platform.entity.Order;
import com.dsh.platform.entity.WorkStage;
import com.dsh.platform.entity.Quotation;
import com.dsh.platform.mapper.AttachmentMapper;
import com.dsh.platform.mapper.ContractMapper;
import com.dsh.platform.mapper.DemandMapper;
import com.dsh.platform.mapper.OrderMapper;
import com.dsh.platform.mapper.QuotationMapper;
import com.dsh.platform.mapper.WorkStageMapper;
import com.dsh.platform.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileService {

    private static final Set<String> ALLOWED = Set.of(
            "pdf", "png", "jpg", "jpeg", "dwg", "dxf", "stp", "step", "zip", "md"
    );

    private final AttachmentMapper attachmentMapper;
    private final ContractMapper contractMapper;
    private final DemandMapper demandMapper;
    private final OrderMapper orderMapper;
    private final WorkStageMapper workStageMapper;
    private final QuotationMapper quotationMapper;

    @Value("${dsh.upload.dir:uploads}")
    private String uploadDir;

    public Map<String, Object> upload(MultipartFile file, String bizType) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择文件");
        }
        if (file.getSize() > 5L * 1024 * 1024) {
            throw new BizException("文件不能超过 5MB");
        }
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!ALLOWED.contains(ext)) {
            throw new BizException("不支持的文件类型");
        }
        String type = (bizType == null || bizType.isBlank()) ? "TEMP" : bizType;
        if ("AVATAR".equals(type)) {
            if (!Set.of("png", "jpg", "jpeg").contains(ext)) {
                throw new BizException("头像仅支持 png/jpg");
            }
        }
        try {
            Path dir = Path.of(uploadDir).toAbsolutePath();
            Files.createDirectories(dir);
            String stored = UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);
            Path dest = dir.resolve(stored);
            file.transferTo(dest.toFile());

            Attachment a = new Attachment();
            a.setBizType(type);
            a.setBizId(0L);
            a.setFileName(original);
            a.setFilePath(dest.toString());
            a.setFileSize(file.getSize());
            a.setFileType(ext);
            a.setUploaderId(UserContext.userId());
            attachmentMapper.insert(a);
            return Map.of("id", a.getId(), "fileName", original);
        } catch (IOException e) {
            throw new BizException("文件保存失败");
        }
    }

    public List<Attachment> listByBiz(String bizType, Long bizId) {
        return attachmentMapper.selectList(new LambdaQueryWrapper<Attachment>()
                .eq(Attachment::getBizType, bizType)
                .eq(Attachment::getBizId, bizId)
                .orderByDesc(Attachment::getId));
    }

    public Long bind(Long attachmentId, String bizType, Long bizId) {
        if (attachmentId == null) return null;
        Attachment a = attachmentMapper.selectById(attachmentId);
        if (a == null) throw new BizException("附件不存在");
        if (a.getBizId() != null && a.getBizId() > 0 && !a.getBizId().equals(bizId)) {
            Attachment copy = new Attachment();
            copy.setBizType(bizType);
            copy.setBizId(bizId);
            copy.setFileName(a.getFileName());
            copy.setFilePath(a.getFilePath());
            copy.setFileSize(a.getFileSize());
            copy.setFileType(a.getFileType());
            copy.setUploaderId(UserContext.userId());
            attachmentMapper.insert(copy);
            return copy.getId();
        }
        a.setBizType(bizType);
        a.setBizId(bizId);
        attachmentMapper.updateById(a);
        return a.getId();
    }

    public ResponseEntity<Resource> download(Long id) {
        Attachment a = requireReadable(id);
        Path path = Path.of(a.getFilePath());
        if (!Files.exists(path)) {
            throw new BizException("文件已丢失");
        }
        String name = a.getFileName() == null ? "file" : a.getFileName();
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(path));
    }

    public ResponseEntity<Resource> preview(Long id) {
        Attachment a = requireReadable(id);
        Path path = Path.of(a.getFilePath());
        if (!Files.exists(path)) {
            throw new BizException("文件已丢失");
        }
        String ext = a.getFileType() == null ? "" : a.getFileType().toLowerCase();
        MediaType mt = switch (ext) {
            case "png" -> MediaType.IMAGE_PNG;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
        return ResponseEntity.ok()
                .contentType(mt)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(new FileSystemResource(path));
    }

    private Attachment requireReadable(Long id) {
        Attachment a = attachmentMapper.selectById(id);
        if (a == null) {
            throw new BizException("附件不存在");
        }
        String role = UserContext.role();
        if ("OPERATOR".equals(role) || "SUPER_ADMIN".equals(role) || "INSPECTION".equals(role)) {
            return a;
        }
        if ("CONTRACT".equals(a.getBizType())) {
            Contract c = contractMapper.selectOne(new LambdaQueryWrapper<Contract>()
                    .eq(Contract::getAttachmentId, a.getId())
                    .last("limit 1"));
            if (c == null && a.getBizId() != null && a.getBizId() > 0) {
                c = contractMapper.selectById(a.getBizId());
            }
            if (c == null) {
                throw new BizException(403, "无权下载该文件");
            }
            assertCanViewContract(c);
            return a;
        }
        if ("PROGRESS".equals(a.getBizType()) && a.getBizId() != null && a.getBizId() > 0) {
            WorkStage ws = workStageMapper.selectById(a.getBizId());
            if (ws == null) {
                throw new BizException(403, "无权下载该文件");
            }
            if ("FACTORY".equals(role) && UserContext.tenantId().equals(ws.getTenantId())) {
                return a;
            }
            if ("BUYER".equals(role)) {
                Order o = orderMapper.selectById(ws.getOrderId());
                Demand d = o == null ? null : demandMapper.selectById(o.getDemandId());
                if (d != null && UserContext.tenantId().equals(d.getTenantId())) {
                    return a;
                }
            }
            throw new BizException(403, "无权下载该文件");
        }
        if ("DEMAND".equals(a.getBizType()) && a.getBizId() != null && a.getBizId() > 0) {
            Demand d = demandMapper.selectById(a.getBizId());
            if (d != null && UserContext.tenantId().equals(d.getTenantId())) {
                return a;
            }
            if (d != null && "FACTORY".equals(role)) {
                boolean bid = factoryBidOn(d.getId());
                if (bid || "PUBLISHED".equals(d.getStatus()) || "FACTORY_THINKING".equals(d.getStatus())
                        || "LOCKING".equals(d.getStatus())) {
                    return a;
                }
            }
        }
        if ("AVATAR".equals(a.getBizType())) {
            if (UserContext.userId() != null && UserContext.userId().equals(a.getUploaderId())) {
                return a;
            }
            if (UserContext.userId() != null && a.getBizId() != null && UserContext.userId().equals(a.getBizId())) {
                return a;
            }
            throw new BizException(403, "无权查看该文件");
        }
        if (UserContext.userId() != null && UserContext.userId().equals(a.getUploaderId())) {
            return a;
        }
        throw new BizException(403, "无权下载该文件");
    }

    private boolean factoryBidOn(Long demandId) {
        if (UserContext.tenantId() == null || demandId == null) {
            return false;
        }
        Long n = quotationMapper.selectCount(new LambdaQueryWrapper<Quotation>()
                .eq(Quotation::getDemandId, demandId)
                .eq(Quotation::getTenantId, UserContext.tenantId()));
        return n != null && n > 0;
    }

    private void assertCanViewContract(Contract c) {
        String role = UserContext.role();
        if ("FACTORY".equals(role)) {
            if (!UserContext.tenantId().equals(c.getTenantId())) {
                throw new BizException(403, "无权下载该文件");
            }
            return;
        }
        if ("BUYER".equals(role)) {
            Order o = orderMapper.selectById(c.getOrderId());
            Demand d = o == null ? null : demandMapper.selectById(o.getDemandId());
            if (d == null || !UserContext.tenantId().equals(d.getTenantId())) {
                throw new BizException(403, "无权下载该文件");
            }
            return;
        }
        throw new BizException(403, "无权下载该文件");
    }
}
