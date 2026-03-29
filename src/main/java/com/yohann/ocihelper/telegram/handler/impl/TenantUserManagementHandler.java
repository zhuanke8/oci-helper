package com.yohann.ocihelper.telegram.handler.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.yohann.ocihelper.bean.params.oci.tenant.GetTenantInfoParams;
import com.yohann.ocihelper.bean.params.oci.tenant.ResetUserPasswordParams;
import com.yohann.ocihelper.bean.params.oci.tenant.UpdateUserBasicParams;
import com.yohann.ocihelper.bean.params.oci.tenant.UpdateUserRecoveryEmailParams;
import com.yohann.ocihelper.bean.response.oci.tenant.TenantInfoRsp;
import com.yohann.ocihelper.service.ITenantService;
import com.yohann.ocihelper.telegram.handler.AbstractCallbackHandler;
import com.yohann.ocihelper.telegram.storage.ConfigSessionStorage;
import com.yohann.ocihelper.telegram.storage.PaginationStorage;
import com.yohann.ocihelper.telegram.storage.TenantUserSelectionStorage;
import com.yohann.ocihelper.telegram.utils.TenantUserMenuHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.generics.TelegramClient;

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
                        TenantUserMenuHelper.buildBackToConfigMarkup(ociCfgId)
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
                    TenantUserMenuHelper.buildBackToConfigMarkup(ociCfgId)
            );
        }
    }

    static List<TenantInfoRsp.TenantUserInfo> loadAndCacheUsers(long chatId, String ociCfgId) {
        ITenantService tenantService = SpringUtil.getBean(ITenantService.class);
        GetTenantInfoParams params = new GetTenantInfoParams();
        params.setOciCfgId(ociCfgId);
        params.setCleanReLaunch(false);

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

        try {
            ITenantService tenantService = SpringUtil.getBean(ITenantService.class);
            UpdateUserRecoveryEmailParams params = new UpdateUserRecoveryEmailParams();
            params.setOciCfgId(ociCfgId);
            params.setUserId(user.getId());
            params.setRecoveryEmail(null);
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

        return TenantUserManagementHandler.buildTenantEditMessage(
                callbackQuery,
                prompt,
                TenantUserMenuHelper.buildInputMarkup(userIndex)
        );
    }
}
