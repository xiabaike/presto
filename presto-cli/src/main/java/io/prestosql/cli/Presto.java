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
package io.prestosql.cli;

import com.google.common.net.HostAndPort;
import io.airlift.units.Duration;
import io.prestosql.cli.ClientOptions.ClientExtraCredential;
import io.prestosql.cli.ClientOptions.ClientResourceEstimate;
import io.prestosql.cli.ClientOptions.ClientSessionProperty;
import picocli.CommandLine;
import picocli.CommandLine.IVersionProvider;

import static com.google.common.base.MoreObjects.firstNonNull;

public final class Presto
{
    private Presto() {}

    public static void main(String[] args)
    {
        System.exit(createCommandLine(new Console()).execute(args));
    }

    public static CommandLine createCommandLine(Object command)
    {
        return new CommandLine(command)
                .registerConverter(ClientResourceEstimate.class, ClientResourceEstimate::new)
                .registerConverter(ClientSessionProperty.class, ClientSessionProperty::new)
                .registerConverter(ClientExtraCredential.class, ClientExtraCredential::new)
                .registerConverter(HostAndPort.class, HostAndPort::fromString)
                .registerConverter(Duration.class, Duration::valueOf);
    }

    public static class VersionProvider
            implements IVersionProvider
    {
        @Override
        public String[] getVersion()
        {
            String version = getClass().getPackage().getImplementationVersion();
            return new String[] {"Presto CLI " + firstNonNull(version, "(version unknown)")};
        }
    }
}
