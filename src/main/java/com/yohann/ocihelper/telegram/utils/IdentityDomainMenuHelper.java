package com.yohann.ocihelper.telegram.utils;

import com.yohann.ocihelper.bean.response.oci.tenant.IdentityDomainRsp;
import com.yohann.ocihelper.telegram.builder.KeyboardBuilder;
import org.apache.commons.lang3.StringUtils;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.ArrayList;
import java.util.List;

/**
 * TG identity domain menu helper.
 */
public final class IdentityDomainMenuHelper {

    private IdentityDomainMenuHelper() {
    }

    public static String buildDomainListText(List<IdentityDomainRsp> domains) {
        StringBuilder message = new StringBuilder();
        message.append("【Identity Domains】\n\n");
        message.append(String.format("共 %d 个域\n\n", domains.size()));

        if (domains.isEmpty()) {
            message.append("当前租户下没有可管理的域。");
            return message.toString();
        }

        for (int i = 0; i < domains.size(); i++) {
            IdentityDomainRsp domain = domains.get(i);
            message.append(String.format(
                    "%d. %s\n" +
                            "   类型: %s | 状态: %s\n" +
                            "   Home Region: %s\n\n",
                    i + 1,
                    TenantUserMenuHelper.escapeMarkdown(defaultText(domain.getDisplayName(), "未命名域")),
                    TenantUserMenuHelper.escapeMarkdown(defaultText(domain.getType(), "未知")),
                    TenantUserMenuHelper.escapeMarkdown(defaultText(domain.getLifecycleState(), "未知")),
                    TenantUserMenuHelper.escapeMarkdown(defaultText(domain.getHomeRegion(), "未知"))
            ));
        }
        return message.toString();
    }

    public static InlineKeyboardMarkup buildDomainListMarkup(String ociCfgId, List<IdentityDomainRsp> domains) {
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        for (int i = 0; i < domains.size(); i += 2) {
            InlineKeyboardRow row = new InlineKeyboardRow();
            row.add(KeyboardBuilder.button(buildDomainButtonText(domains.get(i), i), "identity_domain_select:" + i));
            if (i + 1 < domains.size()) {
                row.add(KeyboardBuilder.button(buildDomainButtonText(domains.get(i + 1), i + 1), "identity_domain_select:" + (i + 1)));
            }
            keyboard.add(row);
        }
        keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("🔄 刷新域列表", "refresh_identity_domains")
        ));
        keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("◀️ 返回配置操作", "select_config:" + ociCfgId)
        ));
        keyboard.add(KeyboardBuilder.buildCancelRow());
        return new InlineKeyboardMarkup(keyboard);
    }

    public static String buildDomainDetailText(IdentityDomainRsp domain, String notice) {
        StringBuilder message = new StringBuilder();
        if (StringUtils.isNotBlank(notice)) {
            message.append(TenantUserMenuHelper.escapeMarkdown(notice)).append("\n\n");
        }
        message.append("【域详情】\n\n");
        message.append(String.format("名称: %s\n", TenantUserMenuHelper.escapeMarkdown(defaultText(domain.getDisplayName(), "未命名域"))));
        message.append(String.format("类型: %s\n", TenantUserMenuHelper.escapeMarkdown(defaultText(domain.getType(), "未知"))));
        message.append(String.format("状态: %s\n", TenantUserMenuHelper.escapeMarkdown(defaultText(domain.getLifecycleState(), "未知"))));
        message.append(String.format("Home Region: %s\n", TenantUserMenuHelper.escapeMarkdown(defaultText(domain.getHomeRegion(), "未知"))));
        message.append(String.format("登录隐藏: %s\n", Boolean.TRUE.equals(domain.getHiddenOnLogin()) ? "是" : "否"));
        message.append(String.format("Endpoint: %s\n", TenantUserMenuHelper.escapeMarkdown(defaultText(domain.getUrl(), "未设置"))));
        message.append(String.format("描述: %s\n\n", TenantUserMenuHelper.escapeMarkdown(defaultText(domain.getDescription(), "无"))));

        if (Boolean.TRUE.equals(domain.getDefaultDomain())) {
            message.append("可执行操作：进入默认域用户管理。Default 域不提供激活、停用操作。");
        } else if (isActive(domain)) {
            message.append("可执行操作：进入用户管理、停用域。");
        } else {
            message.append("可执行操作：激活域。域处于停用状态时不进入用户管理。");
        }
        return message.toString();
    }

    public static InlineKeyboardMarkup buildDomainDetailMarkup(String ociCfgId, int domainIndex, IdentityDomainRsp domain) {
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        if (Boolean.TRUE.equals(domain.getDefaultDomain())) {
            keyboard.add(new InlineKeyboardRow(
                    KeyboardBuilder.button("👥 默认域用户管理", "tenant_user_management_default:" + ociCfgId)
            ));
        } else if (isActive(domain)) {
            keyboard.add(new InlineKeyboardRow(
                    KeyboardBuilder.button("👥 用户管理", "identity_domain_users:" + domainIndex),
                    KeyboardBuilder.button("⏸ 停用域", "identity_domain_deactivate:" + domainIndex)
            ));
        } else {
            keyboard.add(new InlineKeyboardRow(
                    KeyboardBuilder.button("▶️ 激活域", "identity_domain_activate:" + domainIndex)
            ));
        }
        keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("◀️ 返回域列表", "identity_domain_management:" + ociCfgId)
        ));
        keyboard.add(KeyboardBuilder.buildCancelRow());
        return new InlineKeyboardMarkup(keyboard);
    }

    private static boolean isActive(IdentityDomainRsp domain) {
        return StringUtils.equalsIgnoreCase(domain.getLifecycleState(), "ACTIVE");
    }

    private static String buildDomainButtonText(IdentityDomainRsp domain, int index) {
        String label = defaultText(domain.getDisplayName(), "域");
        if (label.length() > 8) {
            label = label.substring(0, 8) + "...";
        }
        return String.format("%d %s", index + 1, label);
    }

    private static String defaultText(String value, String defaultValue) {
        return StringUtils.isBlank(value) ? defaultValue : value;
    }
}
