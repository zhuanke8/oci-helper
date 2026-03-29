package com.yohann.ocihelper.controller;

import com.yohann.ocihelper.bean.ResponseData;
import com.yohann.ocihelper.bean.params.oci.tenant.CreateIdentityDomainAdminUserParams;
import com.yohann.ocihelper.bean.params.oci.tenant.GetIdentityDomainsParams;
import com.yohann.ocihelper.bean.params.oci.tenant.GetTenantInfoParams;
import com.yohann.ocihelper.bean.params.oci.tenant.ResetUserPasswordParams;
import com.yohann.ocihelper.bean.params.oci.tenant.UpdateDomainStateParams;
import com.yohann.ocihelper.bean.params.oci.tenant.UpdatePwdExpirationPolicyParams;
import com.yohann.ocihelper.bean.params.oci.tenant.UpdateUserBasicParams;
import com.yohann.ocihelper.bean.params.oci.tenant.UpdateUserInfoParams;
import com.yohann.ocihelper.bean.params.oci.tenant.UpdateUserPasswordParams;
import com.yohann.ocihelper.bean.params.oci.tenant.UpdateUserRecoveryEmailParams;
import com.yohann.ocihelper.bean.response.oci.tenant.IdentityDomainRsp;
import com.yohann.ocihelper.bean.response.oci.tenant.PasswordOperationRsp;
import com.yohann.ocihelper.bean.response.oci.tenant.TenantInfoRsp;
import com.yohann.ocihelper.service.ITenantService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

import java.util.List;

/**
 * @ClassName TenantController
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-03-12 15:51
 **/
@RestController
@RequestMapping(path = "/api/tenant")
public class TenantController {

    @Resource
    private ITenantService tenantService;

    @RequestMapping("tenantInfo")
    public ResponseData<TenantInfoRsp> tenantInfo(@Validated @RequestBody GetTenantInfoParams params) {
        return ResponseData.successData(tenantService.tenantInfo(params));
    }

    @RequestMapping("listIdentityDomains")
    public ResponseData<List<IdentityDomainRsp>> listIdentityDomains(@Validated @RequestBody GetIdentityDomainsParams params) {
        return ResponseData.successData(tenantService.listIdentityDomains(params));
    }

    @RequestMapping("createIdentityDomainAdminUser")
    public ResponseData<TenantInfoRsp.TenantUserInfo> createIdentityDomainAdminUser(@Validated @RequestBody CreateIdentityDomainAdminUserParams params) {
        TenantInfoRsp.TenantUserInfo rsp = tenantService.createIdentityDomainAdminUser(params);
        return ResponseData.successData(rsp, "Identity Domain 域管理员用户创建成功");
    }

    @RequestMapping("activateDomain")
    public ResponseData<Void> activateDomain(@Validated @RequestBody UpdateDomainStateParams params) {
        tenantService.activateDomain(params);
        return ResponseData.successData("Identity Domain 激活成功");
    }

    @RequestMapping("deactivateDomain")
    public ResponseData<Void> deactivateDomain(@Validated @RequestBody UpdateDomainStateParams params) {
        tenantService.deactivateDomain(params);
        return ResponseData.successData("Identity Domain 停用成功");
    }

    @RequestMapping("deleteUser")
    public ResponseData<Void> deleteUser(@Validated @RequestBody UpdateUserBasicParams params) {
        tenantService.deleteUser(params);
        return ResponseData.successData("删除用户成功");
    }

    @RequestMapping("deleteMfaDevice")
    public ResponseData<Void> deleteMfaDevice(@Validated @RequestBody UpdateUserBasicParams params) {
        tenantService.deleteMfaDevice(params);
        return ResponseData.successData("清除 MFA 因子成功");
    }

    @RequestMapping("deleteApiKey")
    public ResponseData<Void> deleteApiKey(@Validated @RequestBody UpdateUserBasicParams params) {
        tenantService.deleteApiKey(params);
        return ResponseData.successData("清除所有 API 成功");
    }

    @RequestMapping("resetPassword")
    public ResponseData<PasswordOperationRsp> resetPassword(@Validated @RequestBody ResetUserPasswordParams params) {
        PasswordOperationRsp rsp = tenantService.resetPassword(params);
        return ResponseData.successData(rsp, "Identity Domains 随机密码已重置");
    }

    @RequestMapping("changePassword")
    public ResponseData<PasswordOperationRsp> changePassword(@Validated @RequestBody UpdateUserPasswordParams params) {
        PasswordOperationRsp rsp = tenantService.updateUserPassword(params);
        return ResponseData.successData(rsp, "Identity Domains 用户密码修改成功");
    }

    @RequestMapping("resetConsolePassword")
    public ResponseData<String> resetConsolePassword(@Validated @RequestBody UpdateUserBasicParams params) {
        String password = tenantService.resetConsolePassword(params);
        return ResponseData.successData(password, "控制台密码已重置");
    }

    @RequestMapping("updateRecoveryEmail")
    public ResponseData<String> updateRecoveryEmail(@Validated @RequestBody UpdateUserRecoveryEmailParams params) {
        String recoveryEmail = tenantService.updateRecoveryEmail(params);
        String msg = recoveryEmail == null ? "Identity Domains 恢复邮箱已清空" : "Identity Domains 恢复邮箱更新成功";
        return ResponseData.successData(recoveryEmail, msg);
    }

    @RequestMapping("updateUserInfo")
    public ResponseData<Void> updateUserInfo(@Validated @RequestBody UpdateUserInfoParams params) {
        tenantService.updateUserInfo(params);
        return ResponseData.successData("更新用户信息成功");
    }

    @RequestMapping("updatePwdEx")
    public ResponseData<Void> updatePwdEx(@Validated @RequestBody UpdatePwdExpirationPolicyParams params) {
        tenantService.updatePwdExpirationPolicy(params);
        return ResponseData.successData("更新密码过期时间成功");
    }

}
