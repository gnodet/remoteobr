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
    private final ResolveParams params = new ResolveParams();
    private Resolution result = new Resolution();

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
        result = null;
        params.getAddedResources().add(resource);
    }

    public synchronized Resource[] getAddedResources() {
        return params.getAddedResources().toArray(new Resource[params.getAddedResources().size()]);
    }

    public synchronized void add(Requirement requirement) {
        result = null;
        params.getAddedRequirements().add(requirement);
    }

    public synchronized Requirement[] getAddedRequirements() {
        return params.getAddedRequirements().toArray(new Requirement[params.getAddedRequirements().size()]);
    }

    public synchronized void addGlobalCapability(Capability capability) {
        result = null;
        params.getGlobalCapabilities().add(capability);
    }

    public synchronized Capability[] getGlobalCapabilities() {
        return params.getGlobalCapabilities().toArray(new Capability[params.getGlobalCapabilities().size()]);
    }

    public synchronized Resource[] getRequiredResources() {
        if (result != null)
        {
            return result.getRequiredResources().toArray(new Resource[result.getRequiredResources().size()]);
        }
        throw new IllegalStateException("The resources have not been resolved.");
    }

    public synchronized Resource[] getOptionalResources() {
        if (result != null)
        {
            return result.getOptionalResources().toArray(new Resource[result.getOptionalResources().size()]);
        }
        throw new IllegalStateException("The resources have not been resolved.");
    }

    public synchronized Reason[] getReason(Resource resource) {
        if (result != null)
        {
            Set<Reason> l = result.getReasons().get(resource);
            return l != null ? l.toArray(new Reason[l.size()]) : null;
        }
        throw new IllegalStateException("The resources have not been resolved.");
    }

    public synchronized Reason[] getUnsatisfiedRequirements() {
        if (result != null)
        {
            return result.getUnsatisfiedRequirements().toArray(new Reason[result.getUnsatisfiedRequirements().size()]);
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
        result = client.resolve(params);
        return result.getUnsatisfiedRequirements().isEmpty();
    }

    public synchronized void deploy(int i) {
        throw new UnsupportedOperationException();
    }

}
