package xyz.kaaniche.phoenix.core.entities;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.io.Serializable;

/**
 * Base class for entities with a simple primary key.
 */
@MappedSuperclass
public abstract class SimplePKEntity<ID extends Serializable> extends RootEntity<ID> {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected ID id;

    @Override
    public ID getId() {
        return id;
    }

    @Override
    public void setId(ID id) {
        this.id = id;
    }
}
