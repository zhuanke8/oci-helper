package com.yohann.ocihelper.bean.params.oci.tenant;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Identity Domains list params.
 */
@Data
public class GetIdentityDomainsParams {

    @NotBlank(message = "ociCfgId不能为空")
    private String ociCfgId;
}
