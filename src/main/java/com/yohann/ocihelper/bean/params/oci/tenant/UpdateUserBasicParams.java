package com.yohann.ocihelper.bean.params.oci.tenant;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @ClassName UpdateUserBasicParams
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-03-14 18:04
 **/
@Data
public class UpdateUserBasicParams {

    @NotBlank(message = "ociCfgId不能为空")
    private String ociCfgId;
    @NotBlank(message = "userId不能为空")
    private String userId;

}
