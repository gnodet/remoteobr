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

import java.util.Dictionary;
import java.util.Hashtable;

import com.sun.jersey.spi.container.servlet.ServletContainer;
import org.apache.felix.bundlerepository.RepositoryAdmin;
import org.apache.felix.bundlerepository.impl.RepositoryAdminImpl;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.http.HttpService;
import org.osgi.util.tracker.ServiceTracker;

public class Activator implements BundleActivator {

    private BundleContext bundleContext;
    private ServiceTracker httpServiceTracker;
    private RepositoryAdmin admin;

    public void start(BundleContext context) throws Exception {
        bundleContext = context;
        admin = new RepositoryAdminImpl(bundleContext, null);
        httpServiceTracker = new ServiceTracker(bundleContext, HttpService.class.getName(), null) {
            @Override
            public Object addingService(ServiceReference reference) {
                HttpService service = (HttpService) super.addingService(reference);
                try {
                    registerServlet(service);
                    return service;
                } catch (Exception e) {
                    removedService(reference, service);
                    return null;
                }
            }
        };
        httpServiceTracker.open();
    }

    public void stop(BundleContext context) throws Exception {
    }

    protected void registerServlet(HttpService service) throws Exception {
        ServletContainer servlet = new ServletContainer(new Application(admin));
        Dictionary<String,String> params = new Hashtable<String,String>();
        service.registerServlet("/obr", servlet, params, null);
    }


}
