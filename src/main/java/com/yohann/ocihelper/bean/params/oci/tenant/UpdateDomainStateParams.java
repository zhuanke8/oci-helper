package com.yohann.ocihelper.bean.params.oci.tenant;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Identity Domain state update params.
 */
@Data
public class UpdateDomainStateParams {

    @NotBlank(message = "ociCfgId不能为空")
    private String ociCfgId;

    @NotBlank(message = "domainId不能为空")
    private String domainId;
}
