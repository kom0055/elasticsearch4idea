/*
 * Copyright 2020 Anton Shuvaev
 *
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

package org.elasticsearch4idea.service

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import org.elasticsearch4idea.model.AutoRefreshOptions
import org.elasticsearch4idea.model.ClusterConfiguration
import org.elasticsearch4idea.model.ViewMode
import java.util.concurrent.ConcurrentHashMap


@Service
@State(
    name = "ElasticsearchConfiguration",
    storages = [Storage(value = "\$PROJECT_CONFIG_DIR$/elasticsearchSettings.xml")]
)
class ElasticsearchConfiguration(private val project: Project) :
    PersistentStateComponent<ElasticsearchConfiguration.State> {

    private val clusterConfigurations: MutableMap<String, ClusterConfiguration> = ConcurrentHashMap()
    var autoRefresh: AutoRefreshOptions = AutoRefreshOptions.DISABLED
    var viewMode: ViewMode = ViewMode.TEXT

    override fun getState(): State {
        val clusters = HashMap<String, ClusterConfigInternal>()
        clusterConfigurations.forEach { (label, config) ->
            if (config.credentials != null) {
                val credentials = ClusterConfiguration.Credentials(config.credentials.user, config.credentials.password)
                storeCredentials(label, credentials)
            }
            clusters.put(label, ClusterConfigInternal(config.label, config.url))
        }
        return State(clusters, autoRefresh, viewMode)
    }

    override fun loadState(state: State) {
        this.autoRefresh = state.autoRefresh
        this.viewMode = state.viewMode
        clusterConfigurations.clear()

        state.clusterConfigurations.asSequence().map {
            ClusterConfiguration(
                label = it.value.label,
                url = it.value.url,
                credentials = readCredentials(it.key)
            )
        }
            .forEach { clusterConfigurations.put(it.label, it) }
    }

    private fun readCredentials(label: String): ClusterConfiguration.Credentials? {
        val credentialAttributes = createCredentialAttributes(label)
        val credentials = PasswordSafe.instance.get(credentialAttributes)
        if (credentials?.userName == null || credentials.password == null) {
            return null
        }
        return ClusterConfiguration.Credentials(credentials.userName!!, credentials.getPasswordAsString()!!)
    }

    private fun storeCredentials(label: String, configCredentials: ClusterConfiguration.Credentials) {
        val credentialAttributes = createCredentialAttributes(label)
        val credentials = Credentials(configCredentials.user, configCredentials.password)
        PasswordSafe.instance.set(credentialAttributes, credentials)
    }

    private fun createCredentialAttributes(key: String): CredentialAttributes {
        return CredentialAttributes(generateServiceName("ElasticsearchPlugin", key))
    }

    fun putClusterConfiguration(clusterConfiguration: ClusterConfiguration) {
        clusterConfigurations[clusterConfiguration.label] = clusterConfiguration
    }

    fun removeClusterConfiguration(label: String) {
        clusterConfigurations.remove(label)
    }

    fun hasConfiguration(label: String): Boolean {
        return clusterConfigurations.containsKey(label)
    }

    fun getConfigurations(): List<ClusterConfiguration> {
        return clusterConfigurations.values.sortedBy { it.label }
    }

    fun getConfiguration(name: String): ClusterConfiguration? {
        return clusterConfigurations[name]
    }

    class State(
        var clusterConfigurations: Map<String, ClusterConfigInternal> = HashMap(),
        var autoRefresh: AutoRefreshOptions = AutoRefreshOptions.DISABLED,
        var viewMode: ViewMode = ViewMode.TEXT
    )

    class ClusterConfigInternal(
        var label: String = "",
        var url: String = ""
    )

}