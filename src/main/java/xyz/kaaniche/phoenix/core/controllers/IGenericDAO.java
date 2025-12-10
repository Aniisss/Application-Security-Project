package xyz.kaaniche.phoenix.core.controllers;

import xyz.kaaniche.phoenix.core.entities.RootEntity;
import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Generic Data Access Object interface for CRUD operations.
 * @param <ID> Primary key type
 * @param <E> Entity type extending RootEntity
 */
public interface IGenericDAO<ID extends Serializable, E extends RootEntity<ID>> {
    
    <S extends E> S save(S entity);
    
    Optional<E> findById(ID id);
    
    E edit(ID id, Consumer<E> updateFewAttributes);
    
    void delete(E entity);
    
    Class<E> getEntityClass();
    
    E find(ID id);
    
    List<E> findAll();
    
    E update(E entity);
}
