package com.yohann.ocihelper.bean.params.oci.tenant;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @ClassName ResetUserPasswordParams
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2026-03-29 20:50
 **/
@Data
@EqualsAndHashCode(callSuper = true)
public class ResetUserPasswordParams extends UpdateUserBasicParams {

    private Boolean bypassNotification;
    private Boolean userFlowControlledByExternalClient;
}
