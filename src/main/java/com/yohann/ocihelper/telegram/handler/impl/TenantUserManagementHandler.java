package com.yohann.ocihelper.telegram.handler.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.oracle.bmc.identitydomains.requests.GetUserRequest;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.bean.params.oci.tenant.GetTenantInfoParams;
import com.yohann.ocihelper.bean.params.oci.tenant.ResetUserPasswordParams;
import com.yohann.ocihelper.bean.params.oci.tenant.UpdateUserBasicParams;
import com.yohann.ocihelper.bean.params.oci.tenant.UpdateUserRecoveryEmailParams;
import com.yohann.ocihelper.bean.response.oci.tenant.IdentityDomainRsp;
import com.yohann.ocihelper.bean.response.oci.tenant.TenantInfoRsp;
import com.yohann.ocihelper.config.OracleInstanceFetcher;
import com.yohann.ocihelper.service.ISysService;
import com.yohann.ocihelper.service.ITenantService;
import com.yohann.ocihelper.telegram.handler.AbstractCallbackHandler;
import com.yohann.ocihelper.telegram.storage.ConfigSessionStorage;
import com.yohann.ocihelper.telegram.storage.IdentityDomainSelectionStorage;
import com.yohann.ocihelper.telegram.storage.PaginationStorage;
import com.yohann.ocihelper.telegram.storage.TenantUserSelectionStorage;
import com.yohann.ocihelper.telegram.utils.TenantUserMenuHelper;
import com.yohann.ocihelper.utils.OciUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.File;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * TG tenant user management handlers.
 */
@Slf4j
@Component
public class TenantUserManagementHandler extends AbstractCallbackHandler {

    private static final TenantUserManagementHandler MESSAGE_HELPER = new TenantUserManagementHandler();

    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        String ociCfgId = callbackQuery.getData().split(":", 2)[1];
        long chatId = callbackQuery.getMessage().getChatId();

        PaginationStorage.getInstance().resetPage(chatId, TenantUserMenuHelper.getPageType(ociCfgId));
        return renderUserList(callbackQuery, chatId, ociCfgId, true);
    }

    @Override
    public String getCallbackPattern() {
        return "tenant_user_management:";
    }

    static BotApiMethod<? extends Serializable> renderUserList(CallbackQuery callbackQuery,
                                                               long chatId,
                                                               String ociCfgId,
                                                               boolean reload) {
        try {
            TenantUserSelectionStorage storage = TenantUserSelectionStorage.getInstance();
            List<TenantInfoRsp.TenantUserInfo> users = reload ? loadAndCacheUsers(chatId, ociCfgId) : storage.getCachedUsers(chatId);

            if (CollectionUtil.isEmpty(users)) {
                return buildTenantEditMessage(
                        callbackQuery,
                        "❌ 当前租户下暂无用户",
                        TenantUserMenuHelper.buildBackToConfigMarkup(chatId, ociCfgId)
                );
            }

            clampPage(chatId, ociCfgId, users.size());
            return buildTenantEditMessage(
                    callbackQuery,
                    TenantUserMenuHelper.buildUserListText(users, chatId, ociCfgId),
                    TenantUserMenuHelper.buildUserListMarkup(users, chatId, ociCfgId)
            );
        } catch (Exception e) {
            log.error("Failed to render tenant users for ociCfgId: {}", ociCfgId, e);
            return buildTenantEditMessage(
                    callbackQuery,
                    "❌ 获取租户用户失败：" + e.getMessage(),
                    TenantUserMenuHelper.buildBackToConfigMarkup(chatId, ociCfgId)
            );
        }
    }

    static List<TenantInfoRsp.TenantUserInfo> loadAndCacheUsers(long chatId, String ociCfgId) {
        ITenantService tenantService = SpringUtil.getBean(ITenantService.class);
        GetTenantInfoParams params = new GetTenantInfoParams();
        params.setOciCfgId(ociCfgId);
        params.setCleanReLaunch(false);
        IdentityDomainRsp selectedDomain = getSelectedDomain(chatId);
        if (selectedDomain != null) {
            params.setDomainId(selectedDomain.getId());
            params.setDomainName(selectedDomain.getDisplayName());
            params.setDomainUrl(selectedDomain.getUrl());
        }

        List<TenantInfoRsp.TenantUserInfo> users = tenantService.tenantInfo(params).getUserList();
        users = users == null ? Collections.emptyList() : users;

        TenantUserSelectionStorage storage = TenantUserSelectionStorage.getInstance();
        storage.setConfigContext(chatId, ociCfgId);
        storage.setUserCache(chatId, users);
        return users;
    }

    static void clampPage(long chatId, String ociCfgId, int totalUsers) {
        PaginationStorage paginationStorage = PaginationStorage.getInstance();
        String pageType = TenantUserMenuHelper.getPageType(ociCfgId);
        int totalPages = Math.max(PaginationStorage.calculateTotalPages(totalUsers, TenantUserMenuHelper.PAGE_SIZE), 1);
        int currentPage = paginationStorage.getCurrentPage(chatId, pageType);
        if (currentPage >= totalPages) {
            paginationStorage.setCurrentPage(chatId, pageType, totalPages - 1);
        }
    }

    static TenantInfoRsp.TenantUserInfo getCachedUser(long chatId, int userIndex) {
        return TenantUserSelectionStorage.getInstance().getUserByIndex(chatId, userIndex);
    }

    static String getOciCfgId(long chatId) {
        return TenantUserSelectionStorage.getInstance().getConfigContext(chatId);
    }

    static IdentityDomainRsp getSelectedDomain(long chatId) {
        return IdentityDomainSelectionStorage.getInstance().getSelectedDomain(chatId);
    }

    static void applySelectedDomainContext(long chatId, UpdateUserBasicParams params) {
        if (params == null) {
            return;
        }
        IdentityDomainRsp selectedDomain = getSelectedDomain(chatId);
        if (selectedDomain == null) {
            return;
        }
        params.setDomainId(selectedDomain.getId());
        params.setDomainName(selectedDomain.getDisplayName());
        params.setDomainUrl(selectedDomain.getUrl());
    }

    static boolean isSelfTargetUser(long chatId, String ociCfgId, TenantInfoRsp.TenantUserInfo cachedUser) {
        ISysService sysService = SpringUtil.getBean(ISysService.class);
        SysUserDTO authUser = sysService.getOciUser(ociCfgId);
        return isSelfTargetUser(getSelectedDomain(chatId), authUser, null, cachedUser);
    }

    static boolean isSelfTargetUser(IdentityDomainRsp selectedDomain,
                                    SysUserDTO authUser,
                                    String domainUserOcid,
                                    TenantInfoRsp.TenantUserInfo cachedUser) {
        if (selectedDomain != null && !Boolean.TRUE.equals(selectedDomain.getDefaultDomain())) {
            return false;
        }
        String authUserId = authUser == null || authUser.getOciCfg() == null ? null : authUser.getOciCfg().getUserId();
        if (authUserId == null || authUserId.isBlank()) {
            return false;
        }
        if (cachedUser != null && authUserId.equals(cachedUser.getId())) {
            return true;
        }
        if (cachedUser != null && authUserId.equals(cachedUser.getOcid())) {
            return true;
        }
        return domainUserOcid != null && !domainUserOcid.isBlank() && authUserId.equals(domainUserOcid);
    }

    static String buildDiagnosisText(long chatId, String ociCfgId, TenantInfoRsp.TenantUserInfo cachedUser) {
        ISysService sysService = SpringUtil.getBean(ISysService.class);
        SysUserDTO authUser = sysService.getOciUser(ociCfgId);
        IdentityDomainRsp selectedDomain = getSelectedDomain(chatId);

        String endpoint = "未获取";
        String domainUserId = "未获取";
        String domainUserOcid = defaultValue(cachedUser.getOcid(), defaultValue(cachedUser.getId(), "未获取"));
        String domainDisplayName = defaultValue(cachedUser.getName(), "未知");
        String domainEmail = defaultValue(cachedUser.getEmail(), "未设置");
        String domainError = null;

        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(authUser)) {
            endpoint = defaultValue(
                    selectedDomain != null ? selectedDomain.getUrl() : OciUtils.getDomain(fetcher.getIdentityClient(), authUser.getOciCfg().getTenantId()),
                    "未获取"
            );
            if (!"未获取".equals(endpoint)) {
                fetcher.getIdentityDomainsClient().setEndpoint(endpoint);
                com.oracle.bmc.identitydomains.model.User domainUser = fetcher.getIdentityDomainsClient()
                        .getUser(GetUserRequest.builder().userId(cachedUser.getId()).build())
                        .getUser();
                if (domainUser != null) {
                    domainUserId = defaultValue(domainUser.getId(), domainUserId);
                    domainUserOcid = defaultValue(domainUser.getOcid(), domainUserOcid);
                    domainDisplayName = defaultValue(domainUser.getDisplayName(), domainDisplayName);
                    domainEmail = defaultValue(domainUser.getUserName(), domainEmail);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to collect tenant user diagnostic info, ociCfgId={}, userId={}", ociCfgId, cachedUser.getId(), e);
            domainError = shortenMessage(e.getMessage());
        }

        StringBuilder message = new StringBuilder();
        message.append("【权限诊断】\n\n");
        message.append("签名说明: Identity Domains 请求使用当前 OCI 配置里的 API User 签名，不会切换成目标用户身份。\n\n");
        message.append("当前配置:\n");
        message.append(String.format("配置名: %s\n", md(defaultValue(authUser.getUsername(), "未知"))));
        message.append(String.format("区域: %s\n", md(defaultValue(authUser.getOciCfg().getRegion(), "未知"))));
        message.append(String.format("租户 OCID: %s\n", md(defaultValue(authUser.getOciCfg().getTenantId(), "未知"))));
        message.append(String.format("签名用户 OCID: %s\n", md(defaultValue(authUser.getOciCfg().getUserId(), "未知"))));
        message.append(String.format("指纹: %s\n", md(defaultValue(authUser.getOciCfg().getFingerprint(), "未知"))));
        message.append(String.format("私钥文件: %s\n\n", md(getFileName(authUser.getOciCfg().getPrivateKeyPath()))));

        message.append("Identity Domains:\n");
        if (selectedDomain != null) {
            message.append(String.format("当前域: %s\n", md(defaultValue(selectedDomain.getDisplayName(), "未知"))));
            message.append(String.format("当前域 ID: %s\n", md(defaultValue(selectedDomain.getId(), "未知"))));
            message.append(String.format("当前域类型: %s\n", md(defaultValue(selectedDomain.getType(), "未知"))));
            message.append(String.format("当前域状态: %s\n", md(defaultValue(selectedDomain.getLifecycleState(), "未知"))));
        }
        message.append(String.format("Endpoint: %s\n", md(endpoint)));
        message.append(String.format("目标用户请求 userId: %s\n", md(defaultValue(cachedUser.getId(), "未知"))));
        message.append(String.format("目标用户 domain id: %s\n", md(domainUserId)));
        message.append(String.format("目标用户 domain ocid: %s\n", md(domainUserOcid)));
        message.append(String.format("目标用户名称: %s\n", md(domainDisplayName)));
        message.append(String.format("目标用户邮箱: %s\n", md(domainEmail)));
        message.append(String.format("当前 MFA 状态: %s\n", Boolean.TRUE.equals(cachedUser.getIsMfaActivated()) ? "已启用" : "未启用"));
        if (domainError != null) {
            message.append(String.format("域信息补充: %s\n", md(domainError)));
        }

        boolean selfTarget = isSelfTargetUser(selectedDomain, authUser, domainUserOcid, cachedUser);
        message.append("\n权限判断:\n");
        if (selfTarget) {
            message.append("当前目标就是签名用户本人，管理员接口报 401 并不代表签名错误，而是接口类型选错或缺少本人当前密码。\n");
            message.append("1. 随机重置密码 报 401:\n");
            message.append("这条仍走管理员重置接口，需要 Help Desk Administrator 或更高角色。\n");
            message.append("2. 指定密码:\n");
            message.append("本人账号走 MePasswordChanger，自助改密需要 oldPassword。当前 TG 已改为两段式输入：旧密码 -> 新密码。\n");
            message.append("3. 设置 / 清空 recovery email:\n");
            message.append("本人账号走 Me PUT。官方文档要求修改恢复相关属性时提供 currentPassword。当前 TG 已改为两段式输入：当前密码 -> 新 recovery email；清空时为当前密码 -> 执行清空。\n");
            message.append("4. 清除 MFA:\n");
            message.append("本人账号应走 MyAuthenticationFactorsRemover，自助路径不依赖 Help Desk Administrator。若仍失败，再看域策略或账号状态。");
        } else {
            message.append("1. 指定密码 / 随机重置密码 报 401:\n");
            message.append("需要 Help Desk Administrator 或更高角色。\n");
            message.append("2. 设置 / 清空 recovery email 报 401:\n");
            message.append("需要 Users | ALL，通常是 User Manager、User Administrator 或 Identity Domain Administrator。\n");
            message.append("3. 清除 MFA 报 401:\n");
            message.append("需要 AuthenticationFactorsRemover | POST，Help Desk Administrator 或更高角色可执行。\n");
            message.append("4. 如果这三类都报 401:\n");
            message.append("当前配置账号大概率只有 Security Administrator，或者根本没有分配到目标 Identity Domain 的管理员角色。");
        }
        return message.toString();
    }

    static String md(String text) {
        return TenantUserMenuHelper.escapeMarkdown(defaultValue(text, "未设置"));
    }

    static String defaultValue(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    static String getFileName(String path) {
        if (path == null || path.isBlank()) {
            return "未设置";
        }
        return new File(path).getName();
    }

    static String shortenMessage(String message) {
        String normalized = defaultValue(message, "未知错误").replace("\r", " ").replace("\n", " ");
        return normalized.length() > 160 ? normalized.substring(0, 160) + "..." : normalized;
    }

    static BotApiMethod<? extends Serializable> buildTenantEditMessage(CallbackQuery callbackQuery,
                                                                       String text,
                                                                       InlineKeyboardMarkup markup) {
        return MESSAGE_HELPER.buildEditMessage(callbackQuery, text, markup);
    }

    static BotApiMethod<? extends Serializable> buildTenantEditMessage(CallbackQuery callbackQuery, String text) {
        return MESSAGE_HELPER.buildEditMessage(callbackQuery, text);
    }
}

@Slf4j
@Component
class TenantUserManagementDefaultHandler extends AbstractCallbackHandler {

    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        String ociCfgId = callbackQuery.getData().split(":", 2)[1];
        long chatId = callbackQuery.getMessage().getChatId();

        IdentityDomainSelectionStorage.getInstance().setSelectedDomain(chatId, null);
        PaginationStorage.getInstance().resetPage(chatId, TenantUserMenuHelper.getPageType(ociCfgId));
        return TenantUserManagementHandler.renderUserList(callbackQuery, chatId, ociCfgId, true);
    }

    @Override
    public String getCallbackPattern() {
        return "tenant_user_management_default:";
    }
}

@Slf4j
@Component
class RefreshTenantUsersHandler extends AbstractCallbackHandler {

    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        long chatId = callbackQuery.getMessage().getChatId();
        String ociCfgId = TenantUserManagementHandler.getOciCfgId(chatId);
        if (ociCfgId == null) {
            return buildEditMessage(callbackQuery, "❌ 用户上下文已失效，请重新进入配置操作");
        }
        return TenantUserManagementHandler.renderUserList(callbackQuery, chatId, ociCfgId, true);
    }

    @Override
    public String getCallbackPattern() {
        return "refresh_tenant_users";
    }
}

@Slf4j
@Component
class TenantUserPageNavigationHandler extends AbstractCallbackHandler {

    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        long chatId = callbackQuery.getMessage().getChatId();
        String ociCfgId = TenantUserManagementHandler.getOciCfgId(chatId);
        if (ociCfgId == null) {
            return buildEditMessage(callbackQuery, "❌ 用户上下文已失效，请重新进入配置操作");
        }

        List<TenantInfoRsp.TenantUserInfo> users = TenantUserSelectionStorage.getInstance().getCachedUsers(chatId);
        if (CollectionUtil.isEmpty(users)) {
            return TenantUserManagementHandler.renderUserList(callbackQuery, chatId, ociCfgId, true);
        }

        String pageType = TenantUserMenuHelper.getPageType(ociCfgId);
        PaginationStorage paginationStorage = PaginationStorage.getInstance();
        int totalPages = Math.max(PaginationStorage.calculateTotalPages(users.size(), TenantUserMenuHelper.PAGE_SIZE), 1);
        if (callbackQuery.getData().endsWith("prev")) {
            paginationStorage.previousPage(chatId, pageType);
        } else {
            paginationStorage.nextPage(chatId, pageType, totalPages);
        }
        return TenantUserManagementHandler.renderUserList(callbackQuery, chatId, ociCfgId, false);
    }

    @Override
    public String getCallbackPattern() {
        return "tenant_user_page_";
    }
}

@Slf4j
@Component
class TenantUserSelectHandler extends AbstractCallbackHandler {

    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        int userIndex = Integer.parseInt(callbackQuery.getData().split(":", 2)[1]);
        long chatId = callbackQuery.getMessage().getChatId();
        String ociCfgId = TenantUserManagementHandler.getOciCfgId(chatId);
        TenantInfoRsp.TenantUserInfo user = TenantUserManagementHandler.getCachedUser(chatId, userIndex);

        if (ociCfgId == null || user == null) {
            return buildEditMessage(callbackQuery, "❌ 用户不存在或上下文已失效，请重新打开用户列表");
        }

        return buildEditMessage(
                callbackQuery,
                TenantUserMenuHelper.buildUserDetailText(user, userIndex, null),
                TenantUserMenuHelper.buildUserDetailMarkup(ociCfgId, userIndex)
        );
    }

    @Override
    public String getCallbackPattern() {
        return "tenant_user_select:";
    }
}

@Slf4j
@Component
class TenantUserResetPasswordHandler extends AbstractCallbackHandler {

    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        int userIndex = Integer.parseInt(callbackQuery.getData().split(":", 2)[1]);
        long chatId = callbackQuery.getMessage().getChatId();
        String ociCfgId = TenantUserManagementHandler.getOciCfgId(chatId);
        TenantInfoRsp.TenantUserInfo user = TenantUserManagementHandler.getCachedUser(chatId, userIndex);

        if (ociCfgId == null || user == null) {
            return buildEditMessage(callbackQuery, "❌ 用户不存在或上下文已失效，请重新打开用户列表");
        }

        try {
            ITenantService tenantService = SpringUtil.getBean(ITenantService.class);
            ResetUserPasswordParams params = new ResetUserPasswordParams();
            params.setOciCfgId(ociCfgId);
            params.setUserId(user.getId());
            params.setBypassNotification(Boolean.FALSE);
            params.setUserFlowControlledByExternalClient(Boolean.FALSE);
            TenantUserManagementHandler.applySelectedDomainContext(chatId, params);
            tenantService.resetPassword(params);

            return buildEditMessage(
                    callbackQuery,
                    TenantUserMenuHelper.buildUserDetailText(user, userIndex, "✅ 已触发随机密码重置"),
                    TenantUserMenuHelper.buildUserDetailMarkup(ociCfgId, userIndex)
            );
        } catch (Exception e) {
            log.error("Failed to reset tenant user password, ociCfgId={}, userId={}", ociCfgId, user.getId(), e);
            return buildEditMessage(
                    callbackQuery,
                    TenantUserMenuHelper.buildUserDetailText(user, userIndex, "❌ 随机密码重置失败：" + e.getMessage()),
                    TenantUserMenuHelper.buildUserDetailMarkup(ociCfgId, userIndex)
            );
        }
    }

    @Override
    public String getCallbackPattern() {
        return "tenant_user_reset_password:";
    }
}

@Slf4j
@Component
class TenantUserPromptChangePasswordHandler extends AbstractCallbackHandler {

    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        int userIndex = Integer.parseInt(callbackQuery.getData().split(":", 2)[1]);
        long chatId = callbackQuery.getMessage().getChatId();
        String ociCfgId = TenantUserManagementHandler.getOciCfgId(chatId);
        TenantInfoRsp.TenantUserInfo user = TenantUserManagementHandler.getCachedUser(chatId, userIndex);
        if (ociCfgId == null || user == null) {
            return buildEditMessage(callbackQuery, "❌ 用户不存在或上下文已失效，请重新打开用户列表");
        }
        if (TenantUserManagementHandler.isSelfTargetUser(chatId, ociCfgId, user)) {
            BotApiMethod<? extends Serializable> promptMessage = TenantUserInputSessionSupport.startInputSession(
                    callbackQuery,
                    ConfigSessionStorage.SessionType.TENANT_USER_CHANGE_PASSWORD,
                    "【指定密码】\n\n当前目标是签名用户本人。\n\n第 1 步：请输入当前登录密码。\n\n发送 /cancel 可取消当前输入。"
            );
            ConfigSessionStorage.SessionState state = ConfigSessionStorage.getInstance().getSessionState(chatId);
            if (state != null) {
                state.getData().put("selfService", Boolean.TRUE);
                state.getData().put("step", "old_password");
            }
            return promptMessage;
        }
        return TenantUserInputSessionSupport.startInputSession(
                callbackQuery,
                ConfigSessionStorage.SessionType.TENANT_USER_CHANGE_PASSWORD,
                "【指定密码】\n\n请输入新的登录密码。\n\n发送 /cancel 可取消当前输入。"
        );
    }

    @Override
    public String getCallbackPattern() {
        return "tenant_user_prompt_change_password:";
    }
}

@Slf4j
@Component
class TenantUserPromptRecoveryEmailHandler extends AbstractCallbackHandler {

    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        int userIndex = Integer.parseInt(callbackQuery.getData().split(":", 2)[1]);
        long chatId = callbackQuery.getMessage().getChatId();
        String ociCfgId = TenantUserManagementHandler.getOciCfgId(chatId);
        TenantInfoRsp.TenantUserInfo user = TenantUserManagementHandler.getCachedUser(chatId, userIndex);
        if (ociCfgId == null || user == null) {
            return buildEditMessage(callbackQuery, "❌ 用户不存在或上下文已失效，请重新打开用户列表");
        }
        if (TenantUserManagementHandler.isSelfTargetUser(chatId, ociCfgId, user)) {
            BotApiMethod<? extends Serializable> promptMessage = TenantUserInputSessionSupport.startInputSession(
                    callbackQuery,
                    ConfigSessionStorage.SessionType.TENANT_USER_RECOVERY_EMAIL,
                    "【设置 recovery email】\n\n当前目标是签名用户本人。\n\n第 1 步：请输入当前登录密码。\n\n发送 /cancel 可取消当前输入。"
            );
            ConfigSessionStorage.SessionState state = ConfigSessionStorage.getInstance().getSessionState(chatId);
            if (state != null) {
                state.getData().put("selfService", Boolean.TRUE);
                state.getData().put("step", "current_password");
                state.getData().put("action", "set");
            }
            return promptMessage;
        }
        return TenantUserInputSessionSupport.startInputSession(
                callbackQuery,
                ConfigSessionStorage.SessionType.TENANT_USER_RECOVERY_EMAIL,
                "【设置 recovery email】\n\n请输入新的恢复邮箱地址。\n\n如需清空，请返回用户详情后点击“清空恢复邮箱”。\n发送 /cancel 可取消当前输入。"
        );
    }

    @Override
    public String getCallbackPattern() {
        return "tenant_user_prompt_recovery_email:";
    }
}

@Slf4j
@Component
class TenantUserClearRecoveryEmailHandler extends AbstractCallbackHandler {

    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        int userIndex = Integer.parseInt(callbackQuery.getData().split(":", 2)[1]);
        long chatId = callbackQuery.getMessage().getChatId();
        String ociCfgId = TenantUserManagementHandler.getOciCfgId(chatId);
        TenantInfoRsp.TenantUserInfo user = TenantUserManagementHandler.getCachedUser(chatId, userIndex);

        if (ociCfgId == null || user == null) {
            return buildEditMessage(callbackQuery, "❌ 用户不存在或上下文已失效，请重新打开用户列表");
        }
        if (TenantUserManagementHandler.isSelfTargetUser(chatId, ociCfgId, user)) {
            BotApiMethod<? extends Serializable> promptMessage = TenantUserInputSessionSupport.startInputSession(
                    callbackQuery,
                    ConfigSessionStorage.SessionType.TENANT_USER_RECOVERY_EMAIL,
                    "【清空 recovery email】\n\n当前目标是签名用户本人。\n\n请输入当前登录密码后执行清空。\n\n发送 /cancel 可取消当前输入。"
            );
            ConfigSessionStorage.SessionState state = ConfigSessionStorage.getInstance().getSessionState(chatId);
            if (state != null) {
                state.getData().put("selfService", Boolean.TRUE);
                state.getData().put("step", "current_password");
                state.getData().put("action", "clear");
            }
            return promptMessage;
        }

        try {
            ITenantService tenantService = SpringUtil.getBean(ITenantService.class);
            UpdateUserRecoveryEmailParams params = new UpdateUserRecoveryEmailParams();
            params.setOciCfgId(ociCfgId);
            params.setUserId(user.getId());
            params.setRecoveryEmail(null);
            TenantUserManagementHandler.applySelectedDomainContext(chatId, params);
            tenantService.updateRecoveryEmail(params);

            return buildEditMessage(
                    callbackQuery,
                    TenantUserMenuHelper.buildUserDetailText(user, userIndex, "✅ recovery email 已清空"),
                    TenantUserMenuHelper.buildUserDetailMarkup(ociCfgId, userIndex)
            );
        } catch (Exception e) {
            log.error("Failed to clear tenant user recovery email, ociCfgId={}, userId={}", ociCfgId, user.getId(), e);
            return buildEditMessage(
                    callbackQuery,
                    TenantUserMenuHelper.buildUserDetailText(user, userIndex, "❌ 清空 recovery email 失败：" + e.getMessage()),
                    TenantUserMenuHelper.buildUserDetailMarkup(ociCfgId, userIndex)
            );
        }
    }

    @Override
    public String getCallbackPattern() {
        return "tenant_user_clear_recovery_email:";
    }
}

@Slf4j
@Component
class TenantUserDiagnosisHandler extends AbstractCallbackHandler {

    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        int userIndex = Integer.parseInt(callbackQuery.getData().split(":", 2)[1]);
        long chatId = callbackQuery.getMessage().getChatId();
        String ociCfgId = TenantUserManagementHandler.getOciCfgId(chatId);
        TenantInfoRsp.TenantUserInfo user = TenantUserManagementHandler.getCachedUser(chatId, userIndex);

        if (ociCfgId == null || user == null) {
            return buildEditMessage(callbackQuery, "❌ 用户不存在或上下文已失效，请重新打开用户列表");
        }

        try {
            return buildEditMessage(
                    callbackQuery,
                    TenantUserManagementHandler.buildDiagnosisText(chatId, ociCfgId, user),
                    TenantUserMenuHelper.buildDiagnosisMarkup(ociCfgId, userIndex)
            );
        } catch (Exception e) {
            log.error("Failed to diagnose tenant user permissions, ociCfgId={}, userId={}", ociCfgId, user.getId(), e);
            return buildEditMessage(
                    callbackQuery,
                    "❌ 生成诊断信息失败：" + TenantUserManagementHandler.shortenMessage(e.getMessage()),
                    TenantUserMenuHelper.buildUserDetailMarkup(ociCfgId, userIndex)
            );
        }
    }

    @Override
    public String getCallbackPattern() {
        return "tenant_user_diagnose:";
    }
}

@Slf4j
@Component
class TenantUserClearMfaHandler extends AbstractCallbackHandler {

    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        int userIndex = Integer.parseInt(callbackQuery.getData().split(":", 2)[1]);
        long chatId = callbackQuery.getMessage().getChatId();
        String ociCfgId = TenantUserManagementHandler.getOciCfgId(chatId);
        TenantInfoRsp.TenantUserInfo user = TenantUserManagementHandler.getCachedUser(chatId, userIndex);

        if (ociCfgId == null || user == null) {
            return buildEditMessage(callbackQuery, "❌ 用户不存在或上下文已失效，请重新打开用户列表");
        }
        if (!Boolean.TRUE.equals(user.getIsMfaActivated())) {
            return buildEditMessage(
                    callbackQuery,
                    TenantUserMenuHelper.buildUserDetailText(user, userIndex, "ℹ️ 当前用户未启用 MFA"),
                    TenantUserMenuHelper.buildUserDetailMarkup(ociCfgId, userIndex)
            );
        }

        try {
            ITenantService tenantService = SpringUtil.getBean(ITenantService.class);
            UpdateUserBasicParams params = new UpdateUserBasicParams();
            params.setOciCfgId(ociCfgId);
            params.setUserId(user.getId());
            TenantUserManagementHandler.applySelectedDomainContext(chatId, params);
            tenantService.deleteMfaDevice(params);
            user.setIsMfaActivated(Boolean.FALSE);

            return buildEditMessage(
                    callbackQuery,
                    TenantUserMenuHelper.buildUserDetailText(user, userIndex, "✅ 已清除该用户 MFA 因子"),
                    TenantUserMenuHelper.buildUserDetailMarkup(ociCfgId, userIndex)
            );
        } catch (Exception e) {
            log.error("Failed to clear tenant user MFA, ociCfgId={}, userId={}", ociCfgId, user.getId(), e);
            return buildEditMessage(
                    callbackQuery,
                    TenantUserMenuHelper.buildUserDetailText(user, userIndex, "❌ 清除 MFA 失败：" + e.getMessage()),
                    TenantUserMenuHelper.buildUserDetailMarkup(ociCfgId, userIndex)
            );
        }
    }

    @Override
    public String getCallbackPattern() {
        return "tenant_user_clear_mfa:";
    }
}

@Slf4j
@Component
class TenantUserCancelInputHandler extends AbstractCallbackHandler {

    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        int userIndex = Integer.parseInt(callbackQuery.getData().split(":", 2)[1]);
        long chatId = callbackQuery.getMessage().getChatId();
        ConfigSessionStorage.getInstance().clearSession(chatId);

        String ociCfgId = TenantUserManagementHandler.getOciCfgId(chatId);
        TenantInfoRsp.TenantUserInfo user = TenantUserManagementHandler.getCachedUser(chatId, userIndex);
        if (ociCfgId == null || user == null) {
            return buildEditMessage(callbackQuery, "❌ 用户不存在或上下文已失效，请重新打开用户列表");
        }
        return buildEditMessage(
                callbackQuery,
                TenantUserMenuHelper.buildUserDetailText(user, userIndex, null),
                TenantUserMenuHelper.buildUserDetailMarkup(ociCfgId, userIndex)
        );
    }

    @Override
    public String getCallbackPattern() {
        return "tenant_user_cancel_input:";
    }
}

final class TenantUserInputSessionSupport {

    private TenantUserInputSessionSupport() {
    }

    static BotApiMethod<? extends Serializable> startInputSession(CallbackQuery callbackQuery,
                                                                  ConfigSessionStorage.SessionType sessionType,
                                                                  String prompt) {
        int userIndex = Integer.parseInt(callbackQuery.getData().split(":", 2)[1]);
        long chatId = callbackQuery.getMessage().getChatId();
        String ociCfgId = TenantUserManagementHandler.getOciCfgId(chatId);
        TenantInfoRsp.TenantUserInfo user = TenantUserManagementHandler.getCachedUser(chatId, userIndex);
        if (ociCfgId == null || user == null) {
            return TenantUserManagementHandler.buildTenantEditMessage(callbackQuery, "❌ 用户不存在或上下文已失效，请重新打开用户列表");
        }

        ConfigSessionStorage configStorage = ConfigSessionStorage.getInstance();
        configStorage.startSession(chatId, sessionType);
        ConfigSessionStorage.SessionState state = configStorage.getSessionState(chatId);
        state.getData().put("ociCfgId", ociCfgId);
        state.getData().put("userId", user.getId());
        state.getData().put("userIndex", userIndex);
        state.getData().put("messageId", callbackQuery.getMessage().getMessageId());
        IdentityDomainRsp selectedDomain = TenantUserManagementHandler.getSelectedDomain(chatId);
        if (selectedDomain != null) {
            state.getData().put("domainId", selectedDomain.getId());
            state.getData().put("domainName", selectedDomain.getDisplayName());
            state.getData().put("domainUrl", selectedDomain.getUrl());
        }

        return TenantUserManagementHandler.buildTenantEditMessage(
                callbackQuery,
                prompt,
                TenantUserMenuHelper.buildInputMarkup(userIndex)
        );
    }
}
