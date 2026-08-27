package com.dsh.platform.dto;

public class AuthDtos {

    public record RegisterRequest(String phone, String password, String companyName,
                                  String creditCode, String type, String contactName, String address) {}

    public record LoginRequest(String phone, String password) {}

    public record CreateUserRequest(String phone, String password, String realName, String role) {}

    public record LoginResponse(String token, String role, Long tenantId, String name) {}

    public record UpdateEnterpriseRequest(String name, String creditCode, String contactName,
                                          String address, Integer creditScore, String authStatus) {}

    public record UpdateAccountRequest(String phone, String realName, String status, String password) {}
}
