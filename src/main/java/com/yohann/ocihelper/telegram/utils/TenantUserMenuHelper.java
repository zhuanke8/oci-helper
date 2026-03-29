package com.yohann.ocihelper.telegram.utils;

import com.yohann.ocihelper.bean.response.oci.tenant.TenantInfoRsp;
import com.yohann.ocihelper.telegram.builder.KeyboardBuilder;
import com.yohann.ocihelper.telegram.storage.PaginationStorage;
import org.apache.commons.lang3.StringUtils;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.ArrayList;
import java.util.List;

/**
 * TG tenant user menu helper.
 */
public final class TenantUserMenuHelper {

    public static final int PAGE_SIZE = 6;
    private static final String PAGE_TYPE_PREFIX = "tenant_user_list:";

    private TenantUserMenuHelper() {
    }

    public static String getPageType(String ociCfgId) {
        return PAGE_TYPE_PREFIX + ociCfgId;
    }

    public static String buildUserListText(List<TenantInfoRsp.TenantUserInfo> users, long chatId, String ociCfgId) {
        PaginationStorage paginationStorage = PaginationStorage.getInstance();
        String pageType = getPageType(ociCfgId);
        int totalPages = Math.max(PaginationStorage.calculateTotalPages(users.size(), PAGE_SIZE), 1);
        int currentPage = Math.min(paginationStorage.getCurrentPage(chatId, pageType), totalPages - 1);
        int startIndex = PaginationStorage.getStartIndex(currentPage, PAGE_SIZE);
        int endIndex = PaginationStorage.getEndIndex(currentPage, PAGE_SIZE, users.size());

        StringBuilder message = new StringBuilder();
        message.append("【租户用户管理】\n\n");
        message.append(String.format("共 %d 个用户，当前第 %d/%d 页\n\n", users.size(), currentPage + 1, totalPages));

        if (users.isEmpty()) {
            message.append("当前租户下没有可管理的用户。");
            return message.toString();
        }

        for (int i = startIndex; i < endIndex; i++) {
            TenantInfoRsp.TenantUserInfo user = users.get(i);
            message.append(String.format(
                    "%d. %s\n" +
                            "   邮箱: %s\n" +
                            "   状态: %s | MFA: %s\n\n",
                    i + 1,
                    escapeMarkdown(defaultText(user.getName(), "未知用户")),
                    escapeMarkdown(defaultText(user.getEmail(), "未设置")),
                    escapeMarkdown(defaultText(user.getLifecycleState(), "未知")),
                    Boolean.TRUE.equals(user.getIsMfaActivated()) ? "已启用" : "未启用"
            ));
        }
        return message.toString();
    }

    public static InlineKeyboardMarkup buildUserListMarkup(List<TenantInfoRsp.TenantUserInfo> users, long chatId, String ociCfgId) {
        PaginationStorage paginationStorage = PaginationStorage.getInstance();
        String pageType = getPageType(ociCfgId);
        int totalPages = Math.max(PaginationStorage.calculateTotalPages(users.size(), PAGE_SIZE), 1);
        int currentPage = Math.min(paginationStorage.getCurrentPage(chatId, pageType), totalPages - 1);
        int startIndex = PaginationStorage.getStartIndex(currentPage, PAGE_SIZE);
        int endIndex = PaginationStorage.getEndIndex(currentPage, PAGE_SIZE, users.size());

        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        for (int i = startIndex; i < endIndex; i += 2) {
            InlineKeyboardRow row = new InlineKeyboardRow();
            row.add(KeyboardBuilder.button(buildUserButtonText(users.get(i), i), "tenant_user_select:" + i));
            if (i + 1 < endIndex) {
                row.add(KeyboardBuilder.button(buildUserButtonText(users.get(i + 1), i + 1), "tenant_user_select:" + (i + 1)));
            }
            keyboard.add(row);
        }

        if (totalPages > 1) {
            keyboard.add(KeyboardBuilder.buildPaginationRow(
                    currentPage,
                    totalPages,
                    "tenant_user_page_prev",
                    "tenant_user_page_next"
            ));
        }

        keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("🔄 刷新用户列表", "refresh_tenant_users")
        ));
        keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("◀️ 返回配置操作", "select_config:" + ociCfgId)
        ));
        keyboard.add(KeyboardBuilder.buildCancelRow());
        return new InlineKeyboardMarkup(keyboard);
    }

    public static String buildUserDetailText(TenantInfoRsp.TenantUserInfo user, int userIndex, String notice) {
        StringBuilder message = new StringBuilder();
        if (StringUtils.isNotBlank(notice)) {
            message.append(escapeMarkdown(notice)).append("\n\n");
        }
        message.append("【租户用户详情】\n\n");
        message.append(String.format("序号: %d\n", userIndex + 1));
        message.append(String.format("名称: %s\n", escapeMarkdown(defaultText(user.getName(), "未知用户"))));
        message.append(String.format("主邮箱: %s\n", escapeMarkdown(defaultText(user.getEmail(), "未设置"))));
        message.append(String.format("邮箱验证: %s\n", Boolean.TRUE.equals(user.getEmailVerified()) ? "已验证" : "未验证"));
        message.append(String.format("MFA: %s\n", Boolean.TRUE.equals(user.getIsMfaActivated()) ? "已启用" : "未启用"));
        message.append(String.format("状态: %s\n", escapeMarkdown(defaultText(user.getLifecycleState(), "未知"))));
        message.append(String.format("描述: %s\n", escapeMarkdown(defaultText(user.getDescription(), "无"))));
        message.append(String.format("创建时间: %s\n", escapeMarkdown(defaultText(user.getTimeCreated(), "未知"))));
        message.append(String.format("最后成功登录: %s\n\n", escapeMarkdown(defaultText(user.getLastSuccessfulLoginTime(), "未知"))));
        message.append("可执行操作：权限诊断、清除 MFA、随机重置密码、指定密码、设置 recovery email、清空 recovery email。");
        return message.toString();
    }

    public static InlineKeyboardMarkup buildUserDetailMarkup(String ociCfgId, int userIndex) {
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("🩺 权限诊断", "tenant_user_diagnose:" + userIndex),
                KeyboardBuilder.button("🧹 清除 MFA", "tenant_user_clear_mfa:" + userIndex)
        ));
        keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("🎲 随机重置密码", "tenant_user_reset_password:" + userIndex),
                KeyboardBuilder.button("🔐 指定密码", "tenant_user_prompt_change_password:" + userIndex)
        ));
        keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("📧 设置恢复邮箱", "tenant_user_prompt_recovery_email:" + userIndex),
                KeyboardBuilder.button("🗑 清空恢复邮箱", "tenant_user_clear_recovery_email:" + userIndex)
        ));
        keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("◀️ 返回用户列表", "tenant_user_management:" + ociCfgId)
        ));
        keyboard.add(KeyboardBuilder.buildCancelRow());
        return new InlineKeyboardMarkup(keyboard);
    }

    public static InlineKeyboardMarkup buildInputMarkup(int userIndex) {
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("◀️ 返回用户详情", "tenant_user_cancel_input:" + userIndex)
        ));
        keyboard.add(KeyboardBuilder.buildCancelRow());
        return new InlineKeyboardMarkup(keyboard);
    }

    public static InlineKeyboardMarkup buildBackToListMarkup(String ociCfgId) {
        return new InlineKeyboardMarkup(List.of(
                new InlineKeyboardRow(
                        KeyboardBuilder.button("◀️ 返回用户列表", "tenant_user_management:" + ociCfgId)
                ),
                KeyboardBuilder.buildCancelRow()
        ));
    }

    public static InlineKeyboardMarkup buildBackToConfigMarkup(String ociCfgId) {
        return new InlineKeyboardMarkup(List.of(
                new InlineKeyboardRow(
                        KeyboardBuilder.button("◀️ 返回配置操作", "select_config:" + ociCfgId)
                ),
                KeyboardBuilder.buildCancelRow()
        ));
    }

    public static InlineKeyboardMarkup buildDiagnosisMarkup(String ociCfgId, int userIndex) {
        return new InlineKeyboardMarkup(List.of(
                new InlineKeyboardRow(
                        KeyboardBuilder.button("◀️ 返回用户详情", "tenant_user_select:" + userIndex)
                ),
                new InlineKeyboardRow(
                        KeyboardBuilder.button("◀️ 返回用户列表", "tenant_user_management:" + ociCfgId)
                ),
                KeyboardBuilder.buildCancelRow()
        ));
    }

    private static String buildUserButtonText(TenantInfoRsp.TenantUserInfo user, int index) {
        String label = defaultText(user.getName(), defaultText(user.getEmail(), "用户"));
        if (label.length() > 8) {
            label = label.substring(0, 8) + "...";
        }
        return String.format("%d %s", index + 1, label);
    }

    private static String defaultText(String value, String defaultValue) {
        return StringUtils.isBlank(value) ? defaultValue : value;
    }

    public static String escapeMarkdown(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("_", "\\_")
                .replace("*", "\\*")
                .replace("[", "\\[")
                .replace("`", "\\`");
    }
}
