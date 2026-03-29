package com.yohann.ocihelper.telegram.storage;

import com.yohann.ocihelper.bean.response.oci.tenant.IdentityDomainRsp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Telegram Bot identity domain cache storage.
 */
public class IdentityDomainSelectionStorage {

    private static final IdentityDomainSelectionStorage INSTANCE = new IdentityDomainSelectionStorage();

    private final Map<Long, String> configContext = new ConcurrentHashMap<>();
    private final Map<Long, List<IdentityDomainRsp>> domainCache = new ConcurrentHashMap<>();
    private final Map<Long, IdentityDomainRsp> selectedDomain = new ConcurrentHashMap<>();

    private IdentityDomainSelectionStorage() {
    }

    public static IdentityDomainSelectionStorage getInstance() {
        return INSTANCE;
    }

    public void setConfigContext(long chatId, String ociCfgId) {
        configContext.put(chatId, ociCfgId);
    }

    public String getConfigContext(long chatId) {
        return configContext.get(chatId);
    }

    public void setDomainCache(long chatId, List<IdentityDomainRsp> domains) {
        domainCache.put(chatId, new ArrayList<>(domains));
    }

    public List<IdentityDomainRsp> getCachedDomains(long chatId) {
        return domainCache.getOrDefault(chatId, new ArrayList<>());
    }

    public IdentityDomainRsp getDomainByIndex(long chatId, int index) {
        List<IdentityDomainRsp> domains = domainCache.get(chatId);
        if (domains != null && index >= 0 && index < domains.size()) {
            return domains.get(index);
        }
        return null;
    }

    public int getDomainIndex(long chatId, String domainId) {
        List<IdentityDomainRsp> domains = domainCache.get(chatId);
        if (domains == null || domainId == null) {
            return -1;
        }
        for (int i = 0; i < domains.size(); i++) {
            IdentityDomainRsp domain = domains.get(i);
            if (domain != null && domainId.equals(domain.getId())) {
                return i;
            }
        }
        return -1;
    }

    public void setSelectedDomain(long chatId, IdentityDomainRsp domain) {
        if (domain == null) {
            selectedDomain.remove(chatId);
            return;
        }
        selectedDomain.put(chatId, domain);
    }

    public IdentityDomainRsp getSelectedDomain(long chatId) {
        return selectedDomain.get(chatId);
    }

    public void clearAll(long chatId) {
        configContext.remove(chatId);
        domainCache.remove(chatId);
        selectedDomain.remove(chatId);
    }
}
