package com.yohann.ocihelper.service;

import com.yohann.ocihelper.bean.params.oci.tenant.*;
import com.yohann.ocihelper.bean.response.oci.tenant.IdentityDomainRsp;
import com.yohann.ocihelper.bean.response.oci.tenant.PasswordOperationRsp;
import com.yohann.ocihelper.bean.response.oci.tenant.TenantInfoRsp;

import java.util.List;

/**
 * @ClassName ITenantService
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-03-12 16:02
 **/
public interface ITenantService {
    TenantInfoRsp tenantInfo(GetTenantInfoParams params);

    List<IdentityDomainRsp> listIdentityDomains(GetIdentityDomainsParams params);

    void activateDomain(UpdateDomainStateParams params);

    void deactivateDomain(UpdateDomainStateParams params);

    void deleteMfaDevice(UpdateUserBasicParams params);

    void deleteApiKey(UpdateUserBasicParams params);

    PasswordOperationRsp resetPassword(ResetUserPasswordParams params);

    PasswordOperationRsp updateUserPassword(UpdateUserPasswordParams params);

    String resetConsolePassword(UpdateUserBasicParams params);

    String updateRecoveryEmail(UpdateUserRecoveryEmailParams params);

    void updateUserInfo(UpdateUserInfoParams params);

    void deleteUser(UpdateUserBasicParams params);

    void updatePwdExpirationPolicy(UpdatePwdExpirationPolicyParams params);
}
