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
package org.fusesource.remoteobr;

import java.util.Hashtable;


import com.sun.jersey.server.impl.container.servlet.ServletAdaptor;
import org.apache.felix.bundlerepository.Repository;
import org.apache.felix.bundlerepository.RepositoryAdmin;
import org.apache.felix.bundlerepository.Resolver;
import org.apache.felix.bundlerepository.Resource;
import org.apache.felix.bundlerepository.impl.LocalRepositoryImpl;
import org.apache.felix.bundlerepository.impl.RepositoryAdminImpl;
import org.apache.felix.bundlerepository.impl.SystemRepositoryImpl;
import org.easymock.EasyMock;
import org.fusesource.remoteobr.client.RepositoryAdminProxy;
import org.fusesource.remoteobr.server.Application;
import org.junit.Test;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleListener;
import org.osgi.framework.ServiceListener;

import static junit.framework.Assert.*;

public class RemoteObrTest {

    @Test
    public void testRest() throws Exception {
        BundleContext bundleContext = EasyMock.createMock(BundleContext.class);
        Bundle systemBundle = EasyMock.createMock(Bundle.class);
        EasyMock.expect(bundleContext.getProperty((String) EasyMock.anyObject())).andReturn(null).anyTimes();
        EasyMock.expect(bundleContext.getBundle(0)).andReturn(systemBundle).anyTimes();
        EasyMock.expect(bundleContext.getProperty("obr.repository.url")).andReturn(null);
        EasyMock.expect(systemBundle.getHeaders()).andReturn(new Hashtable()).anyTimes();
        EasyMock.expect(systemBundle.getRegisteredServices()).andReturn(null).anyTimes();
        EasyMock.expect(systemBundle.getBundleId()).andReturn(0L).anyTimes();
        EasyMock.expect(systemBundle.getBundleContext()).andReturn(bundleContext).anyTimes();
        bundleContext.addBundleListener((BundleListener) EasyMock.anyObject());
        EasyMock.expectLastCall().anyTimes();
        bundleContext.addServiceListener((ServiceListener) EasyMock.anyObject());
        EasyMock.expectLastCall().anyTimes();
        EasyMock.expect(bundleContext.getBundles()).andReturn(new Bundle[] { systemBundle }).anyTimes();
        EasyMock.makeThreadSafe(bundleContext, true);
        EasyMock.makeThreadSafe(systemBundle, true);

        EasyMock.replay(bundleContext, systemBundle);

        String repositoryUrl = getClass().getClassLoader().getResource("test.xml").toExternalForm();

        RepositoryAdmin ra = new RepositoryAdminImpl(bundleContext, null);
        ra.addRepository(repositoryUrl);
        Application.setAdmin(ra);

        Server server = new Server();
        SocketConnector connector = new SocketConnector();
        connector.setPort(8282);
        server.addConnector(connector);
        Context context = new Context(server, "/");
        ServletAdaptor adaptor = new ServletAdaptor(); 
        ServletHolder holder = new ServletHolder(adaptor);
        holder.setInitParameter("javax.ws.rs.Application", Application.class.getName());
        context.addServlet(holder, "/*");
        context.start();
        server.start();

//        com.sun.jersey.api.client.Client1 client = com.sun.jersey.api.client.Client1.create();
//        com.sun.jersey.api.client.WebResource wr = client.resource("http://localhost:8282/rest/obr");
//        wr.path("repositories").post(new Admin.RepositoryRef("file:///Users/gnodet/.m2/repository/repository.xml"));

//        Thread.sleep(60000 * 5);

        RepositoryAdminProxy admin = new RepositoryAdminProxy(
                                                    "http://localhost:8282/obr/",
                                                    new SystemRepositoryImpl(bundleContext, null),
                                                    new LocalRepositoryImpl(bundleContext, null));

        Repository repo = admin.addRepository(repositoryUrl);
        assertNotNull(repo);
        assertEquals(repositoryUrl, repo.getURI());
        Resource[] res = repo.getResources();
        assertNotNull(res);
        assertNotNull(res[0]);
        assertNotNull(res[0].getSymbolicName());
        assertNotNull(res[0].getCapabilities());
        assertNotSame(0, res[0].getCapabilities().length);
        assertNotNull(res[0].getCapabilities()[0]);

        Resource[] res2 = admin.discoverResources("(&(symbolicname=" + res[0].getSymbolicName() + ")(version=" + res[0].getVersion() + "))");
        assertNotNull(res2);
        assertSame(res2[0], res[0]);

        Resolver resolver = admin.resolver();
        resolver.add(res[0]);
        resolver.resolve();

        server.stop();
    }

}
