/*
 * Copyright 2024 - 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package st.orm.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.TransactionPropagation.MANDATORY;
import static st.orm.TransactionPropagation.NESTED;
import static st.orm.TransactionPropagation.NEVER;
import static st.orm.TransactionPropagation.REQUIRES_NEW;
import static st.orm.template.Transactions.setGlobalTransactionOptions;
import static st.orm.template.Transactions.transaction;
import static st.orm.template.Transactions.withTransactionOptions;

import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.PersistenceException;
import st.orm.TransactionOptions;
import st.orm.TransactionTimedOutException;
import st.orm.UnexpectedRollbackException;
import st.orm.template.model.Visit;

/**
 * Tests for the Java programmatic transaction API. The blocking orchestration and the JDBC transaction context
 * are shared with the Kotlin API (single implementation in storm-core), so these tests focus on the Java
 * facade: option resolution, checked-exception transparency, and the propagation semantics through the public
 * entry points. The exhaustive scenario matrices live in the storm-kotlin transaction suites, which drive the
 * same engine.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@Sql("/data.sql")
public class TransactionTest {

    @Autowired
    ORMTemplate orm;

    @AfterEach
    void resetDefaults() {
        // Restore baseline defaults: REQUIRED, isolation=null, timeout=null, readOnly=false.
        setGlobalTransactionOptions(TransactionOptions.defaults());
    }

    private long countVisits() {
        return orm.entity(Visit.class).count();
    }

    private void deleteAllVisits() {
        orm.entity(Visit.class).removeAll();
    }

    @Test
    public void modificationVisibleAfterCommit() {
        // data.sql inserts 14 visits. After a committed deleteAll, none should remain.
        transaction(tx -> {
            deleteAllVisits();
            return null;
        });
        assertEquals(0, countVisits());
    }

    @Test
    public void modificationRolledBackAfterSetRollbackOnly() {
        transaction(tx -> {
            deleteAllVisits();
            tx.setRollbackOnly();
            assertTrue(tx.isRollbackOnly());
            return null;
        });
        assertEquals(14, countVisits());
    }

    @Test
    public void modificationRolledBackAfterSetRollbackOnlyAtStart() {
        transaction(tx -> {
            tx.setRollbackOnly();
            deleteAllVisits();
            return null;
        });
        assertEquals(14, countVisits());
    }

    @Test
    public void runtimeExceptionRollsBackAndPropagates() {
        assertThrows(IllegalStateException.class, () ->
                transaction(tx -> {
                    deleteAllVisits();
                    throw new IllegalStateException("Simulated failure.");
                }));
        assertEquals(14, countVisits());
    }

    @Test
    public void checkedExceptionPropagatesUnchangedAndRollsBack() {
        // The call site declares the block's checked exception: no wrapping, no cause-unwrapping needed.
        assertThrows(IOException.class, () -> {
            transaction(tx -> {
                deleteAllVisits();
                throw new IOException("Simulated I/O failure.");
            });
        });
        assertEquals(14, countVisits());
    }

    @Test
    public void blockReturnsValue() {
        long count = transaction(tx -> countVisits());
        assertEquals(14, count);
    }

    @Test
    public void joinedRequiredInnerRollbackMarksOuter() {
        assertThrows(UnexpectedRollbackException.class, () ->
                transaction(outer -> {
                    transaction(inner -> {
                        deleteAllVisits();
                        inner.setRollbackOnly();
                        return null;
                    });
                    return null;
                }));
        assertEquals(14, countVisits());
    }

    @Test
    public void requiresNewCommitsIndependentlyOfOuterRollback() {
        transaction(outer -> {
            transaction(REQUIRES_NEW, inner -> {
                deleteAllVisits();
                return null;
            });
            outer.setRollbackOnly();
            return null;
        });
        // The inner transaction committed on its own connection; the outer rollback does not restore it.
        assertEquals(0, countVisits());
    }

    @Test
    public void nestedRollbackKeepsOuterAlive() {
        transaction(outer -> {
            long before = countVisits();
            assertEquals(14, before);
            transaction(NESTED, inner -> {
                deleteAllVisits();
                inner.setRollbackOnly();
                return null;
            });
            // The savepoint rollback undid the inner work; the outer transaction continues.
            assertEquals(14, countVisits());
            return null;
        });
        assertEquals(14, countVisits());
    }

    @Test
    public void nestedCommitBecomesVisibleToOuter() {
        transaction(outer -> {
            transaction(NESTED, inner -> {
                deleteAllVisits();
                return null;
            });
            assertEquals(0, countVisits());
            return null;
        });
        assertEquals(0, countVisits());
    }

    @Test
    public void mandatoryWithoutActiveTransactionFails() {
        assertThrows(PersistenceException.class, () ->
                transaction(MANDATORY, tx -> countVisits()));
    }

    @Test
    public void neverWithActiveTransactionFails() {
        assertThrows(PersistenceException.class, () ->
                transaction(outer -> {
                    countVisits(); // Materialize the outer transaction.
                    return transaction(NEVER, inner -> countVisits());
                }));
    }

    @Test
    public void expiredDeadlineOnCommitPathTimesOut() {
        assertThrows(TransactionTimedOutException.class, () ->
                transaction(TransactionOptions.defaults().withTimeoutSeconds(1), tx -> {
                    assertEquals(14, countVisits());
                    sleep(1500);
                    // On commit, the deadline is expired -> rollback path.
                    return null;
                }));
        assertEquals(14, countVisits());
    }

    @Test
    public void unmaterializedScopeStillEnforcesDeadline() {
        // The block never touches a template, so no physical transaction exists; the deadline check still
        // fails deterministically.
        assertThrows(TransactionTimedOutException.class, () ->
                transaction(TransactionOptions.defaults().withTimeoutSeconds(1), tx -> {
                    sleep(1500);
                    return null;
                }));
    }

    @Test
    public void globalOptionsApplyToNewTransactions() {
        setGlobalTransactionOptions(TransactionOptions.defaults().withTimeoutSeconds(1));
        assertThrows(TransactionTimedOutException.class, () ->
                transaction(tx -> {
                    assertEquals(14, countVisits());
                    sleep(1500);
                    return null;
                }));
        assertEquals(14, countVisits());
    }

    @Test
    public void scopedOptionsApplyAndRestore() {
        assertThrows(TransactionTimedOutException.class, () ->
                withTransactionOptions(TransactionOptions.defaults().withTimeoutSeconds(1), () ->
                        transaction(tx -> {
                            sleep(1500);
                            return null;
                        })));
        // Outside the scope, the previous defaults apply again: no timeout.
        transaction(tx -> {
            sleep(1200);
            return countVisits();
        });
    }

    @Test
    public void explicitOptionsOverrideScopedDefaults() {
        withTransactionOptions(TransactionOptions.defaults().withTimeoutSeconds(1), () ->
                transaction(TransactionOptions.defaults().withTimeoutSeconds(10), tx -> {
                    sleep(1200);
                    return countVisits();
                }));
    }

    @Test
    public void transactionWithoutTemplateUseIsANoOp() {
        var result = transaction(tx -> {
            assertFalse(tx.isRollbackOnly());
            return "done";
        });
        assertEquals("done", result);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
