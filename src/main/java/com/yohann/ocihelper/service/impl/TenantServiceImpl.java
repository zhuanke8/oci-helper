package com.yohann.ocihelper.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.oracle.bmc.identity.IdentityClient;
import com.oracle.bmc.identity.model.DomainSummary;
import com.oracle.bmc.identity.model.RegionSubscription;
import com.oracle.bmc.identity.model.Tenancy;
import com.oracle.bmc.identity.requests.ActivateDomainRequest;
import com.oracle.bmc.identity.requests.DeactivateDomainRequest;
import com.oracle.bmc.identity.requests.DeleteUserRequest;
import com.oracle.bmc.identity.requests.GetTenancyRequest;
import com.oracle.bmc.identity.requests.ListRegionSubscriptionsRequest;
import com.oracle.bmc.identity.requests.ListUsersRequest;
import com.oracle.bmc.identitydomains.model.ExtensionMfaUser;
import com.oracle.bmc.identitydomains.model.PasswordPolicy;
import com.oracle.bmc.identitydomains.model.UserEmails;
import com.yohann.ocihelper.bean.constant.CacheConstant;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
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
import com.yohann.ocihelper.config.OracleInstanceFetcher;
import com.yohann.ocihelper.exception.OciException;
import com.yohann.ocihelper.service.ISysService;
import com.yohann.ocihelper.service.ITenantService;
import com.yohann.ocihelper.utils.CommonUtils;
import com.yohann.ocihelper.utils.CustomExpiryGuavaCache;
import com.yohann.ocihelper.utils.OciUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * @ClassName TenantServiceImpl
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-03-12 16:02
 **/
@Service
@Slf4j
public class TenantServiceImpl implements ITenantService {

    @Resource
    private ISysService sysService;
    @Resource
    private ExecutorService virtualExecutor;
    @Resource
    private CustomExpiryGuavaCache<String, Object> customCache;

    @Override
    public TenantInfoRsp tenantInfo(GetTenantInfoParams params) {
        boolean selectedDomainMode = StrUtil.isNotBlank(params.getDomainUrl());
        if (params.isCleanReLaunch() && !selectedDomainMode) {
            customCache.remove(CacheConstant.PREFIX_TENANT_INFO + params.getOciCfgId());
        }

        SysUserDTO sysUserDTO = sysService.getOciUser(params.getOciCfgId());
        if (StrUtil.isNotBlank(params.getRegion())) {
            SysUserDTO.OciCfg ociCfg = sysUserDTO.getOciCfg();
            ociCfg.setRegion(params.getRegion());
            sysUserDTO.setOciCfg(ociCfg);
        }

        if (!selectedDomainMode) {
            TenantInfoRsp tenantInfoInCache = (TenantInfoRsp) customCache.get(CacheConstant.PREFIX_TENANT_INFO + params.getOciCfgId());
            if (tenantInfoInCache != null && StrUtil.isNotBlank(tenantInfoInCache.getCreatTime())) {
                customCache.put(CacheConstant.PREFIX_TENANT_INFO + params.getOciCfgId(), tenantInfoInCache, 10 * 60 * 1000);
                return tenantInfoInCache;
            }
        }

        TenantInfoRsp rsp = new TenantInfoRsp();
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            IdentityClient identityClient = fetcher.getIdentityClient();
            Tenancy tenancy = identityClient.getTenancy(GetTenancyRequest.builder()
                    .tenancyId(sysUserDTO.getOciCfg().getTenantId())
                    .build()).getTenancy();
            BeanUtils.copyProperties(tenancy, rsp);

            CompletableFuture<List<TenantInfoRsp.TenantUserInfo>> userListTask = CompletableFuture.supplyAsync(() ->
                            selectedDomainMode
                                    ? listIdentityDomainUsers(fetcher, params.getDomainUrl())
                                    : Optional.ofNullable(identityClient.listUsers(ListUsersRequest.builder()
                                                    .compartmentId(fetcher.getCompartmentId())
                                                    .build()).getItems())
                                            .filter(CollectionUtil::isNotEmpty).orElseGet(Collections::emptyList).stream()
                                            .map(x -> {
                                                TenantInfoRsp.TenantUserInfo info = new TenantInfoRsp.TenantUserInfo();
                                                info.setId(x.getId());
                                                info.setOcid(x.getId());
                                                info.setName(x.getName());
                                                info.setEmail(x.getEmail());
                                                info.setDescription(x.getDescription());
                                                info.setLifecycleState(x.getLifecycleState().getValue());
                                                info.setEmailVerified(x.getEmailVerified());
                                                info.setIsMfaActivated(x.getIsMfaActivated());
                                                info.setTimeCreated(CommonUtils.dateFmt2String(x.getTimeCreated()));
                                                info.setLastSuccessfulLoginTime(x.getLastSuccessfulLoginTime() == null ? null : CommonUtils.dateFmt2String(x.getLastSuccessfulLoginTime()));
                                                info.setJsonStr(JSONUtil.toJsonStr(x));
                                                return info;
                                            }).collect(Collectors.toList()), virtualExecutor)
                    .exceptionally(e -> {
                        log.error("get user list error", e);
                        return Collections.emptyList();
                    });

            CompletableFuture<List<String>> regionsTask = CompletableFuture.supplyAsync(() ->
                            identityClient.listRegionSubscriptions(ListRegionSubscriptionsRequest.builder()
                                            .tenancyId(sysUserDTO.getOciCfg().getTenantId())
                                            .build()).getItems().stream()
                                    .map(RegionSubscription::getRegionName)
                                    .collect(Collectors.toList()), virtualExecutor)
                    .exceptionally(e -> {
                        log.error("get region list error", e);
                        return Collections.emptyList();
                    });

            CompletableFuture<PasswordPolicy> pwdExpTask = CompletableFuture.supplyAsync(() ->
                            selectedDomainMode
                                    ? OciUtils.getCurrentCustomPasswordPolicy(fetcher, params.getDomainUrl())
                                    : OciUtils.getCurrentCustomPasswordPolicy(fetcher), virtualExecutor)
                    .exceptionally(e -> {
                        log.error("get pwd expires after error", e);
                        return PasswordPolicy.builder().build();
                    });

            CompletableFuture<String> createTimeTask = CompletableFuture.supplyAsync(() -> {
                        String registeredTime = fetcher.getRegisteredTime();
                        String timeDifference = CommonUtils.getTimeDifference(LocalDateTime.parse(registeredTime, CommonUtils.DATETIME_FMT_NORM));
                        return registeredTime + "（" + timeDifference + "）";
                    }, virtualExecutor)
                    .exceptionally(e -> {
                        log.error("get account create time error", e);
                        return null;
                    });
            ;

            CompletableFuture.allOf(userListTask, regionsTask, pwdExpTask, createTimeTask).join();

            rsp.setUserList(CommonUtils.safeJoin(userListTask, Collections.emptyList()));
            rsp.setRegions(CommonUtils.safeJoin(regionsTask, Collections.emptyList()));
            PasswordPolicy passwordPolicy = CommonUtils.safeJoin(pwdExpTask, PasswordPolicy.builder().build());
            rsp.setPasswordExpiresAfter(passwordPolicy.getPasswordExpiresAfter());
            rsp.setPasswordPolicyId(passwordPolicy.getId());
            rsp.setPasswordPolicyName(passwordPolicy.getName());
            rsp.setPasswordStrength(passwordPolicy.getPasswordStrength() == null ? null : passwordPolicy.getPasswordStrength().getValue());
            rsp.setCreatTime(CommonUtils.safeJoin(createTimeTask, null));

            if (!selectedDomainMode) {
                customCache.put(CacheConstant.PREFIX_TENANT_INFO + params.getOciCfgId(), rsp, 10 * 60 * 1000);
            }
            return rsp;
        } catch (Exception e) {
            log.error("获取租户信息失败", e);
            throw new OciException(-1, "获取租户信息失败", e);
        }
    }

    @Override
    public List<IdentityDomainRsp> listIdentityDomains(GetIdentityDomainsParams params) {
        SysUserDTO sysUserDTO = getAuthUser(params.getOciCfgId());
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            return OciUtils.listDomains(fetcher.getIdentityClient(), sysUserDTO.getOciCfg().getTenantId())
                    .stream()
                    .map(this::buildIdentityDomainInfo)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("查询 Identity Domains 失败", e);
            throw new OciException(-1, "查询 Identity Domains 失败", e);
        }
    }

    @Override
    public void activateDomain(UpdateDomainStateParams params) {
        SysUserDTO sysUserDTO = getAuthUser(params.getOciCfgId());
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            fetcher.getIdentityClient().activateDomain(ActivateDomainRequest.builder()
                    .domainId(params.getDomainId())
                    .build());
            invalidateTenantCache(params.getOciCfgId());
        } catch (Exception e) {
            log.error("激活 Identity Domain 失败", e);
            throw new OciException(-1, "激活 Identity Domain 失败", e);
        }
    }

    @Override
    public void deactivateDomain(UpdateDomainStateParams params) {
        SysUserDTO sysUserDTO = getAuthUser(params.getOciCfgId());
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            fetcher.getIdentityClient().deactivateDomain(DeactivateDomainRequest.builder()
                    .domainId(params.getDomainId())
                    .build());
            invalidateTenantCache(params.getOciCfgId());
        } catch (Exception e) {
            log.error("停用 Identity Domain 失败", e);
            throw new OciException(-1, "停用 Identity Domain 失败", e);
        }
    }

    @Override
    public void deleteMfaDevice(UpdateUserBasicParams params) {
        SysUserDTO sysUserDTO = getAuthUser(params.getOciCfgId());
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            OciUtils.removeUserMfaFactors(fetcher, params.getUserId(), params.getDomainUrl());
            invalidateTenantCache(params.getOciCfgId());
        } catch (Exception e) {
            log.error("清除 MFA 因子失败", e);
            throw new OciException(-1, "清除 MFA 因子失败", e);
        }
    }

    @Override
    public void deleteApiKey(UpdateUserBasicParams params) {
        SysUserDTO sysUserDTO = getAuthUser(params.getOciCfgId());
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            fetcher.deleteAllApiKey(params.getUserId());
            invalidateTenantCache(params.getOciCfgId());
        } catch (Exception e) {
            log.error("清除所有 API 失败", e);
            throw new OciException(-1, "清除所有 API 失败", e);
        }
    }

    @Override
    public PasswordOperationRsp resetPassword(ResetUserPasswordParams params) {
        SysUserDTO sysUserDTO = getAuthUser(params.getOciCfgId());
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            return OciUtils.resetUserPassword(fetcher,
                    params.getUserId(),
                    params.getBypassNotification(),
                    params.getUserFlowControlledByExternalClient(),
                    params.getDomainUrl());
        } catch (Exception e) {
            log.error("通过 Identity Domains 重置用户密码失败", e);
            throw new OciException(-1, "通过 Identity Domains 重置用户密码失败", e);
        }
    }

    @Override
    public PasswordOperationRsp updateUserPassword(UpdateUserPasswordParams params) {
        SysUserDTO sysUserDTO = getAuthUser(params.getOciCfgId());
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            return OciUtils.changeUserPassword(fetcher,
                    params.getUserId(),
                    params.getPassword(),
                    params.getBypassNotification(),
                    params.getOldPassword(),
                    params.getDomainUrl());
        } catch (Exception e) {
            log.error("通过 Identity Domains 修改用户密码失败", e);
            throw new OciException(-1, "通过 Identity Domains 修改用户密码失败", e);
        }
    }

    @Override
    public String resetConsolePassword(UpdateUserBasicParams params) {
        SysUserDTO sysUserDTO = getAuthUser(params.getOciCfgId());
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            return fetcher.createOrResetUIPassword(params.getUserId());
        } catch (Exception e) {
            log.error("重置控制台密码失败", e);
            throw new OciException(-1, "重置控制台密码失败", e);
        }
    }

    @Override
    public String updateRecoveryEmail(UpdateUserRecoveryEmailParams params) {
        SysUserDTO sysUserDTO = getAuthUser(params.getOciCfgId());
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            String recoveryEmail = OciUtils.updateUserRecoveryEmail(
                    fetcher,
                    params.getUserId(),
                    params.getRecoveryEmail(),
                    params.getCurrentPassword(),
                    params.getDomainUrl()
            );
            invalidateTenantCache(params.getOciCfgId());
            return recoveryEmail;
        } catch (Exception e) {
            log.error("通过 Identity Domains 更新恢复邮箱失败", e);
            throw new OciException(-1, "通过 Identity Domains 更新恢复邮箱失败", e);
        }
    }

    @Override
    public void updateUserInfo(UpdateUserInfoParams params) {
        SysUserDTO sysUserDTO = getAuthUser(params.getOciCfgId());
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            fetcher.updateUser(params.getUserId(), params.getEmail(), params.getDbUserName(), params.getDescription());
            invalidateTenantCache(params.getOciCfgId());
        } catch (Exception e) {
            log.error("更新用户信息失败", e);
            throw new OciException(-1, "更新用户信息失败", e);
        }
    }

    @Override
    public void deleteUser(UpdateUserBasicParams params) {
        SysUserDTO sysUserDTO = getAuthUser(params.getOciCfgId());
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            fetcher.getIdentityClient().deleteUser(DeleteUserRequest.builder()
                    .userId(params.getUserId())
                    .build());
            invalidateTenantCache(params.getOciCfgId());
        } catch (Exception e) {
            log.error("删除用户失败", e);
            throw new OciException(-1, "删除用户失败", e);
        }
    }

    @Override
    public void updatePwdExpirationPolicy(UpdatePwdExpirationPolicyParams params) {
        SysUserDTO sysUserDTO = sysService.getOciUser(params.getCfgId());
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            if (params.getPasswordExpiresAfter() == null || params.getPasswordExpiresAfter() == 0) {
                OciUtils.disablePasswordExpirationWithAutoDomain(fetcher);
            } else {
                OciUtils.enablePasswordExpirationWithAutoDomain(fetcher, params.getPasswordExpiresAfter());
            }
            invalidateTenantCache(params.getCfgId());
        } catch (Exception e) {
            log.error("更新密码策略失败", e);
            throw new OciException(-1, "更新密码策略失败");
        }
    }

    private SysUserDTO getAuthUser(String ociCfgId) {
        return sysService.getOciUser(ociCfgId);
    }

    private List<TenantInfoRsp.TenantUserInfo> listIdentityDomainUsers(OracleInstanceFetcher fetcher, String domainUrl) {
        return Optional.ofNullable(OciUtils.prepareIdentityDomainsClient(fetcher, domainUrl)
                        .listUsers(com.oracle.bmc.identitydomains.requests.ListUsersRequest.builder()
                                .count(1000)
                                .build())
                        .getUsers())
                .map(com.oracle.bmc.identitydomains.model.Users::getResources)
                .orElse(Collections.emptyList())
                .stream()
                .map(this::buildIdentityDomainUserInfo)
                .collect(Collectors.toList());
    }

    private TenantInfoRsp.TenantUserInfo buildIdentityDomainUserInfo(com.oracle.bmc.identitydomains.model.User user) {
        TenantInfoRsp.TenantUserInfo info = new TenantInfoRsp.TenantUserInfo();
        info.setId(user.getId());
        info.setOcid(user.getOcid());
        info.setName(StrUtil.blankToDefault(user.getDisplayName(), user.getUserName()));
        info.setEmail(extractPrimaryEmail(user));
        info.setDescription(user.getDescription());
        info.setLifecycleState(Boolean.TRUE.equals(user.getActive()) ? "ACTIVE" : "INACTIVE");
        info.setEmailVerified(extractEmailVerified(user));
        info.setIsMfaActivated(isMfaActivated(user));
        info.setTimeCreated(formatIdentityDomainTime(Optional.ofNullable(user.getMeta()).map(com.oracle.bmc.identitydomains.model.Meta::getCreated).orElse(null)));
        info.setLastSuccessfulLoginTime(null);
        info.setJsonStr(JSONUtil.toJsonStr(user));
        return info;
    }

    private String extractPrimaryEmail(com.oracle.bmc.identitydomains.model.User user) {
        return Optional.ofNullable(user.getEmails())
                .orElse(Collections.emptyList())
                .stream()
                .filter(x -> x != null && Boolean.TRUE.equals(x.getPrimary()) && StrUtil.isNotBlank(x.getValue()))
                .map(UserEmails::getValue)
                .findFirst()
                .or(() -> Optional.ofNullable(user.getEmails())
                        .orElse(Collections.emptyList())
                        .stream()
                        .filter(x -> x != null && StrUtil.isNotBlank(x.getValue()))
                        .map(UserEmails::getValue)
                        .findFirst())
                .orElse(user.getUserName());
    }

    private Boolean extractEmailVerified(com.oracle.bmc.identitydomains.model.User user) {
        return Optional.ofNullable(user.getEmails())
                .orElse(Collections.emptyList())
                .stream()
                .filter(x -> x != null && Boolean.TRUE.equals(x.getPrimary()))
                .map(UserEmails::getVerified)
                .findFirst()
                .or(() -> Optional.ofNullable(user.getEmails())
                        .orElse(Collections.emptyList())
                        .stream()
                        .filter(Objects::nonNull)
                        .map(UserEmails::getVerified)
                        .findFirst())
                .orElse(Boolean.FALSE);
    }

    private Boolean isMfaActivated(com.oracle.bmc.identitydomains.model.User user) {
        ExtensionMfaUser extensionMfaUser = user.getUrnIetfParamsScimSchemasOracleIdcsExtensionMfaUser();
        return extensionMfaUser != null && extensionMfaUser.getMfaStatus() == ExtensionMfaUser.MfaStatus.Enrolled;
    }

    private String formatIdentityDomainTime(String rawTime) {
        if (StrUtil.isBlank(rawTime)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(rawTime).toLocalDateTime().format(CommonUtils.DATETIME_FMT_NORM);
        } catch (DateTimeParseException e) {
            log.debug("Failed to parse identity domain time: {}", rawTime, e);
            return rawTime;
        }
    }

    private IdentityDomainRsp buildIdentityDomainInfo(DomainSummary domain) {
        IdentityDomainRsp info = new IdentityDomainRsp();
        info.setId(domain.getId());
        info.setDisplayName(domain.getDisplayName());
        info.setDescription(domain.getDescription());
        info.setUrl(domain.getUrl());
        info.setHomeRegionUrl(domain.getHomeRegionUrl());
        info.setHomeRegion(domain.getHomeRegion());
        info.setLifecycleState(domain.getLifecycleState() == null ? null : domain.getLifecycleState().getValue());
        info.setType(domain.getType() == null ? null : domain.getType().getValue());
        info.setHiddenOnLogin(domain.getIsHiddenOnLogin());
        info.setDefaultDomain(domain.getType() == DomainSummary.Type.Default);
        return info;
    }

    private void invalidateTenantCache(String ociCfgId) {
        customCache.remove(CacheConstant.PREFIX_TENANT_INFO + ociCfgId);
    }
}
