package com.dsh.platform.dto;

public class AuthDtos {

    public record RegisterRequest(String phone, String password, String companyName,
                                  String creditCode, String type, String contactName, String address) {}

    public record LoginRequest(String phone, String password) {}

    public record CreateUserRequest(String phone, String password, String realName, String role,
                                    String orgName) {}

    public record ChangePasswordRequest(String oldPassword, String newPassword) {}

    public record LoginResponse(String token, String role, Long tenantId, String name, Long avatarId) {}

    public record MeResponse(String role, Long tenantId, String name, Long avatarId) {}

    public record UpdateEnterpriseRequest(String name, String creditCode, String contactName,
                                          String address, Integer creditScore, String authStatus) {}

    public record UpdateAccountRequest(String phone, String realName, String status, String password) {}
}
