/**
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **/
package org.fusesource.remoteobr.client;

import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.felix.bundlerepository.Capability;
import org.apache.felix.bundlerepository.Requirement;
import org.apache.felix.bundlerepository.Resource;

public class ResolutionParams {

    private Set<Resource> addedResources = new LinkedHashSet<Resource>();
    private Set<Requirement> addedRequirements = new LinkedHashSet<Requirement>();
    private Set<Capability> globalCapabilities = new LinkedHashSet<Capability>();
    private Set<String> repositories = new LinkedHashSet<String>();
    private Set<Resource> localResources = new LinkedHashSet<Resource>();
    private int flags = 0;

    public Set<Resource> getAddedResources() {
        return addedResources;
    }

    public void setAddedResources(Set<Resource> addedResources) {
        this.addedResources = addedResources;
    }

    public Set<Requirement> getAddedRequirements() {
        return addedRequirements;
    }

    public void setAddedRequirements(Set<Requirement> addedRequirements) {
        this.addedRequirements = addedRequirements;
    }

    public Set<Capability> getGlobalCapabilities() {
        return globalCapabilities;
    }

    public void setGlobalCapabilities(Set<Capability> globalCapabilities) {
        this.globalCapabilities = globalCapabilities;
    }

    public Set<String> getRepositories() {
        return repositories;
    }

    public void setRepositories(Set<String> repositories) {
        this.repositories = repositories;
    }

    public Set<Resource> getLocalResources() {
        return localResources;
    }

    public void setLocalResources(Set<Resource> localResources) {
        this.localResources = localResources;
    }

    public int getFlags() {
        return flags;
    }

    public void setFlags(int flags) {
        this.flags = flags;
    }
}
