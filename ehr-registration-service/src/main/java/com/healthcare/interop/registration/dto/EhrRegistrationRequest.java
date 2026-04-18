package com.healthcare.interop.registration.dto;

import com.healthcare.interop.common.enums.AuthType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class EhrRegistrationRequest {
    @NotBlank
    private String ehrCode;

    @NotBlank
    private String displayName;

    private String orgName;

    @Email
    private String contactEmail;

    @NotBlank
    private String baseUrl;

    @NotNull
    private AuthType authType;

    private Map<String, String> authConfig;

    private String description;
    private String version;
}
