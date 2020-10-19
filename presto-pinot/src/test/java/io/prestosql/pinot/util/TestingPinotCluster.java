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
package io.prestosql.pinot.util;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.io.ByteStreams;
import com.google.common.net.HostAndPort;
import com.google.common.net.HttpHeaders;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.Request;
import io.airlift.http.client.StaticBodyGenerator;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.json.JsonCodec;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

import java.io.Closeable;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkState;
import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;
import static io.airlift.json.JsonCodec.jsonCodec;
import static io.airlift.json.JsonCodec.listJsonCodec;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.testcontainers.containers.KafkaContainer.ZOOKEEPER_PORT;

public class TestingPinotCluster
        implements Closeable
{
    private static final String BASE_IMAGE = "apachepinot/pinot:github_final_demo";
    private static final String ZOOKEEPER_INTERNAL_HOST = "zookeeper";
    private static final JsonCodec<List<String>> LIST_JSON_CODEC = listJsonCodec(String.class);
    private static final JsonCodec<PinotSuccessResponse> PINOT_SUCCESS_RESPONSE_JSON_CODEC = jsonCodec(PinotSuccessResponse.class);

    public static final int CONTROLLER_PORT = 9000;
    public static final int BROKER_PORT = 8099;
    public static final int SERVER_ADMIN_PORT = 8097;
    public static final int SERVER_PORT = 8098;

    private final GenericContainer<?> controller;
    private final GenericContainer<?> broker;
    private final GenericContainer<?> server;
    private final GenericContainer<?> zookeeper;
    private final HttpClient httpClient;

    public TestingPinotCluster()
    {
        httpClient = new JettyHttpClient();
        zookeeper = new GenericContainer<>("zookeeper:3.5.6")
                .withNetwork(Network.SHARED)
                .withNetworkAliases(ZOOKEEPER_INTERNAL_HOST)
                .withEnv("ZOOKEEPER_CLIENT_PORT", String.valueOf(ZOOKEEPER_PORT))
                .withExposedPorts(ZOOKEEPER_PORT);

        controller = new GenericContainer<>(BASE_IMAGE)
                .withNetwork(Network.SHARED)
                .withClasspathResourceMapping("/pinot-controller", "/var/pinot/controller/config", BindMode.READ_ONLY)
                .withEnv("JAVA_OPTS", "-Xmx512m -Dlog4j2.configurationFile=/opt/pinot/conf/pinot-controller-log4j2.xml -Dplugins.dir=/opt/pinot/plugins")
                .withCommand("StartController", "-configFileName", "/var/pinot/controller/config/pinot-controller.conf")
                .withNetworkAliases("pinot-controller", "localhost")
                .withExposedPorts(CONTROLLER_PORT);

        broker = new GenericContainer<>(BASE_IMAGE)
                .withNetwork(Network.SHARED)
                .withClasspathResourceMapping("/pinot-broker", "/var/pinot/broker/config", BindMode.READ_ONLY)
                .withEnv("JAVA_OPTS", "-Xmx512m -Dlog4j2.configurationFile=/opt/pinot/conf/pinot-broker-log4j2.xml -Dplugins.dir=/opt/pinot/plugins")
                .withCommand("StartBroker", "-clusterName", "pinot", "-zkAddress", getZookeeperInternalHostPort(), "-configFileName", "/var/pinot/broker/config/pinot-broker.conf")
                .withNetworkAliases("pinot-broker", "localhost")
                .withExposedPorts(BROKER_PORT);

        server = new GenericContainer<>(BASE_IMAGE)
                .withNetwork(Network.SHARED)
                .withClasspathResourceMapping("/pinot-server", "/var/pinot/server/config", BindMode.READ_ONLY)
                .withEnv("JAVA_OPTS", "-Xmx512m -Dlog4j2.configurationFile=/opt/pinot/conf/pinot-server-log4j2.xml -Dplugins.dir=/opt/pinot/plugins")
                .withCommand("StartServer", "-clusterName", "pinot", "-zkAddress", getZookeeperInternalHostPort(), "-configFileName", "/var/pinot/server/config/pinot-server.conf")
                .withNetworkAliases("pinot-server", "localhost")
                .withExposedPorts(SERVER_PORT, SERVER_ADMIN_PORT);
    }

    public void start()
    {
        zookeeper.start();
        controller.start();
        broker.start();
        server.start();
    }

    @Override
    public void close()
    {
        server.stop();
        broker.stop();
        controller.stop();
        zookeeper.stop();
        httpClient.close();
    }

    private static String getZookeeperInternalHostPort()
    {
        return format("%s:%s", ZOOKEEPER_INTERNAL_HOST, ZOOKEEPER_PORT);
    }

    public String getControllerConnectString()
    {
        return controller.getContainerIpAddress() + ":" + controller.getMappedPort(CONTROLLER_PORT);
    }

    public HostAndPort getBrokerHostAndPort()
    {
        return HostAndPort.fromParts(broker.getContainerIpAddress(), broker.getMappedPort(BROKER_PORT));
    }

    public HostAndPort getServerHostAndPort()
    {
        return HostAndPort.fromParts(server.getContainerIpAddress(), server.getMappedPort(SERVER_PORT));
    }

    public void createSchema(InputStream tableSchemaSpec, String tableName)
            throws Exception
    {
        byte[] bytes = ByteStreams.toByteArray(tableSchemaSpec);
        Request request = Request.Builder.preparePost()
                .setUri(getControllerUri("schemas"))
                .setHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .setHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                .setBodyGenerator(StaticBodyGenerator.createStaticBodyGenerator(bytes))
                .build();

        PinotSuccessResponse response = doWithRetries(() -> httpClient.execute(request, createJsonResponseHandler(PINOT_SUCCESS_RESPONSE_JSON_CODEC)), 10);
        checkState(response.getStatus().equals(format("%s successfully added", tableName)), "Unexpected response: '%s'", response.getStatus());
        verifySchema(tableName);
    }

    private URI getControllerUri(String path)
    {
        return URI.create(format("http://%s/%s", getControllerConnectString(), path));
    }

    private void verifySchema(String tableName)
            throws Exception
    {
        Request request = Request.Builder.prepareGet().setUri(getControllerUri("schemas"))
                .setHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .build();
        doWithRetries(() -> {
            List<String> schemas = httpClient.execute(request, createJsonResponseHandler(LIST_JSON_CODEC));
            checkState(schemas.contains(tableName), format("Schema for '%s' not found", tableName));
            return null;
        }, 10);
    }

    public void addRealTimeTable(InputStream realTimeSpec, String tableName)
            throws Exception
    {
        byte[] bytes = ByteStreams.toByteArray(realTimeSpec);
        Request request = Request.Builder.preparePost()
                .setUri(getControllerUri("tables"))
                .setHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .setHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                .setBodyGenerator(StaticBodyGenerator.createStaticBodyGenerator(bytes))
                .build();

        PinotSuccessResponse response = doWithRetries(() -> httpClient.execute(request, createJsonResponseHandler(PINOT_SUCCESS_RESPONSE_JSON_CODEC)), 10);
        // Typo in response: https://github.com/apache/incubator-pinot/issues/5566
        checkState(response.getStatus().equals(format("Table %s_REALTIME succesfully added", tableName)), "Unexpected response: '%s'", response.getStatus());
    }

    private static <T> T doWithRetries(Supplier<T> supplier, int retries)
            throws Exception
    {
        Exception exception = null;
        for (int retry = 0; retry < retries; retry++) {
            try {
                return supplier.get();
            }
            catch (Exception t) {
                exception = t;
            }
            Thread.sleep(1000);
        }
        throw exception;
    }

    public static class PinotSuccessResponse
    {
        private final String status;

        @JsonCreator
        public PinotSuccessResponse(@JsonProperty("status") String status)
        {
            this.status = requireNonNull(status, "status is null");
        }

        @JsonProperty
        public String getStatus()
        {
            return status;
        }
    }
}
