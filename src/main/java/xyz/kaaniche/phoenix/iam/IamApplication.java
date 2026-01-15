package xyz.kaaniche.phoenix.iam;
/**
 * IamApplication
 *
 * This class defines the entry point of the IAM REST application.
 * It configures the base REST path and exposes shared components
 * using CDI producers.
 *
 * Security note:
 * - Configuration values are injected from environment variables
 *   to avoid hardcoding sensitive information.
 * - Centralized configuration improves maintainability and reduces
 *   misconfiguration risks.
 */

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.logging.Logger;



@ApplicationPath("/rest-iam")
public class IamApplication extends Application {
    @ApplicationScoped
    public static final class CDIConfigurator {
        @PersistenceContext(unitName = "default")
        private EntityManager entityManager;

        @Produces
        public EntityManager getEntityManager() {
            return entityManager;
        }

        @Inject
        @ConfigProperty(name = "jwt.realm")
        private String realm;

        @Produces
        @Named(value = "realm")
        public String getRealm(){
            return realm;
        }

        @Produces
        @Dependent
        public Logger getLogger(InjectionPoint injectionPoint){
            return Logger.getLogger(injectionPoint.getBean().getBeanClass().getName());
        }

        public void disposeLogger(@Disposes Logger logger){
            logger.info("logger disposed!");
        }
    }
}
