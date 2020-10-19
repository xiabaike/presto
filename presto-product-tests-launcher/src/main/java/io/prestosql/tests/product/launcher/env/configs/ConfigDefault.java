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
package io.prestosql.tests.product.launcher.env.configs;

import io.prestosql.tests.product.launcher.env.EnvironmentConfig;
import io.prestosql.tests.product.launcher.env.EnvironmentDefaults;

import static com.google.common.base.MoreObjects.toStringHelper;
import static io.prestosql.tests.product.launcher.env.Environments.nameForConfigClass;

public class ConfigDefault
        implements EnvironmentConfig
{
    @Override
    public String getHadoopBaseImage()
    {
        return EnvironmentDefaults.HADOOP_BASE_IMAGE;
    }

    @Override
    public String getImagesVersion()
    {
        return EnvironmentDefaults.DOCKER_IMAGES_VERSION;
    }

    @Override
    public String getHadoopImagesVersion()
    {
        return EnvironmentDefaults.HADOOP_IMAGES_VERSION;
    }

    @Override
    public String getTemptoEnvironmentConfigFile()
    {
        return EnvironmentDefaults.TEMPTO_ENVIRONMENT_CONFIG;
    }

    @Override
    public String getConfigName()
    {
        return nameForConfigClass(getClass());
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("hadoopBaseImage", getHadoopBaseImage())
                .add("hadoopImagesVersion", getHadoopImagesVersion())
                .add("imagesVersion", getImagesVersion())
                .add("excludedGroups", getExcludedGroups())
                .add("excludedTests", getExcludedTests())
                .add("temptoEnvironmentConfigFile", getTemptoEnvironmentConfigFile())
                .toString();
    }
}
