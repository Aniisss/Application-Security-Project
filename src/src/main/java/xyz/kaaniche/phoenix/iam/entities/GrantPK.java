package xyz.kaaniche.phoenix.iam.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import xyz.kaaniche.phoenix.core.entities.CompoundPK;

@Embeddable
public class GrantPK extends CompoundPK {

    @Column(name = "tenant_id",nullable = false)
    private Short tenantId;
    @Column(name = "identity_id",nullable = false)
    private Long identityId;

    public GrantPK(){
        super(GrantPK.class);
    }

    public Short getTenantId() {
        return tenantId;
    }

    public void setTenantId(Short tenantId) {
        this.tenantId = tenantId;
    }

    public Long getIdentityId() {
        return identityId;
    }

    public void setIdentityId(Long identityId) {
        this.identityId = identityId;
    }
}
