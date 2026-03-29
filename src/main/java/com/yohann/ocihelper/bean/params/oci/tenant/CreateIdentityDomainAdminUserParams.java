package com.yohann.ocihelper.bean.params.oci.tenant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Create identity domain administrator user params.
 */
@Data
public class CreateIdentityDomainAdminUserParams {

    @NotBlank(message = "ociCfgId不能为空")
    private String ociCfgId;

    private String domainId;
    private String domainName;

    @NotBlank(message = "domainUrl不能为空")
    private String domainUrl;

    @NotBlank(message = "userName不能为空")
    @Pattern(
            regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$",
            message = "userName必须是邮箱格式"
    )
    private String userName;

    @NotBlank(message = "displayName不能为空")
    private String displayName;

    @NotBlank(message = "password不能为空")
    private String password;
}
