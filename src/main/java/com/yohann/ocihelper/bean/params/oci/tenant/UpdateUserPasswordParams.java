package com.yohann.ocihelper.bean.params.oci.tenant;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @ClassName UpdateUserPasswordParams
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2026-03-29 20:50
 **/
@Data
@EqualsAndHashCode(callSuper = true)
public class UpdateUserPasswordParams extends ResetUserPasswordParams {

    @NotBlank(message = "password不能为空")
    private String password;
}
