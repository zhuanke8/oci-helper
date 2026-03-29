package com.yohann.ocihelper.bean.response.oci.tenant;

import lombok.Data;

/**
 * Identity Domain response.
 */
@Data
public class IdentityDomainRsp {

    private String id;
    private String displayName;
    private String description;
    private String url;
    private String homeRegionUrl;
    private String homeRegion;
    private String lifecycleState;
    private String type;
    private Boolean hiddenOnLogin;
    private Boolean defaultDomain;
}
