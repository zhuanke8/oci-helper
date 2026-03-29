package com.yohann.ocihelper.telegram.storage;

import com.yohann.ocihelper.bean.response.oci.tenant.TenantInfoRsp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Telegram Bot tenant user cache storage.
 */
public class TenantUserSelectionStorage {

    private static final TenantUserSelectionStorage INSTANCE = new TenantUserSelectionStorage();

    private final Map<Long, String> configContext = new ConcurrentHashMap<>();
    private final Map<Long, List<TenantInfoRsp.TenantUserInfo>> userCache = new ConcurrentHashMap<>();

    private TenantUserSelectionStorage() {
    }

    public static TenantUserSelectionStorage getInstance() {
        return INSTANCE;
    }

    public void setConfigContext(long chatId, String ociCfgId) {
        configContext.put(chatId, ociCfgId);
    }

    public String getConfigContext(long chatId) {
        return configContext.get(chatId);
    }

    public void setUserCache(long chatId, List<TenantInfoRsp.TenantUserInfo> users) {
        userCache.put(chatId, new ArrayList<>(users));
    }

    public List<TenantInfoRsp.TenantUserInfo> getCachedUsers(long chatId) {
        return userCache.getOrDefault(chatId, new ArrayList<>());
    }

    public TenantInfoRsp.TenantUserInfo getUserByIndex(long chatId, int index) {
        List<TenantInfoRsp.TenantUserInfo> users = userCache.get(chatId);
        if (users != null && index >= 0 && index < users.size()) {
            return users.get(index);
        }
        return null;
    }

    public void clearAll(long chatId) {
        configContext.remove(chatId);
        userCache.remove(chatId);
    }
}
