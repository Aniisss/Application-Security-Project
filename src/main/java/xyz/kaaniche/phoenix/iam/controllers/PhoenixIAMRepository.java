package xyz.kaaniche.phoenix.iam.controllers;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import xyz.kaaniche.phoenix.iam.entities.Grant;
import xyz.kaaniche.phoenix.iam.entities.Identity;
import xyz.kaaniche.phoenix.iam.entities.Tenant;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Singleton
public class PhoenixIAMRepository {
    @Inject
    private EntityManager entityManager;

    public Tenant findTenantByName(String name){
        try {
            return entityManager.createQuery("select t from Tenant t where name =:name",Tenant.class)
                    .setParameter("name",name)
                    .getSingleResult();
        } catch (jakarta.persistence.NoResultException e) {
            return null;
        }
    }

    public Identity findIdentityByUsername(String username){
        try {
            return entityManager.createQuery("select i from Identity i where username=:username",Identity.class)
                    .setParameter("username",username)
                    .getSingleResult();
        } catch (jakarta.persistence.NoResultException e) {
            return null;
        }
    }

    public Optional<Grant> findGrant(String tenantName,Long identityId){
        Tenant tenant = findTenantByName(tenantName);
        if(tenant==null){
            throw new IllegalArgumentException("Invalid Client Id!");
        }
        try {
            Grant grant = entityManager.createQuery("select g from Grant g where g.id.tenantId =:tenantId and g.id.identityId = :identityId",Grant.class)
                    .setParameter("tenantId",tenant.getId())
                    .setParameter("identityId",identityId)
                    .getSingleResult();
            return Optional.of(grant);
        } catch (jakarta.persistence.NoResultException e) {
            return Optional.empty();
        }
    }
    public String[] getRoles(String username){
        TypedQuery<Long> query = entityManager.createQuery("select i.roles from Identity i where username=:username",Long.class);
        query.setParameter("username",username);
        Long roles = query.getSingleResult();
        Set<String> ret = new HashSet<>();
        for(Role role:Role.values()){
            if((roles&role.getValue())!=0L){
                String value = Role.byValue(role.getValue());
                if (value==null){
                    continue;
                }
                ret.add(value);
            }
        }
        return ret.toArray(new String[0]);
    }
}
