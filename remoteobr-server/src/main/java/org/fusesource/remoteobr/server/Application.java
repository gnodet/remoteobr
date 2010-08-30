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
package org.fusesource.remoteobr.server;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.felix.bundlerepository.RepositoryAdmin;

public class Application extends javax.ws.rs.core.Application {

    private RepositoryAdmin admin;

    public Application(RepositoryAdmin admin) {
        this.admin = admin;
    }

    @Override
    public Set<Object> getSingletons() {
        return Collections.<Object>singleton(new Admin(admin));
    }

    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> classes = new HashSet<Class<?>>();
        classes.add(Admin.ResourceProvider.class);
        classes.add(Admin.ResourceRefsProvider.class);
        classes.add(Admin.ResourceRefProvider.class);
        classes.add(Admin.RepositoryProvider.class);
        classes.add(Admin.RepositoryRefsProvider.class);
        classes.add(Admin.RepositoryRefProvider.class);
        classes.add(Admin.DiscoverProvider.class);
        classes.add(Admin.ResolveProvider.class);
        classes.add(Admin.ResolutionProvider.class);
        return classes;
    }
}
