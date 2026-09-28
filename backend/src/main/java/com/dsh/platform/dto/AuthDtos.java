package com.dsh.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AuthDtos {

    public record RegisterRequest(
            @NotBlank(message = "请填写手机号")
            @Pattern(regexp = "^\\s*\\d{11}\\s*$", message = "手机号必须为11位数字")
            String phone,
            @NotBlank(message = "请填写密码")
            @Size(min = 6, max = 64, message = "密码至少 6 位")
            String password,
            @NotBlank(message = "请填写企业名称") @Size(max = 128) String companyName,
            @NotBlank(message = "请填写18位统一社会信用代码")
            @Pattern(regexp = "^\\s*[0-9A-Za-z]{18}\\s*$", message = "请填写18位统一社会信用代码")
            String creditCode,
            @NotBlank(message = "请选择企业类型") String type,
            @NotBlank(message = "请填写联系人") String contactName,
            @NotBlank(message = "请填写企业地址") String address) {}

    /** 运营/质检账号用用户名登录，买家/工厂用手机号，因此这里只做非空校验。 */
    public record LoginRequest(
            @NotBlank(message = "请填写账号") String phone,
            @NotBlank(message = "请填写密码") String password) {}

    public record CreateUserRequest(String phone, String password, String realName, String role,
                                    String orgName) {}

    public record ChangePasswordRequest(String oldPassword, String newPassword) {}

    public record LoginResponse(String token, String role, Long tenantId, String name, Long avatarId) {}

    public record MeResponse(String role, Long tenantId, String name, Long avatarId) {}

    public record UpdateEnterpriseRequest(String name, String creditCode, String contactName,
                                          String address, Integer creditScore, String authStatus) {}

    public record UpdateAccountRequest(String phone, String realName, String status, String password) {}
}
