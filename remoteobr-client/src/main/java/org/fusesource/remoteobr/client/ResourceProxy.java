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

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Map;

import org.apache.felix.bundlerepository.Capability;
import org.apache.felix.bundlerepository.Requirement;
import org.apache.felix.bundlerepository.Resource;
import org.osgi.framework.Version;

/**
 * Resource that lazily loads the real resource from the remote server.
 * This allows very fast requests
 */
public class ResourceProxy implements Resource {

    private final String id;
    private final String repository;
    private final transient RepositoryAdminProxy client;
    private volatile transient Reference<Resource> resource;

    public ResourceProxy(RepositoryAdminProxy client, String id, String repository) {
        this.client = client;
        this.id = id;
        this.repository = repository;
    }

    public String getId() {
        return id;
    }

    public String getRepository() {
        return repository;
    }

    public Map getProperties() {
        return getResource().getProperties();
    }

    public String getSymbolicName() {
        return getResource().getSymbolicName();
    }

    public Version getVersion() {
        return getResource().getVersion();
    }

    public String getPresentationName() {
        return getResource().getPresentationName();
    }

    public String getURI() {
        return getResource().getURI();
    }

    public Long getSize() {
        return getResource().getSize();
    }

    public String[] getCategories() {
        return getResource().getCategories();
    }

    public Capability[] getCapabilities() {
        return getResource().getCapabilities();
    }

    public Requirement[] getRequirements() {
        return getResource().getRequirements();
    }

    public boolean isLocal() {
        return false;
    }

    private Resource getResource() {
        Resource r;
        if (resource == null || (r = resource.get()) == null) {
            synchronized (this) {
                if (resource == null || (r = resource.get()) == null) {
                    r = client.loadResource(id, repository);
                    resource = new WeakReference<Resource>(r);
                }
            }
        }
        return r;
    }
}
