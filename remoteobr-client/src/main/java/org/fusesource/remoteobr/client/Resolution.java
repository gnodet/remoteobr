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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.felix.bundlerepository.Reason;
import org.apache.felix.bundlerepository.Resource;

public class Resolution {

    private Set<Resource> requiredResources = new LinkedHashSet<Resource>();
    private Set<Resource> optionalResources = new LinkedHashSet<Resource>();
    private Map<Resource, Set<Reason>> reasons = new LinkedHashMap<Resource, Set<Reason>>();
    private Set<Reason> unsatisfiedRequirements = new LinkedHashSet<Reason>();

    public Set<Resource> getRequiredResources() {
        return requiredResources;
    }

    public void setRequiredResources(Set<Resource> requiredResources) {
        this.requiredResources = requiredResources;
    }

    public Set<Resource> getOptionalResources() {
        return optionalResources;
    }

    public void setOptionalResources(Set<Resource> optionalResources) {
        this.optionalResources = optionalResources;
    }

    public Map<Resource, Set<Reason>> getReasons() {
        return reasons;
    }

    public void setReasons(Map<Resource, Set<Reason>> reasons) {
        this.reasons = reasons;
    }

    public Set<Reason> getUnsatisfiedRequirements() {
        return unsatisfiedRequirements;
    }

    public void setUnsatisfiedRequirements(Set<Reason> unsatisfiedRequirements) {
        this.unsatisfiedRequirements = unsatisfiedRequirements;
    }
}
