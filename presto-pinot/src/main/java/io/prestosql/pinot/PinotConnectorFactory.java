/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.prestosql.pinot;

import com.google.common.collect.ImmutableList;
import com.google.inject.Injector;
import com.google.inject.Module;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.json.JsonModule;
import io.prestosql.spi.connector.Connector;
import io.prestosql.spi.connector.ConnectorContext;
import io.prestosql.spi.connector.ConnectorFactory;
import io.prestosql.spi.connector.ConnectorHandleResolver;
import org.weakref.jmx.guice.MBeanModule;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class PinotConnectorFactory
        implements ConnectorFactory
{
    private final Optional<Module> extension;

    public PinotConnectorFactory(Optional<Module> extension)
    {
        this.extension = requireNonNull(extension, "extension is null");
    }

    @Override
    public String getName()
    {
        return "pinot";
    }

    @Override
    public ConnectorHandleResolver getHandleResolver()
    {
        return new PinotHandleResolver();
    }

    @Override
    public Connector create(String catalogName, Map<String, String> config, ConnectorContext context)
    {
        requireNonNull(catalogName, "connectorId is null");
        requireNonNull(config, "config is null");

        ImmutableList.Builder<Module> modulesBuilder = ImmutableList.<Module>builder()
                .add(new JsonModule())
                .add(new MBeanModule())
                .add(new PinotModule(catalogName, context.getTypeManager(), context.getNodeManager()));

        extension.ifPresent(modulesBuilder::add);

        Bootstrap app = new Bootstrap(modulesBuilder.build());

        Injector injector = app.strictConfig()
                .doNotInitializeLogging()
                .setRequiredConfigurationProperties(config)
                .initialize();

        return injector.getInstance(PinotConnector.class);
    }
}
