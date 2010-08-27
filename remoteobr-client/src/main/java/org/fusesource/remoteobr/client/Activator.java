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

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.felix.bundlerepository.RepositoryAdmin;
import org.apache.felix.bundlerepository.impl.LocalRepositoryImpl;
import org.apache.felix.bundlerepository.impl.SystemRepositoryImpl;
import org.apache.felix.utils.log.Logger;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;

public class Activator implements BundleActivator, ManagedService {

    private BundleContext bundleContext;
    private ServiceRegistration registration;
    private SystemRepositoryImpl system;
    private LocalRepositoryImpl local;

    public void start(BundleContext context) throws Exception {
        this.bundleContext = context;
        Logger logger = new Logger(bundleContext);
        system = new SystemRepositoryImpl(bundleContext, logger);
        local = new LocalRepositoryImpl(bundleContext, logger);
        Hashtable<String,String> props = new Hashtable<String,String>();
        props.put("service.pid", "org.fusesource.remoteobr");
        bundleContext.registerService(ManagedService.class.getName(), this, props);
    }

    public void stop(BundleContext context) throws Exception {

    }

    public void updated(Dictionary properties) throws ConfigurationException {
        Object o = properties.get("url");
        if (registration != null) {
            registration.unregister();
            registration = null;
        }
        if (o != null) {
            RepositoryAdminProxy proxy = new RepositoryAdminProxy(o.toString(), system, local);
            registration = bundleContext.registerService(RepositoryAdmin.class.getName(), proxy, null);
        } 
    }

}
