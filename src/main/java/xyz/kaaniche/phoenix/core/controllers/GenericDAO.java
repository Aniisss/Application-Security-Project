package xyz.kaaniche.phoenix.core.controllers;

import xyz.kaaniche.phoenix.core.entities.RootEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.ejb.Stateless;
import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Generic Data Access Object for CRUD operations.
 * @param <ID> Primary key type
 * @param <E> Entity type extending RootEntity
 */
@Stateless
public class GenericDAO<ID extends Serializable, E extends RootEntity<ID>> implements IGenericDAO<ID, E> {
    
    @PersistenceContext
    protected EntityManager entityManager;

    private final Class<E> entityClass;

    @SuppressWarnings("unchecked")
    public GenericDAO() {
        this.entityClass = (Class<E>) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0];
    }

    public <S extends E> S save(S entity) {
        entityManager.persist(entity);
        return entity;
    }

    public Optional<E> findById(ID id) {
        return Optional.ofNullable(entityManager.find(entityClass, id));
    }

    public E edit(ID id, Consumer<E> updateFewAttributes) {
        E entity = entityManager.find(entityClass, id);
        if (entity != null) {
            updateFewAttributes.accept(entity);
            entity = entityManager.merge(entity);
        }
        return entity;
    }

    public void delete(E entity) {
        E managed = entityManager.merge(entity);
        entityManager.remove(managed);
    }

    public Class<E> getEntityClass() {
        return entityClass;
    }

    public E find(ID id) {
        return entityManager.find(entityClass, id);
    }

    public List<E> findAll() {
        return entityManager.createQuery("SELECT e FROM " + entityClass.getSimpleName() + " e", entityClass)
                .getResultList();
    }

    public E update(E entity) {
        return entityManager.merge(entity);
    }
}
