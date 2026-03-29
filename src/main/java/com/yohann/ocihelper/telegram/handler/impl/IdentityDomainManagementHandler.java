package com.yohann.ocihelper.telegram.handler.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.yohann.ocihelper.bean.params.oci.tenant.GetIdentityDomainsParams;
import com.yohann.ocihelper.bean.params.oci.tenant.UpdateDomainStateParams;
import com.yohann.ocihelper.bean.response.oci.tenant.IdentityDomainRsp;
import com.yohann.ocihelper.service.ITenantService;
import com.yohann.ocihelper.telegram.builder.KeyboardBuilder;
import com.yohann.ocihelper.telegram.handler.AbstractCallbackHandler;
import com.yohann.ocihelper.telegram.storage.IdentityDomainSelectionStorage;
import com.yohann.ocihelper.telegram.storage.PaginationStorage;
import com.yohann.ocihelper.telegram.utils.IdentityDomainMenuHelper;
import com.yohann.ocihelper.telegram.utils.TenantUserMenuHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class IdentityDomainManagementHandler extends AbstractCallbackHandler {

    private static final IdentityDomainManagementHandler MESSAGE_HELPER = new IdentityDomainManagementHandler();

    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        String ociCfgId = callbackQuery.getData().split(":", 2)[1];
        long chatId = callbackQuery.getMessage().getChatId();
        return renderDomainList(callbackQuery, chatId, ociCfgId, true);
    }

    @Override
    public String getCallbackPattern() {
        return "identity_domain_management:";
    }

    static BotApiMethod<? extends Serializable> renderDomainList(CallbackQuery callbackQuery,
                                                                 long chatId,
                                                                 String ociCfgId,
                                                                 boolean reload) {
        try {
            IdentityDomainSelectionStorage storage = IdentityDomainSelectionStorage.getInstance();
            List<IdentityDomainRsp> domains = reload ? loadAndCacheDomains(chatId, ociCfgId) : storage.getCachedDomains(chatId);
            if (domains == null) {
                domains = Collections.emptyList();
            }

            if (CollectionUtil.isEmpty(domains)) {
                return buildDomainEditMessage(
                        callbackQuery,
                        "❌ 当前租户下未找到 Identity Domains",
                        buildBackToConfigMarkup(ociCfgId)
                );
            }

            return buildDomainEditMessage(
                    callbackQuery,
                    IdentityDomainMenuHelper.buildDomainListText(domains),
                    IdentityDomainMenuHelper.buildDomainListMarkup(ociCfgId, domains)
            );
        } catch (Exception e) {
            log.error("Failed to render identity domains for ociCfgId: {}", ociCfgId, e);
            return buildDomainEditMessage(
                    callbackQuery,
                    "❌ 获取 Identity Domains 失败：" + e.getMessage(),
                    buildBackToConfigMarkup(ociCfgId)
            );
        }
    }

    static List<IdentityDomainRsp> loadAndCacheDomains(long chatId, String ociCfgId) {
        ITenantService tenantService = SpringUtil.getBean(ITenantService.class);
        GetIdentityDomainsParams params = new GetIdentityDomainsParams();
        params.setOciCfgId(ociCfgId);

        IdentityDomainSelectionStorage storage = IdentityDomainSelectionStorage.getInstance();
        IdentityDomainRsp previousSelected = storage.getSelectedDomain(chatId);
        List<IdentityDomainRsp> domains = tenantService.listIdentityDomains(params);
        domains = domains == null ? Collections.emptyList() : domains;

        storage.setConfigContext(chatId, ociCfgId);
        storage.setDomainCache(chatId, domains);
        if (previousSelected == null || StringUtils.isBlank(previousSelected.getId())) {
            storage.setSelectedDomain(chatId, null);
        } else {
            storage.setSelectedDomain(
                    chatId,
                    domains.stream()
                            .filter(x -> x != null && StringUtils.equals(x.getId(), previousSelected.getId()))
                            .findFirst()
                            .orElse(null)
            );
        }
        return domains;
    }

    static String getOciCfgId(long chatId) {
        return IdentityDomainSelectionStorage.getInstance().getConfigContext(chatId);
    }

    static IdentityDomainRsp getDomain(long chatId, int index) {
        return IdentityDomainSelectionStorage.getInstance().getDomainByIndex(chatId, index);
    }

    static BotApiMethod<? extends Serializable> buildDomainEditMessage(CallbackQuery callbackQuery,
                                                                       String text,
                                                                       InlineKeyboardMarkup markup) {
        return MESSAGE_HELPER.buildEditMessage(callbackQuery, text, markup);
    }

    static BotApiMethod<? extends Serializable> buildDomainEditMessage(CallbackQuery callbackQuery, String text) {
        return MESSAGE_HELPER.buildEditMessage(callbackQuery, text);
    }

    static BotApiMethod<? extends Serializable> updateDomainState(CallbackQuery callbackQuery, boolean activate) {
        int domainIndex = Integer.parseInt(callbackQuery.getData().split(":", 2)[1]);
        long chatId = callbackQuery.getMessage().getChatId();
        String ociCfgId = getOciCfgId(chatId);
        IdentityDomainRsp domain = getDomain(chatId, domainIndex);
        if (StringUtils.isBlank(ociCfgId) || domain == null) {
            return buildDomainEditMessage(callbackQuery, "❌ 域不存在或上下文已失效，请重新打开域列表");
        }
        if (Boolean.TRUE.equals(domain.getDefaultDomain())) {
            return buildDomainEditMessage(
                    callbackQuery,
                    IdentityDomainMenuHelper.buildDomainDetailText(
                            domain,
                            activate ? "ℹ️ Default 域不支持激活操作" : "ℹ️ Default 域不支持停用操作"
                    ),
                    IdentityDomainMenuHelper.buildDomainDetailMarkup(ociCfgId, domainIndex, domain)
            );
        }

        try {
            ITenantService tenantService = SpringUtil.getBean(ITenantService.class);
            UpdateDomainStateParams params = new UpdateDomainStateParams();
            params.setOciCfgId(ociCfgId);
            params.setDomainId(domain.getId());
            if (activate) {
                tenantService.activateDomain(params);
            } else {
                tenantService.deactivateDomain(params);
            }

            IdentityDomainRsp refreshedDomain = loadAndCacheDomains(chatId, ociCfgId)
                    .stream()
                    .filter(x -> x != null && StringUtils.equals(x.getId(), domain.getId()))
                    .findFirst()
                    .orElse(domain);
            IdentityDomainSelectionStorage.getInstance().setSelectedDomain(chatId, refreshedDomain);
            int refreshedIndex = IdentityDomainSelectionStorage.getInstance().getDomainIndex(chatId, refreshedDomain.getId());
            return buildDomainEditMessage(
                    callbackQuery,
                    IdentityDomainMenuHelper.buildDomainDetailText(
                            refreshedDomain,
                            activate ? "✅ 域已激活" : "✅ 域已停用"
                    ),
                    IdentityDomainMenuHelper.buildDomainDetailMarkup(
                            ociCfgId,
                            refreshedIndex >= 0 ? refreshedIndex : domainIndex,
                            refreshedDomain
                    )
            );
        } catch (Exception e) {
            log.error("Failed to {} identity domain, ociCfgId={}, domainId={}",
                    activate ? "activate" : "deactivate", ociCfgId, domain.getId(), e);
            return buildDomainEditMessage(
                    callbackQuery,
                    IdentityDomainMenuHelper.buildDomainDetailText(
                            domain,
                            activate ? "❌ 激活域失败：" + e.getMessage() : "❌ 停用域失败：" + e.getMessage()
                    ),
                    IdentityDomainMenuHelper.buildDomainDetailMarkup(ociCfgId, domainIndex, domain)
            );
        }
    }

    private static InlineKeyboardMarkup buildBackToConfigMarkup(String ociCfgId) {
        return new InlineKeyboardMarkup(List.of(
                new InlineKeyboardRow(
                        KeyboardBuilder.button("◀️ 返回配置操作", "select_config:" + ociCfgId)
                ),
                KeyboardBuilder.buildCancelRow()
        ));
    }
}

@Slf4j
@Component
class RefreshIdentityDomainsHandler extends AbstractCallbackHandler {

    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        long chatId = callbackQuery.getMessage().getChatId();
        String ociCfgId = IdentityDomainManagementHandler.getOciCfgId(chatId);
        if (StringUtils.isBlank(ociCfgId)) {
            return buildEditMessage(callbackQuery, "❌ 域上下文已失效，请重新进入配置操作");
        }
        return IdentityDomainManagementHandler.renderDomainList(callbackQuery, chatId, ociCfgId, true);
    }

    @Override
    public String getCallbackPattern() {
        return "refresh_identity_domains";
    }
}

@Slf4j
@Component
class IdentityDomainSelectHandler extends AbstractCallbackHandler {

    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        int domainIndex = Integer.parseInt(callbackQuery.getData().split(":", 2)[1]);
        long chatId = callbackQuery.getMessage().getChatId();
        String ociCfgId = IdentityDomainManagementHandler.getOciCfgId(chatId);
        IdentityDomainRsp domain = IdentityDomainManagementHandler.getDomain(chatId, domainIndex);
        if (StringUtils.isBlank(ociCfgId) || domain == null) {
            return buildEditMessage(callbackQuery, "❌ 域不存在或上下文已失效，请重新打开域列表");
        }

        IdentityDomainSelectionStorage.getInstance().setSelectedDomain(chatId, domain);
        return buildEditMessage(
                callbackQuery,
                IdentityDomainMenuHelper.buildDomainDetailText(domain, null),
                IdentityDomainMenuHelper.buildDomainDetailMarkup(ociCfgId, domainIndex, domain)
        );
    }

    @Override
    public String getCallbackPattern() {
        return "identity_domain_select:";
    }
}

@Slf4j
@Component
class IdentityDomainActivateHandler extends AbstractCallbackHandler {

    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        return IdentityDomainManagementHandler.updateDomainState(callbackQuery, true);
    }

    @Override
    public String getCallbackPattern() {
        return "identity_domain_activate:";
    }

}

@Slf4j
@Component
class IdentityDomainDeactivateHandler extends AbstractCallbackHandler {

    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        return IdentityDomainManagementHandler.updateDomainState(callbackQuery, false);
    }

    @Override
    public String getCallbackPattern() {
        return "identity_domain_deactivate:";
    }
}

@Slf4j
@Component
class IdentityDomainUsersHandler extends AbstractCallbackHandler {

    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        int domainIndex = Integer.parseInt(callbackQuery.getData().split(":", 2)[1]);
        long chatId = callbackQuery.getMessage().getChatId();
        String ociCfgId = IdentityDomainManagementHandler.getOciCfgId(chatId);
        IdentityDomainRsp domain = IdentityDomainManagementHandler.getDomain(chatId, domainIndex);
        if (StringUtils.isBlank(ociCfgId) || domain == null) {
            return buildEditMessage(callbackQuery, "❌ 域不存在或上下文已失效，请重新打开域列表");
        }

        if (Boolean.TRUE.equals(domain.getDefaultDomain())) {
            IdentityDomainSelectionStorage.getInstance().setSelectedDomain(chatId, null);
            PaginationStorage.getInstance().resetPage(chatId, TenantUserMenuHelper.getPageType(ociCfgId));
            return TenantUserManagementHandler.renderUserList(callbackQuery, chatId, ociCfgId, true);
        }
        if (!StringUtils.equalsIgnoreCase(domain.getLifecycleState(), "ACTIVE")) {
            return buildEditMessage(
                    callbackQuery,
                    IdentityDomainMenuHelper.buildDomainDetailText(domain, "❌ 当前域未激活，无法进入用户管理"),
                    IdentityDomainMenuHelper.buildDomainDetailMarkup(ociCfgId, domainIndex, domain)
            );
        }

        IdentityDomainSelectionStorage.getInstance().setSelectedDomain(chatId, domain);
        PaginationStorage.getInstance().resetPage(chatId, TenantUserMenuHelper.getPageType(ociCfgId));
        return TenantUserManagementHandler.renderUserList(callbackQuery, chatId, ociCfgId, true);
    }

    @Override
    public String getCallbackPattern() {
        return "identity_domain_users:";
    }
}
