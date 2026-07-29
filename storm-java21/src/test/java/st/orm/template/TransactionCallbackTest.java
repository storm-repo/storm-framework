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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.TransactionPropagation.REQUIRES_NEW;
import static st.orm.template.Transactions.transaction;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.TransactionCallbackException;
import st.orm.template.model.Visit;

/**
 * Tests for transaction lifecycle callbacks through the Java API: firing conditions, registration order,
 * exception handling, and deferral to the outermost physical transaction.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@Sql("/data.sql")
public class TransactionCallbackTest {

    @Autowired
    ORMTemplate orm;

    private long countVisits() {
        return orm.entity(Visit.class).count();
    }

    @Test
    public void onCommitFiresAfterSuccessfulCompletion() {
        List<String> events = new ArrayList<>();
        transaction(tx -> {
            tx.onCommit(() -> events.add("commit"));
            tx.onRollback(() -> events.add("rollback"));
            return countVisits();
        });
        assertEquals(List.of("commit"), events);
    }

    @Test
    public void onRollbackFiresOnException() {
        List<String> events = new ArrayList<>();
        assertThrows(IllegalStateException.class, () ->
                transaction(tx -> {
                    tx.onCommit(() -> events.add("commit"));
                    tx.onRollback(() -> events.add("rollback"));
                    countVisits();
                    throw new IllegalStateException("Simulated failure.");
                }));
        assertEquals(List.of("rollback"), events);
    }

    @Test
    public void onRollbackFiresOnRollbackOnly() {
        List<String> events = new ArrayList<>();
        transaction(tx -> {
            tx.onRollback(() -> events.add("rollback"));
            tx.setRollbackOnly();
            return countVisits();
        });
        assertEquals(List.of("rollback"), events);
    }

    @Test
    public void callbacksExecuteInRegistrationOrder() {
        List<String> events = new ArrayList<>();
        transaction(tx -> {
            tx.onCommit(() -> events.add("first"));
            tx.onCommit(() -> events.add("second"));
            tx.onCommit(() -> events.add("third"));
            return countVisits();
        });
        assertEquals(List.of("first", "second", "third"), events);
    }

    @Test
    public void throwingCallbackDoesNotPreventRemainingCallbacks() {
        List<String> events = new ArrayList<>();
        var thrown = assertThrows(TransactionCallbackException.class, () ->
                transaction(tx -> {
                    tx.onCommit(() -> {
                        throw new IllegalStateException("First callback failed.");
                    });
                    tx.onCommit(() -> events.add("second"));
                    return countVisits();
                }));
        assertTrue(thrown.isCommitted());
        assertEquals("First callback failed.", thrown.getCause().getMessage());
        assertEquals(List.of("second"), events);
    }

    @Test
    public void completionCallbackReportsCommit() {
        List<Boolean> outcomes = new ArrayList<>();
        transaction(tx -> {
            tx.onCompletion(outcomes::add);
            return countVisits();
        });
        assertEquals(List.of(true), outcomes);
    }

    @Test
    public void completionCallbackReportsRollback() {
        List<Boolean> outcomes = new ArrayList<>();
        transaction(tx -> {
            tx.onCompletion(outcomes::add);
            tx.setRollbackOnly();
            return countVisits();
        });
        assertEquals(List.of(false), outcomes);
    }

    @Test
    public void callbacksOfEveryKindFireInRegistrationOrder() {
        List<String> events = new ArrayList<>();
        transaction(tx -> {
            tx.onCompletion(committed -> events.add("completion-1"));
            tx.onCommit(() -> events.add("commit"));
            tx.onRollback(() -> events.add("rollback"));
            tx.onCompletion(committed -> events.add("completion-2"));
            return countVisits();
        });
        assertEquals(List.of("completion-1", "commit", "completion-2"), events);
    }

    @Test
    public void commitCallbackFailureReportsTheTransactionAsCommitted() {
        var thrown = assertThrows(TransactionCallbackException.class, () ->
                transaction(tx -> {
                    orm.entity(Visit.class).removeAll();
                    tx.onCommit(() -> {
                        throw new IllegalStateException("Callback failed.");
                    });
                    return countVisits();
                }));
        assertTrue(thrown.isCommitted());
        // The failure is a failed side effect: the transaction itself committed.
        long remaining = transaction(tx -> countVisits());
        assertEquals(0L, remaining);
    }

    @Test
    public void joinedRequiredDefersCallbacksToOutermostTransaction() {
        List<String> events = new ArrayList<>();
        transaction(outer -> {
            countVisits(); // Materialize the outer transaction.
            transaction(inner -> {
                inner.onCommit(() -> events.add("inner-commit"));
                return countVisits();
            });
            // The joined scope completed, but its callbacks await the outermost physical commit.
            assertEquals(List.of(), events);
            outer.onCommit(() -> events.add("outer-commit"));
            return null;
        });
        assertEquals(List.of("inner-commit", "outer-commit"), events);
    }

    @Test
    public void requiresNewFiresCallbacksIndependently() {
        List<String> events = new ArrayList<>();
        transaction(outer -> {
            countVisits();
            transaction(REQUIRES_NEW, inner -> {
                inner.onCommit(() -> events.add("inner-commit"));
                return countVisits();
            });
            // The REQUIRES_NEW scope owns its physical transaction: its callbacks fired on its own commit.
            assertEquals(List.of("inner-commit"), events);
            return null;
        });
    }

    @Test
    public void joinedCallbacksFireOnRollbackOfOutermostTransaction() {
        List<String> events = new ArrayList<>();
        assertThrows(IllegalStateException.class, () ->
                transaction(outer -> {
                    countVisits();
                    transaction(inner -> {
                        inner.onCommit(() -> events.add("inner-commit"));
                        inner.onRollback(() -> events.add("inner-rollback"));
                        return countVisits();
                    });
                    throw new IllegalStateException("Outer failure.");
                }));
        assertEquals(List.of("inner-rollback"), events);
    }
}
