package com.yohann.ocihelper.bean.params.oci.tenant;

import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @ClassName UpdateUserRecoveryEmailParams
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2026-03-29 22:40
 **/
@Data
@EqualsAndHashCode(callSuper = true)
public class UpdateUserRecoveryEmailParams extends UpdateUserBasicParams {

    /**
     * Identity Domains recovery email。
     * 传空字符串或 null 表示清空 recovery email。
     */
    @Pattern(
            regexp = "^$|^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$",
            message = "recoveryEmail格式不正确"
    )
    private String recoveryEmail;
}
