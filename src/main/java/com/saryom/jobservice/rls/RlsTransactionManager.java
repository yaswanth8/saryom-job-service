package com.saryom.jobservice.rls;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.function.Supplier;

/**
 * Tells the database who each transaction is for.
 *
 * <p>The tables are protected by row level security policies that compare a
 * row's owner with {@code current_setting('app.user_id')}. Something has to put
 * the caller's identity there, and it has to be impossible to forget: a query
 * that ran without it would see nothing, and a write would be refused.
 *
 * <p>Doing it in the transaction manager covers every transaction there is —
 * service methods, and the implicit one a repository opens when called outside
 * a service — rather than relying on each method to remember.
 *
 * <p>{@code set_config(…, true)} is transaction-local. Connections are pooled,
 * so a session-level setting would follow the connection to the next request
 * and hand one user's identity to another.
 */
public class RlsTransactionManager extends JpaTransactionManager {

    static final String SET_IDENTITY =
            "select set_config('app.user_id', ?1, true), set_config('app.is_admin', ?2, true), "
                    + "set_config('app.token_hash', ?3, true)";

    private final transient Supplier<RequestIdentity> identity;
    private final boolean enabled;

    public RlsTransactionManager(EntityManagerFactory entityManagerFactory,
                                 Supplier<RequestIdentity> identity, boolean enabled) {
        super(entityManagerFactory);
        this.identity = identity;
        this.enabled = enabled;
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        if (!enabled) {
            return;
        }
        try {
            applyIdentity(currentEntityManager(), identity.get());
        } catch (RuntimeException e) {
            // The transaction is open but anonymous to the database. Close it
            // rather than let the caller proceed and be puzzled by empty results.
            try {
                currentEntityManager().getTransaction().rollback();
            } finally {
                doCleanupAfterCompletion(transaction);
            }
            throw e;
        }
    }

    private EntityManager currentEntityManager() {
        var holder = (EntityManagerHolder) TransactionSynchronizationManager
                .getResource(obtainEntityManagerFactory());
        if (holder == null) {
            throw new IllegalStateException("No EntityManager bound to the transaction just begun");
        }
        return holder.getEntityManager();
    }

    static void applyIdentity(EntityManager entityManager, RequestIdentity who) {
        entityManager.createNativeQuery(SET_IDENTITY)
                .setParameter(1, who.uid() == null ? "" : who.uid())
                .setParameter(2, who.admin() ? "true" : "false")
                .setParameter(3, who.tokenHash() == null ? "" : who.tokenHash())
                .getSingleResult();
    }
}
