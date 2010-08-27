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
import org.apache.felix.bundlerepository.Requirement;
import org.apache.felix.bundlerepository.Resolver;
import org.apache.felix.bundlerepository.Resource;
import org.apache.felix.bundlerepository.impl.LocalRepositoryImpl;
import org.apache.felix.bundlerepository.impl.RepositoryAdminImpl;
import org.apache.felix.bundlerepository.impl.SystemRepositoryImpl;
import org.easymock.EasyMock;
import org.fusesource.remoteobr.client.RepositoryAdminProxy;
import org.fusesource.remoteobr.server.Application;
import org.junit.After;
import org.junit.Before;
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

    protected RepositoryAdmin admin;
    protected Server server;

    @Test
    public void testRest() throws Exception {

        Repository repo = admin.addRepository(getClass().getResource("/repo_for_resolvertest.xml"));
        assertNotNull(repo);
        assertEquals(getClass().getResource("/repo_for_resolvertest.xml").toExternalForm(), repo.getURI());
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

    @Test
    public void testReferral1() throws Exception
    {

        admin.addRepository(getClass().getResource("/repo_for_resolvertest.xml"));

        Resolver resolver = admin.resolver();

        Resource[] discoverResources = admin.discoverResources("(symbolicname=org.apache.felix.test*)");
        assertNotNull(discoverResources);
        assertEquals(1, discoverResources.length);

        resolver.add(discoverResources[0]);
        assertTrue(resolver.resolve());
    }

    @Test
    public void testMatchingReq() throws Exception
    {
        admin.addRepository(getClass().getResource("/repo_for_resolvertest.xml"));

        Resource[] res = admin.discoverResources(
            new Requirement[] { admin.getHelper().requirement(
                "package", "(package=org.apache.felix.test.osgi)") });
        assertNotNull(res);
        assertEquals(1, res.length);
    }

    @Test
    public void testResolveReq() throws Exception
    {
        admin.addRepository(getClass().getResource("/repo_for_resolvertest.xml"));

        Resolver resolver = admin.resolver();
        resolver.add(admin.getHelper().requirement("package", "(package=org.apache.felix.test.osgi)"));
        assertTrue(resolver.resolve());
    }

    @Test
    public void testResolveInterrupt() throws Exception
    {
        admin.addRepository(getClass().getResource("/repo_for_resolvertest.xml"));

        Resolver resolver = admin.resolver();
        resolver.add(admin.getHelper().requirement("package", "(package=org.apache.felix.test.osgi)"));

        Thread.currentThread().interrupt();
        try
        {
            resolver.resolve();
            fail("An exception should have been thrown");
        }
        catch (org.apache.felix.bundlerepository.InterruptedResolutionException e)
        {
            // ok
        }
    }

    @Test
    public void testOptionalResolution() throws Exception
    {
        admin.addRepository(getClass().getResource("/repo_for_optional_resources.xml"));

        Resolver resolver = admin.resolver();
        resolver.add(admin.getHelper().requirement("bundle", "(symbolicname=res1)"));

        assertTrue(resolver.resolve());
        assertEquals(1, resolver.getRequiredResources().length);
        assertEquals(2, resolver.getOptionalResources().length);
    }

    @Test
    public void testMandatoryPackages() throws Exception
    {
        admin.addRepository(getClass().getResource("/repo_for_mandatory.xml"));

        Resolver resolver = admin.resolver();
        resolver.add(admin.getHelper().requirement("bundle", "(symbolicname=res2)"));
        assertFalse(resolver.resolve());

        resolver = admin.resolver();
        resolver.add(admin.getHelper().requirement("bundle", "(symbolicname=res3)"));
        assertTrue(resolver.resolve());

        resolver = admin.resolver();
        resolver.add(admin.getHelper().requirement("bundle", "(symbolicname=res4)"));
        assertFalse(resolver.resolve());

    }


    @Before
    public void setUp() throws Exception {
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

        RepositoryAdmin ra = new RepositoryAdminImpl(bundleContext, null);
        Application.setAdmin(ra);

        server = new Server();
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

        admin = new RepositoryAdminProxy("http://localhost:8282/obr/",
                                         new SystemRepositoryImpl(bundleContext, null),
                                         new LocalRepositoryImpl(bundleContext, null));
    }

    @After
    public void tearDown() throws Exception {
        server.stop();
    }

}
