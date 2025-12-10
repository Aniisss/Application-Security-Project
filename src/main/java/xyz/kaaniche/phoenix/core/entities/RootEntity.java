package xyz.kaaniche.phoenix.core.entities;

import jakarta.persistence.MappedSuperclass;
import java.io.Serializable;

/**
 * Base class for all entities.
 */
@MappedSuperclass
public abstract class RootEntity<ID extends Serializable> {
    public abstract ID getId();
    public abstract void setId(ID id);
}
