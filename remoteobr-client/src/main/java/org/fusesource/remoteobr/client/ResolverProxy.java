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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.felix.bundlerepository.Capability;
import org.apache.felix.bundlerepository.InterruptedResolutionException;
import org.apache.felix.bundlerepository.Reason;
import org.apache.felix.bundlerepository.Repository;
import org.apache.felix.bundlerepository.Requirement;
import org.apache.felix.bundlerepository.Resolver;
import org.apache.felix.bundlerepository.Resource;

/**
 * Resolver implementation delegating to the remote server.
 * Deployment is not supported.
 */
public class ResolverProxy implements Resolver {

    private final RepositoryAdminProxy client;
    private final Repository[] repositories;
    private final ResolutionParams params = new ResolutionParams();
    private ResolutionResults results = new ResolutionResults();

    public ResolverProxy(RepositoryAdminProxy client, Repository[] repositories) {
        this.client = client;
        this.repositories = repositories;
        for (Repository repo : repositories) {
            String uri = repo.getURI();
            if (!uri.equals(Repository.SYSTEM) && !uri.equals(Repository.LOCAL)) {
                this.params.getRepositories().add(uri);
            }
        }
    }

    public synchronized void add(Resource resource) {
        results = null;
        params.getAddedResources().add(resource);
    }

    public synchronized Resource[] getAddedResources() {
        return params.getAddedResources().toArray(new Resource[params.getAddedResources().size()]);
    }

    public synchronized void add(Requirement requirement) {
        results = null;
        params.getAddedRequirements().add(requirement);
    }

    public synchronized Requirement[] getAddedRequirements() {
        return params.getAddedRequirements().toArray(new Requirement[params.getAddedRequirements().size()]);
    }

    public synchronized void addGlobalCapability(Capability capability) {
        results = null;
        params.getGlobalCapabilities().add(capability);
    }

    public synchronized Capability[] getGlobalCapabilities() {
        return params.getGlobalCapabilities().toArray(new Capability[params.getGlobalCapabilities().size()]);
    }

    public synchronized Resource[] getRequiredResources() {
        if (results != null)
        {
            return results.getRequiredResources().toArray(new Resource[results.getRequiredResources().size()]);
        }
        throw new IllegalStateException("The resources have not been resolved.");
    }

    public synchronized Resource[] getOptionalResources() {
        if (results != null)
        {
            return results.getOptionalResources().toArray(new Resource[results.getOptionalResources().size()]);
        }
        throw new IllegalStateException("The resources have not been resolved.");
    }

    public synchronized Reason[] getReason(Resource resource) {
        if (results != null)
        {
            Set<Reason> l = results.getReasons().get(resource);
            return l != null ? l.toArray(new Reason[l.size()]) : null;
        }
        throw new IllegalStateException("The resources have not been resolved.");
    }

    public synchronized Reason[] getUnsatisfiedRequirements() {
        if (results != null)
        {
            return results.getUnsatisfiedRequirements().toArray(new Reason[results.getUnsatisfiedRequirements().size()]);
        }
        throw new IllegalStateException("The resources have not been resolved.");
    }

    public synchronized boolean resolve() throws InterruptedResolutionException {
        return resolve(0);
    }

    public synchronized boolean resolve(int i) throws InterruptedResolutionException {
        Set<Resource> resources = new HashSet<Resource>();
        for (Repository repository : repositories) {
            String uri = repository.getURI();
            if (uri.equals(Repository.SYSTEM) || uri.equals(Repository.LOCAL)) {
                resources.addAll(Arrays.asList(repository.getResources()));
            }
        }
        params.setLocalResources(resources);
        results = client.resolve(params);
        return results.getUnsatisfiedRequirements().isEmpty();
    }

    public synchronized void deploy(int i) {
        throw new UnsupportedOperationException();
    }

}
