package com.yohann.ocihelper.utils;

import cn.hutool.json.JSONUtil;
import com.oracle.bmc.identity.IdentityClient;
import com.oracle.bmc.identity.model.DomainSummary;
import com.oracle.bmc.identity.requests.ListDomainsRequest;
import com.oracle.bmc.identity.responses.ListDomainsResponse;
import com.oracle.bmc.identitydomains.IdentityDomainsClient;
import com.oracle.bmc.identitydomains.model.AuthenticationFactorsRemover;
import com.oracle.bmc.identitydomains.model.AuthenticationFactorsRemoverUser;
import com.oracle.bmc.identitydomains.model.ExtensionMeUser;
import com.oracle.bmc.identitydomains.model.Me;
import com.oracle.bmc.identitydomains.model.MeEmails;
import com.oracle.bmc.identitydomains.model.MePasswordChanger;
import com.oracle.bmc.identitydomains.model.MyAuthenticationFactorsRemover;
import com.oracle.bmc.identitydomains.model.MyAuthenticationFactorsRemoverUser;
import com.oracle.bmc.identitydomains.model.NotificationSetting;
import com.oracle.bmc.identitydomains.model.PasswordPolicies;
import com.oracle.bmc.identitydomains.model.PasswordPolicy;
import com.oracle.bmc.identitydomains.model.User;
import com.oracle.bmc.identitydomains.model.UserEmails;
import com.oracle.bmc.identitydomains.model.UserPasswordChanger;
import com.oracle.bmc.identitydomains.model.UserPasswordResetter;
import com.oracle.bmc.identitydomains.requests.CreateAuthenticationFactorsRemoverRequest;
import com.oracle.bmc.identitydomains.requests.CreateMyAuthenticationFactorsRemoverRequest;
import com.oracle.bmc.identitydomains.requests.GetMeRequest;
import com.oracle.bmc.identitydomains.requests.GetUserRequest;
import com.oracle.bmc.identitydomains.requests.GetNotificationSettingRequest;
import com.oracle.bmc.identitydomains.requests.ListPasswordPoliciesRequest;
import com.oracle.bmc.identitydomains.requests.PutUserRequest;
import com.oracle.bmc.identitydomains.requests.PutNotificationSettingRequest;
import com.oracle.bmc.identitydomains.requests.PutPasswordPolicyRequest;
import com.oracle.bmc.identitydomains.requests.PutMePasswordChangerRequest;
import com.oracle.bmc.identitydomains.requests.PutMeRequest;
import com.oracle.bmc.identitydomains.requests.PutUserPasswordChangerRequest;
import com.oracle.bmc.identitydomains.requests.PutUserPasswordResetterRequest;
import com.oracle.bmc.identitydomains.responses.ListPasswordPoliciesResponse;
import com.oracle.bmc.identitydomains.responses.PutMePasswordChangerResponse;
import com.oracle.bmc.identitydomains.responses.PutPasswordPolicyResponse;
import com.oracle.bmc.identitydomains.responses.PutUserPasswordChangerResponse;
import com.oracle.bmc.identitydomains.responses.PutUserPasswordResetterResponse;
import com.yohann.ocihelper.bean.response.oci.tenant.PasswordOperationRsp;
import com.yohann.ocihelper.config.OracleInstanceFetcher;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

/**
 * @ClassName OciUtils
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-09-19 17:34
 **/
@Slf4j
public class OciUtils {

    private static final String NOTIFICATION_SETTINGS_SCHEMA =
            "urn:ietf:params:scim:schemas:oracle:idcs:NotificationSettings";
    private static final String USER_PASSWORD_CHANGER_SCHEMA =
            "urn:ietf:params:scim:schemas:oracle:idcs:UserPasswordChanger";
    private static final String USER_PASSWORD_RESETTER_SCHEMA =
            "urn:ietf:params:scim:schemas:oracle:idcs:UserPasswordResetter";
    private static final String AUTHENTICATION_FACTORS_REMOVER_SCHEMA =
            "urn:ietf:params:scim:schemas:oracle:idcs:AuthenticationFactorsRemover";
    private static final String ME_PASSWORD_CHANGER_SCHEMA =
            "urn:ietf:params:scim:schemas:oracle:idcs:MePasswordChanger";
    private static final String NOTIFICATION_SETTINGS_ID = "NotificationSettings";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    private static final UserEmails.Type RECOVERY_EMAIL_TYPE = UserEmails.Type.create("recovery");
    private static final MeEmails.Type ME_RECOVERY_EMAIL_TYPE = MeEmails.Type.Recovery;

    /**
     * 统一的返回结果封装
     */
    public static class Result extends HashMap<String, Object> {
        public static Result ok(String message) {
            Result r = new Result();
            r.put("success", true);
            r.put("message", message);
            return r;
        }

        public static Result fail(String message) {
            Result r = new Result();
            r.put("success", false);
            r.put("message", message);
            return r;
        }

        public Result data(String key, Object value) {
            this.put(key, value);
            return this;
        }
    }

    /**
     * 校验邮箱
     */
    private static boolean isValidEmail(String email) {
        return StringUtils.isNotBlank(email) && EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    /**
     * 获取当前收件人
     */
    public static Result getCurrentRecipients(OracleInstanceFetcher fetcher) {
        try {
            IdentityDomainsClient client = prepareIdentityDomainsClient(fetcher);
            NotificationSetting setting = client.getNotificationSetting(
                    GetNotificationSettingRequest.builder().notificationSettingId(NOTIFICATION_SETTINGS_ID).build()
            ).getNotificationSetting();
            List<String> recipients = Optional.ofNullable(setting.getTestRecipients()).orElse(Collections.emptyList());
            return Result.ok("Recipients retrieved")
                    .data("recipients", recipients)
                    .data("totalCount", recipients.size());
        } catch (Exception e) {
            log.error("getCurrentRecipients error: {}", e.getMessage(), e);
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 更新收件人
     */
    public static Result updateRecipients(OracleInstanceFetcher fetcher, List<String> emails) {
        List<String> valid = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        for (String email : emails) {
            if (isValidEmail(email)) {
                valid.add(email.trim().toLowerCase());
            } else {
                invalid.add(email);
            }
        }
        if (!invalid.isEmpty()) {
            return Result.fail("Invalid emails: " + String.join(", ", invalid));
        }
        if (valid.isEmpty()) {
            return Result.fail("No valid email provided");
        }

        try {
            IdentityDomainsClient client = prepareIdentityDomainsClient(fetcher);
            NotificationSetting old = client.getNotificationSetting(
                    GetNotificationSettingRequest.builder().notificationSettingId(NOTIFICATION_SETTINGS_ID).build()
            ).getNotificationSetting();

            NotificationSetting updated = NotificationSetting.builder()
                    .copy(old)
                    .testRecipients(valid)
                    .testModeEnabled(true)
                    .schemas(Collections.singletonList(NOTIFICATION_SETTINGS_SCHEMA))
                    .build();

            client.putNotificationSetting(PutNotificationSettingRequest.builder()
                    .notificationSettingId(NOTIFICATION_SETTINGS_ID)
                    .notificationSetting(updated)
                    .build()
            );

            return Result.ok("Recipients updated")
                    .data("previousRecipients", old.getTestRecipients())
                    .data("newRecipients", valid)
                    .data("recipientCount", valid.size());
        } catch (Exception e) {
            log.error("updateRecipients error: {}", e.getMessage(), e);
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 添加收件人
     */
    public static Result addRecipients(OracleInstanceFetcher fetcher, List<String> emails) {
        Result currentRes = getCurrentRecipients(fetcher);
        if (!(boolean) currentRes.get("success")) {
            return currentRes;
        }

        List<String> current = (List<String>) currentRes.get("recipients");
        Set<String> newSet = new HashSet<>(current);

        List<String> added = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();
        for (String email : emails) {
            String lower = email.trim().toLowerCase();
            if (!isValidEmail(lower)) {
                continue;
            }
            if (newSet.add(lower)) {
                added.add(lower);
            } else {
                duplicates.add(lower);
            }
        }

        if (added.isEmpty()) {
            return Result.ok("No new recipients added")
                    .data("duplicateEmails", duplicates)
                    .data("currentRecipients", current);
        }

        return updateRecipients(fetcher, new ArrayList<>(newSet))
                .data("addedRecipients", added)
                .data("duplicateEmails", duplicates)
                .data("totalRecipients", newSet.size());
    }

    /**
     * 移除收件人
     */
    public static Result removeRecipients(OracleInstanceFetcher fetcher, List<String> emails) {
        Result currentRes = getCurrentRecipients(fetcher);
        if (!(boolean) currentRes.get("success")) {
            return currentRes;
        }

        List<String> current = (List<String>) currentRes.get("recipients");
        Set<String> remaining = new HashSet<>(current);

        List<String> removed = new ArrayList<>();
        for (String email : emails) {
            String lower = email.trim().toLowerCase();
            if (remaining.remove(lower)) {
                removed.add(lower);
            }
        }

        if (removed.isEmpty()) {
            return Result.ok("No recipients removed")
                    .data("currentRecipients", current);
        }

        return updateRecipients(fetcher, new ArrayList<>(remaining))
                .data("removedRecipients", removed)
                .data("remainingRecipients", remaining);
    }

    /**
     * 更新测试模式开关
     */
    public static Result updateTestMode(OracleInstanceFetcher fetcher, boolean enable) {
        try {
            IdentityDomainsClient client = prepareIdentityDomainsClient(fetcher);
            NotificationSetting old = client.getNotificationSetting(
                    GetNotificationSettingRequest.builder().notificationSettingId(NOTIFICATION_SETTINGS_ID).build()
            ).getNotificationSetting();

            NotificationSetting updated = NotificationSetting.builder()
                    .copy(old)
                    .testModeEnabled(enable)
                    .schemas(Collections.singletonList(NOTIFICATION_SETTINGS_SCHEMA))
                    .build();

            client.putNotificationSetting(PutNotificationSettingRequest.builder()
                    .notificationSettingId(NOTIFICATION_SETTINGS_ID)
                    .notificationSetting(updated)
                    .build()
            );

            return Result.ok("Test mode updated")
                    .data("testMode", enable)
                    .data("recipients", old.getTestRecipients());
        } catch (Exception e) {
            log.error("updateTestMode error: {}", e.getMessage(), e);
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 关闭密码过期
     */
    public static boolean disablePasswordExpirationWithAutoDomain(OracleInstanceFetcher fetcher) {
        return updatePasswordExpiration(fetcher, 0);
    }

    /**
     * 启用密码过期（默认 120 天）
     */
    public static boolean enablePasswordExpirationWithAutoDomain(OracleInstanceFetcher fetcher) {
        return enablePasswordExpirationWithAutoDomain(fetcher, 120);
    }

    /**
     * 启用密码过期（自定义天数）
     */
    public static boolean enablePasswordExpirationWithAutoDomain(OracleInstanceFetcher fetcher, Integer expirationDays) {
        if (expirationDays == null || expirationDays <= 0) {
            log.error("Invalid expirationDays: {}", expirationDays);
            return false;
        }
        return updatePasswordExpiration(fetcher, expirationDays);
    }

    /**
     * 公共方法：更新密码过期策略
     */
    private static boolean updatePasswordExpiration(OracleInstanceFetcher fetcher, int expirationDays) {

        try {
            IdentityDomainsClient identityDomainsClient = prepareIdentityDomainsClient(fetcher);

            // 查询当前策略
            List<PasswordPolicy> policies = listPasswordPolicies(identityDomainsClient);
            if (policies.isEmpty()) {
                log.warn("No password policies found for tenant: {}", fetcher.getUser().getOciCfg().getTenantId());
                return false;
            }

            for (com.oracle.bmc.identitydomains.model.PasswordPolicy policy : policies) {
                log.debug("Current policy: {}", JSONUtil.toJsonStr(policy));

                if (policy.getPasswordStrength() != com.oracle.bmc.identitydomains.model.PasswordPolicy.PasswordStrength.Custom) {
                    log.warn("Skip non-custom policy: {}", policy.getName());
                    continue;
                }

                com.oracle.bmc.identitydomains.model.PasswordPolicy updated = com.oracle.bmc.identitydomains.model.PasswordPolicy.builder()
                        .copy(policy)
                        .passwordExpiresAfter(expirationDays)  // 0 = 不过期
                        .forcePasswordReset(false)
                        .passwordExpireWarning(7)
                        .build();

                PutPasswordPolicyRequest request = PutPasswordPolicyRequest.builder()
                        .passwordPolicyId(policy.getId())
                        .passwordPolicy(updated)
                        .build();

                PutPasswordPolicyResponse response = identityDomainsClient.putPasswordPolicy(request);
                if (response.getPasswordPolicy() != null) {
                    log.info("Updated password policy [{}]: expiresAfter={}",
                            policy.getName(), expirationDays);
                }
            }
            return true;
        } catch (Exception e) {
            log.error("Failed to update password expiration: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 获取当前密码策略
     */
    public static List<com.oracle.bmc.identitydomains.model.PasswordPolicy> getCurrentPasswordPolicy(OracleInstanceFetcher fetcher) {
        return getCurrentPasswordPolicy(fetcher, null);
    }

    /**
     * 获取指定域当前密码策略
     */
    public static List<com.oracle.bmc.identitydomains.model.PasswordPolicy> getCurrentPasswordPolicy(OracleInstanceFetcher fetcher,
                                                                                                     String domainUrl) {
        try {
            return listPasswordPolicies(prepareIdentityDomainsClient(fetcher, domainUrl));
        } catch (Exception e) {
            log.error("Failed to get password policies: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取当前自定义密码策略
     */
    public static PasswordPolicy getCurrentCustomPasswordPolicy(OracleInstanceFetcher fetcher) {
        return getCurrentCustomPasswordPolicy(fetcher, null);
    }

    /**
     * 获取指定域当前自定义密码策略
     */
    public static PasswordPolicy getCurrentCustomPasswordPolicy(OracleInstanceFetcher fetcher, String domainUrl) {
        return getCurrentPasswordPolicy(fetcher, domainUrl).parallelStream()
                .filter(x -> x.getPasswordStrength() == PasswordPolicy.PasswordStrength.Custom)
                .findAny()
                .orElse(PasswordPolicy.builder().build());
    }

    /**
     * Identity Domains 清除用户 MFA 因子
     */
    public static void removeUserMfaFactors(OracleInstanceFetcher fetcher, String userId) {
        removeUserMfaFactors(fetcher, userId, null);
    }

    /**
     * Identity Domains 清除指定域用户 MFA 因子
     */
    public static void removeUserMfaFactors(OracleInstanceFetcher fetcher, String userId, String domainUrl) {
        try {
            IdentityDomainsClient client = prepareIdentityDomainsClient(fetcher, domainUrl);
            if (isSelfTarget(fetcher, userId)) {
                Me currentMe = client.getMe(GetMeRequest.builder().build()).getMe();
                if (currentMe == null) {
                    throw new RuntimeException("Current user not found in Identity Domains");
                }

                MyAuthenticationFactorsRemover remover = MyAuthenticationFactorsRemover.builder()
                        .schemas(Collections.singletonList(AUTHENTICATION_FACTORS_REMOVER_SCHEMA))
                        .type(MyAuthenticationFactorsRemover.Type.Mfa)
                        .user(MyAuthenticationFactorsRemoverUser.builder()
                                .value(currentMe.getId())
                                .ocid(currentMe.getOcid())
                                .display(currentMe.getDisplayName())
                                .build())
                        .build();

                client.createMyAuthenticationFactorsRemover(
                        CreateMyAuthenticationFactorsRemoverRequest.builder()
                                .myAuthenticationFactorsRemover(remover)
                                .build()
                );
                return;
            }

            User currentUser = client.getUser(GetUserRequest.builder()
                    .userId(userId)
                    .build()).getUser();

            if (currentUser == null) {
                throw new RuntimeException("User not found in Identity Domains: " + userId);
            }

            AuthenticationFactorsRemover remover = AuthenticationFactorsRemover.builder()
                    .schemas(Collections.singletonList(AUTHENTICATION_FACTORS_REMOVER_SCHEMA))
                    .type(AuthenticationFactorsRemover.Type.Mfa)
                    .user(AuthenticationFactorsRemoverUser.builder()
                            .value(currentUser.getId())
                            .ocid(currentUser.getOcid())
                            .display(currentUser.getDisplayName())
                            .build())
                    .build();

            client.createAuthenticationFactorsRemover(
                    CreateAuthenticationFactorsRemoverRequest.builder()
                            .authenticationFactorsRemover(remover)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to remove user MFA factors", e);
        }
    }

    /**
     * Identity Domains 指定密码修改
     */
    public static PasswordOperationRsp changeUserPassword(OracleInstanceFetcher fetcher,
                                                          String userId,
                                                          String newPassword,
                                                          Boolean bypassNotification,
                                                          String oldPassword) {
        return changeUserPassword(fetcher, userId, newPassword, bypassNotification, oldPassword, null);
    }

    /**
     * Identity Domains 指定密码修改
     */
    public static PasswordOperationRsp changeUserPassword(OracleInstanceFetcher fetcher,
                                                          String userId,
                                                          String newPassword,
                                                          Boolean bypassNotification,
                                                          String oldPassword,
                                                          String domainUrl) {
        try {
            IdentityDomainsClient client = prepareIdentityDomainsClient(fetcher, domainUrl);
            if (isSelfTarget(fetcher, userId)) {
                if (StringUtils.isBlank(oldPassword)) {
                    throw new IllegalArgumentException("当前目标是签名用户本人，自助改密必须提供旧密码");
                }
                MePasswordChanger changer = MePasswordChanger.builder()
                        .schemas(Collections.singletonList(ME_PASSWORD_CHANGER_SCHEMA))
                        .oldPassword(oldPassword)
                        .password(newPassword)
                        .build();

                PutMePasswordChangerResponse response = client.putMePasswordChanger(
                        PutMePasswordChangerRequest.builder()
                                .mePasswordChanger(changer)
                                .build()
                );

                MePasswordChanger rsp = response.getMePasswordChanger();
                return PasswordOperationRsp.builder()
                        .userId(userId)
                        .resourceId(rsp == null ? null : rsp.getId())
                        .operation("CHANGE_PASSWORD")
                        .notificationBypassed(Boolean.TRUE.equals(bypassNotification))
                        .build();
            }

            UserPasswordChanger changer = UserPasswordChanger.builder()
                    .schemas(Collections.singletonList(USER_PASSWORD_CHANGER_SCHEMA))
                    .password(newPassword)
                    .bypassNotification(Boolean.TRUE.equals(bypassNotification))
                    .build();

            PutUserPasswordChangerResponse response = client.putUserPasswordChanger(
                    PutUserPasswordChangerRequest.builder()
                            .userPasswordChangerId(userId)
                            .userPasswordChanger(changer)
                            .build()
            );

            UserPasswordChanger rsp = response.getUserPasswordChanger();
            return PasswordOperationRsp.builder()
                    .userId(userId)
                    .resourceId(rsp == null ? null : rsp.getId())
                    .operation("CHANGE_PASSWORD")
                    .notificationBypassed(Boolean.TRUE.equals(bypassNotification))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to change user password", e);
        }
    }

    /**
     * Identity Domains 随机重置密码
     */
    public static PasswordOperationRsp resetUserPassword(OracleInstanceFetcher fetcher,
                                                         String userId,
                                                         Boolean bypassNotification,
                                                         Boolean userFlowControlledByExternalClient) {
        return resetUserPassword(fetcher, userId, bypassNotification, userFlowControlledByExternalClient, null);
    }

    /**
     * Identity Domains 随机重置密码
     */
    public static PasswordOperationRsp resetUserPassword(OracleInstanceFetcher fetcher,
                                                         String userId,
                                                         Boolean bypassNotification,
                                                         Boolean userFlowControlledByExternalClient,
                                                         String domainUrl) {
        try {
            if (isSelfTarget(fetcher, userId)) {
                throw new IllegalStateException("当前目标是签名用户本人。随机重置密码仍走管理员接口，需要 Help Desk Administrator 或更高角色。");
            }
            IdentityDomainsClient client = prepareIdentityDomainsClient(fetcher, domainUrl);
            UserPasswordResetter resetter = UserPasswordResetter.builder()
                    .schemas(Collections.singletonList(USER_PASSWORD_RESETTER_SCHEMA))
                    .bypassNotification(Boolean.TRUE.equals(bypassNotification))
                    .userFlowControlledByExternalClient(Boolean.TRUE.equals(userFlowControlledByExternalClient))
                    .build();

            PutUserPasswordResetterResponse response = client.putUserPasswordResetter(
                    PutUserPasswordResetterRequest.builder()
                            .userPasswordResetterId(userId)
                            .userPasswordResetter(resetter)
                            .build()
            );

            UserPasswordResetter rsp = response.getUserPasswordResetter();
            return PasswordOperationRsp.builder()
                    .userId(userId)
                    .resourceId(rsp == null ? null : rsp.getId())
                    .operation("RESET_PASSWORD")
                    .notificationBypassed(Boolean.TRUE.equals(bypassNotification))
                    .userFlowControlledByExternalClient(Boolean.TRUE.equals(userFlowControlledByExternalClient))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to reset user password", e);
        }
    }

    /**
     * Identity Domains 恢复邮箱更新
     */
    public static String updateUserRecoveryEmail(OracleInstanceFetcher fetcher,
                                                 String userId,
                                                 String recoveryEmail,
                                                 String currentPassword) {
        return updateUserRecoveryEmail(fetcher, userId, recoveryEmail, currentPassword, null);
    }

    /**
     * Identity Domains 恢复邮箱更新
     */
    public static String updateUserRecoveryEmail(OracleInstanceFetcher fetcher,
                                                 String userId,
                                                 String recoveryEmail,
                                                 String currentPassword,
                                                 String domainUrl) {
        try {
            IdentityDomainsClient client = prepareIdentityDomainsClient(fetcher, domainUrl);
            if (isSelfTarget(fetcher, userId)) {
                if (StringUtils.isBlank(currentPassword)) {
                    throw new IllegalArgumentException("当前目标是签名用户本人，自助修改 recovery email 必须提供当前密码");
                }

                Me currentMe = client.getMe(GetMeRequest.builder().build()).getMe();
                if (currentMe == null) {
                    throw new RuntimeException("Current user not found in Identity Domains");
                }

                String normalizedRecoveryEmail = normalizeOptionalEmail(recoveryEmail);
                List<MeEmails> mergedEmails = mergeMeRecoveryEmail(currentMe.getEmails(), normalizedRecoveryEmail);

                Me updatedMe = currentMe.toBuilder()
                        .emails(mergedEmails)
                        .urnIetfParamsScimSchemasOracleIdcsExtensionMeUser(
                                ExtensionMeUser.builder()
                                        .currentPassword(currentPassword)
                                        .build()
                        )
                        .build();

                Me rsp = client.putMe(PutMeRequest.builder()
                        .me(updatedMe)
                        .build()).getMe();

                return extractRecoveryEmail(rsp == null ? updatedMe : rsp);
            }

            User currentUser = client.getUser(GetUserRequest.builder()
                    .userId(userId)
                    .build()).getUser();

            if (currentUser == null) {
                throw new RuntimeException("User not found in Identity Domains: " + userId);
            }

            String normalizedRecoveryEmail = normalizeOptionalEmail(recoveryEmail);
            List<UserEmails> mergedEmails = mergeUserRecoveryEmail(currentUser.getEmails(), normalizedRecoveryEmail);

            User updatedUser = currentUser.toBuilder()
                    .emails(mergedEmails)
                    .build();

            User rsp = client.putUser(PutUserRequest.builder()
                    .userId(userId)
                    .user(updatedUser)
                    .build()).getUser();

            return extractRecoveryEmail(rsp == null ? updatedUser : rsp);
        } catch (Exception e) {
            throw new RuntimeException("Failed to update user recovery email", e);
        }
    }

    /**
     * 列出密码策略
     */
    private static List<com.oracle.bmc.identitydomains.model.PasswordPolicy> listPasswordPolicies(IdentityDomainsClient domainsClient) {
        ListPasswordPoliciesResponse resp = domainsClient.listPasswordPolicies(
                ListPasswordPoliciesRequest.builder().build()
        );
        PasswordPolicies wrapper = resp.getPasswordPolicies();
        return wrapper != null && wrapper.getResources() != null ? wrapper.getResources() : Collections.emptyList();
    }

    private static String normalizeOptionalEmail(String email) {
        if (StringUtils.isBlank(email)) {
            return null;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!isValidEmail(normalized)) {
            throw new IllegalArgumentException("Invalid recovery email: " + email);
        }
        return normalized;
    }

    private static List<UserEmails> mergeUserRecoveryEmail(List<UserEmails> existingEmails, String recoveryEmail) {
        List<UserEmails> merged = Optional.ofNullable(existingEmails)
                .orElse(Collections.emptyList())
                .stream()
                .filter(Objects::nonNull)
                .filter(email -> !RECOVERY_EMAIL_TYPE.equals(email.getType()))
                .map(OciUtils::copyWritableEmail)
                .collect(Collectors.toCollection(ArrayList::new));

        if (StringUtils.isNotBlank(recoveryEmail)) {
            merged.add(UserEmails.builder()
                    .value(recoveryEmail)
                    .type(RECOVERY_EMAIL_TYPE)
                    .primary(Boolean.FALSE)
                    .build());
        }
        return merged;
    }

    private static List<MeEmails> mergeMeRecoveryEmail(List<MeEmails> existingEmails, String recoveryEmail) {
        List<MeEmails> merged = Optional.ofNullable(existingEmails)
                .orElse(Collections.emptyList())
                .stream()
                .filter(Objects::nonNull)
                .filter(email -> !ME_RECOVERY_EMAIL_TYPE.equals(email.getType()))
                .map(OciUtils::copyWritableEmail)
                .collect(Collectors.toCollection(ArrayList::new));

        if (StringUtils.isNotBlank(recoveryEmail)) {
            merged.add(MeEmails.builder()
                    .value(recoveryEmail)
                    .type(ME_RECOVERY_EMAIL_TYPE)
                    .primary(Boolean.FALSE)
                    .build());
        }
        return merged;
    }

    private static UserEmails copyWritableEmail(UserEmails email) {
        return UserEmails.builder()
                .value(email.getValue())
                .type(email.getType())
                .primary(email.getPrimary())
                .build();
    }

    private static MeEmails copyWritableEmail(MeEmails email) {
        return MeEmails.builder()
                .value(email.getValue())
                .type(email.getType())
                .primary(email.getPrimary())
                .secondary(email.getSecondary())
                .verified(email.getVerified())
                .build();
    }

    private static String extractRecoveryEmail(User user) {
        return Optional.ofNullable(user)
                .map(User::getEmails)
                .orElse(Collections.emptyList())
                .stream()
                .filter(Objects::nonNull)
                .filter(email -> RECOVERY_EMAIL_TYPE.equals(email.getType()))
                .map(UserEmails::getValue)
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse(null);
    }

    private static String extractRecoveryEmail(Me me) {
        return Optional.ofNullable(me)
                .map(Me::getEmails)
                .orElse(Collections.emptyList())
                .stream()
                .filter(Objects::nonNull)
                .filter(email -> ME_RECOVERY_EMAIL_TYPE.equals(email.getType()))
                .map(MeEmails::getValue)
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse(null);
    }

    private static boolean isSelfTarget(OracleInstanceFetcher fetcher, String userId) {
        String authUserId = Optional.ofNullable(fetcher)
                .map(OracleInstanceFetcher::getUser)
                .map(user -> user.getOciCfg().getUserId())
                .orElse(null);
        return StringUtils.isNotBlank(authUserId) && StringUtils.equals(authUserId, userId);
    }

    private static IdentityDomainsClient prepareIdentityDomainsClient(OracleInstanceFetcher fetcher) {
        return prepareIdentityDomainsClient(fetcher, null);
    }

    public static IdentityDomainsClient prepareIdentityDomainsClient(OracleInstanceFetcher fetcher, String explicitDomainUrl) {
        String tenantId = fetcher.getUser().getOciCfg().getTenantId();
        String domainUrl = StringUtils.isNotBlank(explicitDomainUrl)
                ? explicitDomainUrl
                : getDomain(fetcher.getIdentityClient(), tenantId);
        if (StringUtils.isBlank(domainUrl)) {
            throw new RuntimeException("No active Identity Domain found for tenant: " + tenantId);
        }
        IdentityDomainsClient identityDomainsClient = fetcher.getIdentityDomainsClient();
        identityDomainsClient.setEndpoint(domainUrl);
        return identityDomainsClient;
    }

    /**
     * 列出所有 Identity Domains
     */
    public static List<DomainSummary> listDomains(IdentityClient identityClient, String compartmentId) {
        try {
            ListDomainsResponse response = identityClient.listDomains(
                    ListDomainsRequest.builder().compartmentId(compartmentId).build()
            );
            return Optional.ofNullable(response.getItems())
                    .orElse(Collections.emptyList())
                    .stream()
                    .sorted(Comparator
                            .comparing((DomainSummary x) -> x.getType() != DomainSummary.Type.Default)
                            .thenComparing(x -> Optional.ofNullable(x.getDisplayName()).orElse("")))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to list domains", e);
        }
    }

    /**
     * 获取 Domain URL
     */
    public static String getDomain(IdentityClient identityClient, String compartmentId) {
        try {
            List<DomainSummary> domains = listDomains(identityClient, compartmentId);
            Optional<DomainSummary> targetDomain = domains.stream()
                    .filter(domain -> domain.getLifecycleState() == DomainSummary.LifecycleState.Active)
                    .filter(domain -> domain.getType() == DomainSummary.Type.Default)
                    .findFirst()
                    .or(() -> domains.stream()
                            .filter(domain -> domain.getLifecycleState() == DomainSummary.LifecycleState.Active)
                            .findFirst());
            if (targetDomain.isPresent()) {
                log.debug("Found domain [{}] URL: {}", targetDomain.get().getDisplayName(), targetDomain.get().getUrl());
                return targetDomain.get().getUrl();
            }
            log.error("No active domain found in compartment: {}", compartmentId);
            return "";
        } catch (Exception e) {
            throw new RuntimeException("Failed to get domain", e);
        }
    }
}
