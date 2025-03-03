/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.minicluster;

import org.apache.flink.configuration.AkkaOptions;
import org.apache.flink.configuration.ClusterOptions;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.JobManagerOptions;
import org.apache.flink.configuration.NettyShuffleEnvironmentOptions;
import org.apache.flink.configuration.RestOptions;
import org.apache.flink.configuration.TaskManagerOptions;
import org.apache.flink.configuration.UnmodifiableConfiguration;
import org.apache.flink.core.plugin.PluginManager;
import org.apache.flink.runtime.taskexecutor.TaskExecutorResourceUtils;
import org.apache.flink.util.Preconditions;

import javax.annotation.Nullable;

import java.time.Duration;

import static org.apache.flink.runtime.minicluster.RpcServiceSharing.SHARED;

/** Configuration object for the {@link MiniCluster}. */
public class MiniClusterConfiguration {

    static final int DEFAULT_IO_POOL_SIZE = 4;

    private final UnmodifiableConfiguration configuration;

    private final int numTaskManagers;

    private final RpcServiceSharing rpcServiceSharing;

    @Nullable private final String commonBindAddress;

    private final MiniCluster.HaServices haServices;

    @Nullable private final PluginManager pluginManager;

    // ------------------------------------------------------------------------
    //  Construction
    // ------------------------------------------------------------------------

    public MiniClusterConfiguration(
            Configuration configuration,
            int numTaskManagers,
            RpcServiceSharing rpcServiceSharing,
            @Nullable String commonBindAddress,
            MiniCluster.HaServices haServices,
            @Nullable PluginManager pluginManager) {
        this.numTaskManagers = numTaskManagers;
        this.configuration = generateConfiguration(Preconditions.checkNotNull(configuration));
        this.rpcServiceSharing = Preconditions.checkNotNull(rpcServiceSharing);
        this.commonBindAddress = commonBindAddress;
        this.haServices = haServices;
        this.pluginManager = pluginManager;
    }

    private UnmodifiableConfiguration generateConfiguration(final Configuration configuration) {
        final Configuration modifiedConfig = new Configuration(configuration);

        TaskExecutorResourceUtils.adjustForLocalExecution(modifiedConfig);

        // reduce the default number of network buffers used by sort-shuffle to avoid the
        // "Insufficient number of network buffers" error.
        if (!modifiedConfig.contains(
                NettyShuffleEnvironmentOptions.NETWORK_SORT_SHUFFLE_MIN_BUFFERS)) {
            modifiedConfig.set(NettyShuffleEnvironmentOptions.NETWORK_SORT_SHUFFLE_MIN_BUFFERS, 16);
        }

        // set default io pool size.
        if (!modifiedConfig.contains(ClusterOptions.CLUSTER_IO_EXECUTOR_POOL_SIZE)) {
            modifiedConfig.set(ClusterOptions.CLUSTER_IO_EXECUTOR_POOL_SIZE, DEFAULT_IO_POOL_SIZE);
        }

        // increase the ask.timeout if not set in order to harden tests on slow CI
        if (!modifiedConfig.contains(AkkaOptions.ASK_TIMEOUT_DURATION)) {
            modifiedConfig.set(AkkaOptions.ASK_TIMEOUT_DURATION, Duration.ofMinutes(5L));
        }

        return new UnmodifiableConfiguration(modifiedConfig);
    }

    // ------------------------------------------------------------------------
    //  getters
    // ------------------------------------------------------------------------

    public RpcServiceSharing getRpcServiceSharing() {
        return rpcServiceSharing;
    }

    @Nullable
    public PluginManager getPluginManager() {
        return pluginManager;
    }

    public int getNumTaskManagers() {
        return numTaskManagers;
    }

    public String getJobManagerExternalAddress() {
        return commonBindAddress != null
                ? commonBindAddress
                : configuration.get(JobManagerOptions.ADDRESS, "localhost");
    }

    public String getTaskManagerExternalAddress() {
        return commonBindAddress != null
                ? commonBindAddress
                : configuration.get(TaskManagerOptions.HOST, "localhost");
    }

    public String getJobManagerExternalPortRange() {
        return String.valueOf(configuration.get(JobManagerOptions.PORT, 0));
    }

    public String getTaskManagerExternalPortRange() {
        return configuration.get(TaskManagerOptions.RPC_PORT);
    }

    public String getJobManagerBindAddress() {
        return commonBindAddress != null
                ? commonBindAddress
                : configuration.get(JobManagerOptions.BIND_HOST, "localhost");
    }

    public String getTaskManagerBindAddress() {
        return commonBindAddress != null
                ? commonBindAddress
                : configuration.get(TaskManagerOptions.BIND_HOST, "localhost");
    }

    public UnmodifiableConfiguration getConfiguration() {
        return configuration;
    }

    public MiniCluster.HaServices getHaServices() {
        return haServices;
    }

    @Override
    public String toString() {
        return "MiniClusterConfiguration {"
                + "singleRpcService="
                + rpcServiceSharing
                + ", numTaskManagers="
                + numTaskManagers
                + ", commonBindAddress='"
                + commonBindAddress
                + '\''
                + ", config="
                + configuration
                + '}';
    }

    // ----------------------------------------------------------------------------------
    // Enums
    // ----------------------------------------------------------------------------------

    // ----------------------------------------------------------------------------------
    // Builder
    // ----------------------------------------------------------------------------------

    /** Builder for the MiniClusterConfiguration. */
    public static class Builder {
        private Configuration configuration = new Configuration();
        private int numTaskManagers = 1;
        private int numSlotsPerTaskManager = 1;
        private RpcServiceSharing rpcServiceSharing = SHARED;
        @Nullable private String commonBindAddress = null;
        private MiniCluster.HaServices haServices = MiniCluster.HaServices.CONFIGURED;
        private boolean useRandomPorts = false;
        @Nullable private PluginManager pluginManager;

        public Builder setConfiguration(Configuration configuration1) {
            this.configuration = Preconditions.checkNotNull(configuration1);
            return this;
        }

        public Builder setNumTaskManagers(int numTaskManagers) {
            this.numTaskManagers = numTaskManagers;
            return this;
        }

        public Builder setNumSlotsPerTaskManager(int numSlotsPerTaskManager) {
            this.numSlotsPerTaskManager = numSlotsPerTaskManager;
            return this;
        }

        public Builder setRpcServiceSharing(RpcServiceSharing rpcServiceSharing) {
            this.rpcServiceSharing = Preconditions.checkNotNull(rpcServiceSharing);
            return this;
        }

        public Builder setCommonBindAddress(String commonBindAddress) {
            this.commonBindAddress = commonBindAddress;
            return this;
        }

        public Builder setHaServices(MiniCluster.HaServices haServices) {
            this.haServices = haServices;
            return this;
        }

        public Builder withRandomPorts() {
            this.useRandomPorts = true;
            return this;
        }

        public Builder setPluginManager(PluginManager pluginManager) {
            this.pluginManager = Preconditions.checkNotNull(pluginManager);
            return this;
        }

        public MiniClusterConfiguration build() {
            final Configuration modifiedConfiguration = new Configuration(configuration);
            modifiedConfiguration.set(TaskManagerOptions.NUM_TASK_SLOTS, numSlotsPerTaskManager);
            modifiedConfiguration.set(
                    RestOptions.ADDRESS,
                    modifiedConfiguration.get(RestOptions.ADDRESS, "localhost"));

            if (useRandomPorts) {
                if (!configuration.contains(JobManagerOptions.PORT)) {
                    modifiedConfiguration.set(JobManagerOptions.PORT, 0);
                }
                if (!configuration.contains(RestOptions.BIND_PORT)) {
                    modifiedConfiguration.set(RestOptions.BIND_PORT, "0");
                }
            }

            return new MiniClusterConfiguration(
                    modifiedConfiguration,
                    numTaskManagers,
                    rpcServiceSharing,
                    commonBindAddress,
                    haServices,
                    pluginManager);
        }
    }
}
