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
package st.orm.core.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class DatabaseProductNameCacheTest {

    @Test
    void cachesProductNamePerDataSourceIdentity() {
        var connectionCount = new AtomicInteger();
        DataSource dataSource = dataSource(connectionCount, "H2");
        assertEquals("H2", Providers.getDatabaseProductName(dataSource));
        assertEquals("H2", Providers.getDatabaseProductName(dataSource));
        assertEquals(1, connectionCount.get());
    }

    @Test
    void cacheDoesNotPinCollectedDataSource() throws Exception {
        DataSource dataSource = dataSource(new AtomicInteger(), "H2");
        assertEquals("H2", Providers.getDatabaseProductName(dataSource));
        var reference = new WeakReference<>(dataSource);
        //noinspection UnusedAssignment
        dataSource = null;
        awaitCleared(reference);
    }

    private static DataSource dataSource(AtomicInteger connectionCount, String productName) {
        ClassLoader loader = DatabaseProductNameCacheTest.class.getClassLoader();
        InvocationHandler metaDataHandler = (proxy, method, args) -> {
            if (method.getName().equals("getDatabaseProductName")) {
                return productName;
            }
            throw new UnsupportedOperationException(method.getName());
        };
        DatabaseMetaData metaData = (DatabaseMetaData) Proxy.newProxyInstance(
                loader, new Class<?>[] {DatabaseMetaData.class}, metaDataHandler);
        InvocationHandler connectionHandler = (proxy, method, args) -> switch (method.getName()) {
            case "getMetaData" -> metaData;
            case "close" -> null;
            default -> throw new UnsupportedOperationException(method.getName());
        };
        Connection connection = (Connection) Proxy.newProxyInstance(
                loader, new Class<?>[] {Connection.class}, connectionHandler);
        InvocationHandler dataSourceHandler = (proxy, method, args) -> {
            if (method.getName().equals("getConnection") && (args == null || args.length == 0)) {
                connectionCount.incrementAndGet();
                return connection;
            }
            throw new UnsupportedOperationException(method.getName());
        };
        return (DataSource) Proxy.newProxyInstance(loader, new Class<?>[] {DataSource.class}, dataSourceHandler);
    }

    private static void awaitCleared(WeakReference<?> reference) throws InterruptedException {
        for (int attempt = 0; attempt < 200; attempt++) {
            if (reference.get() == null) {
                return;
            }
            System.gc();
            Thread.sleep(10);
        }
        fail("Referent was not collected; the cache still pins it.");
    }
}
