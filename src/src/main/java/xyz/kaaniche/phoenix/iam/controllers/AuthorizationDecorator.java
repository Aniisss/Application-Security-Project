package xyz.kaaniche.phoenix.iam.controllers;

import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptor;
import jakarta.ws.rs.NotAuthorizedException;
import xyz.kaaniche.phoenix.core.controllers.GenericDAO;
import xyz.kaaniche.phoenix.core.entities.RootEntity;
import xyz.kaaniche.phoenix.iam.security.IdentityUtility;

import java.util.EnumMap;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Decorator
@Priority(Interceptor.Priority.APPLICATION)
public abstract class AuthorizationDecorator<E extends RootEntity<ID>,ID extends java.io.Serializable> implements GenericDAO<E,ID> {
    @Any @Inject @Delegate
    private GenericDAO<E,ID> delegate;
    private final EnumMap<Role,Set<Permission>> roleToPermissions = new EnumMap<>(Role.class);



    @Override
    public <S extends E> S save(S entity){
        authorize(SecureAction.SAVE,entity);
        return delegate.save(entity);
    }

    @Override
    public E edit(ID id, Consumer<E> updateFewAttributes){
        authorize(SecureAction.EDIT,delegate.findById(id).orElseThrow());
        return delegate.edit(id,updateFewAttributes);
    }
    @Override
    public void delete(E entity){
        authorize(SecureAction.DELETE,entity);
        delegate.delete(entity);
    }

    private void authorize(SecureAction action,E entity){
        Set<Role> roles = IdentityUtility.getRoles().stream().map(Role::byId).collect(Collectors.toUnmodifiableSet());
        Set<Permission> permissions = roles.stream().map(roleToPermissions::get).flatMap(Set::stream).collect(Collectors.toUnmodifiableSet());
        ID id = entity.getId();
        Class<E> type = delegate.getEntityClass();
        if(!permissions.contains(new Permission(action,type,id))){
            throw new NotAuthorizedException(action);
        }
    }

    private final class Permission extends java.security.Permission {
        private Class<E> type;
        private ID id;

        /**
         * Constructs a permission with the specified name.
         *
         * @param name name of the Permission object being created.
         */
        public Permission(String name) {
            super(name);
        }

        public Permission(SecureAction action,Class<E> type,ID id){
            super(action.name());
            this.type = type;
            this.id = id;
        }

        @Override
        public boolean implies(java.security.Permission permission) {
            if(permission instanceof Permission that){
                return this.getName().equals(that.getName()) && this.type.equals(that.type) && this.id.equals(that.id);
            }
            return false;
        }

        @Override
        public boolean equals(Object obj) {
            if(obj==null){
                return false;
            }
            if(obj instanceof java.security.Permission p){
                return this.implies(p) && p.implies(this);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return 31*Objects.hash(type, id)+getName().hashCode();
        }

        @Override
        public String getActions() {
            return null;
        }
    }

    private enum SecureAction{
        SAVE,EDIT,DELETE
    }
}
