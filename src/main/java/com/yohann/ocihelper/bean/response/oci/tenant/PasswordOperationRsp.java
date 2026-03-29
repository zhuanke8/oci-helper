package com.yohann.ocihelper.bean.response.oci.tenant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @ClassName PasswordOperationRsp
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2026-03-29 20:51
 **/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordOperationRsp {

    private String userId;
    private String resourceId;
    private String operation;
    private Boolean notificationBypassed;
    private Boolean userFlowControlledByExternalClient;
    private String oneTimePassword;
    private String userToken;
    private String userTokenRef;
}
