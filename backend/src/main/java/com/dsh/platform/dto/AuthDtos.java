package com.dsh.platform.dto;

public class AuthDtos {

    public record RegisterRequest(String phone, String password, String companyName,
                                  String creditCode, String type, String contactName) {}

    public record LoginRequest(String phone, String password) {}

    public record LoginResponse(String token, String role, Long tenantId, String name) {}
}
