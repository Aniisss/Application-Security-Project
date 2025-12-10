package xyz.kaaniche.phoenix.core.entities;

import jakarta.persistence.Embeddable;
import java.io.Serializable;

/**
 * Base class for compound primary keys.
 */
@Embeddable
public abstract class CompoundPK implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public CompoundPK() {
        // No-arg constructor for JPA
    }
    
    @SuppressWarnings("unused")
    public CompoundPK(Class<?> clazz) {
        // For backward compatibility with subclasses that call super(GrantPK.class)
    }
}
