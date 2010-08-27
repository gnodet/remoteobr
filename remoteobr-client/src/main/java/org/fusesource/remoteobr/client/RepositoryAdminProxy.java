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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.felix.bundlerepository.Capability;
import org.apache.felix.bundlerepository.DataModelHelper;
import org.apache.felix.bundlerepository.InterruptedResolutionException;
import org.apache.felix.bundlerepository.Reason;
import org.apache.felix.bundlerepository.Repository;
import org.apache.felix.bundlerepository.RepositoryAdmin;
import org.apache.felix.bundlerepository.Requirement;
import org.apache.felix.bundlerepository.Resolver;
import org.apache.felix.bundlerepository.Resource;
import org.apache.felix.bundlerepository.impl.*;
import org.kxml2.io.KXmlParser;
import org.osgi.framework.InvalidSyntaxException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * A Repository Admin delegating queries and repository loading to a remote server.
 */
public class RepositoryAdminProxy implements RepositoryAdmin {

    private final SystemRepositoryImpl system;
    private final LocalRepositoryImpl local;
    private final Map<String, RepositoryImpl> repositories = new HashMap<String, RepositoryImpl>();
    private final DataModelHelper helper = new DataModelHelperImpl();
    private final String remoteObrUrl;
    private static final String REQUIRED_RESOURCES = "required-resources";
    private static final String OPTIONAL_RESOURCES = "optional-resources";
    private static final String REASONS = "reasons";
    private static final String UNSATISFIED_REQUIREMENTS = "unsatisfied-requirements";
    private static final String REASON = "reason";

    public RepositoryAdminProxy(String remoteObrUrl, SystemRepositoryImpl system, LocalRepositoryImpl local) {
        this.remoteObrUrl = remoteObrUrl;
        this.system = system;
        this.local = local;
    }

    public Resource[] discoverResources(String filter) throws InvalidSyntaxException {
        // make sure the syntax is valid
        helper.filter(filter);
        // call remote server
        List<Resource> res = discoverResources(repositories.keySet(), filter);
        return res.toArray(new Resource[res.size()]);
    }

    public Resource[] discoverResources(Requirement[] requirements) {
        List<Resource> res = discoverResources(repositories.keySet(), requirements);
        return res.toArray(new Resource[res.size()]);
    }

    public Resolver resolver() {
        List<Repository> repositories = new ArrayList<Repository>();
        repositories.add(system);
        repositories.add(local);
        repositories.addAll(this.repositories.values());
        return resolver(repositories.toArray(new Repository[repositories.size()]));
    }

    public Resolver resolver(Repository[] repositories) {
        return new ResolverProxy(this, repositories);
    }

    public Repository addRepository(String uri) throws Exception {
        return addRepository(new URL(uri));
    }

    public Repository addRepository(URL url) throws Exception {
        return addRepository(url, Integer.MAX_VALUE);
    }

    public Repository addRepository(URL url, int hopCount) throws Exception {
        RepositoryImpl repository = remoteAddRepository(url);
        repositories.put(url.toExternalForm(), repository);
        hopCount--;
        if (hopCount > 0 && repository.getReferrals() != null) {
            for (int i = 0; i < repository.getReferrals().length; i++) {
                Referral referral = repository.getReferrals()[i];
                URL referralUrl = new URL(url, referral.getUrl());
                hopCount = (referral.getDepth() > hopCount) ? hopCount : referral.getDepth();
                addRepository(referralUrl, hopCount);
            }
        }
        return repository;
    }

    public boolean removeRepository(String uri) {
        return repositories.remove(uri) != null;
    }

    public Repository[] listRepositories() {
        return repositories.values().toArray(new Repository[repositories.size()]);
    }

    public Repository getSystemRepository() {
        return system;
    }

    public Repository getLocalRepository() {
        return local;
    }

    public DataModelHelper getHelper() {
        return helper;
    }

    public Resource loadResource(String id, String repository) {
        InputStream is = null;
        try {
            URLConnection con = open("resources?resId=" + id + "&repId=" + repository);
            is = con.getInputStream();
            return helper.readResource(new InputStreamReader(is));
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            close(is);
        }
    }

    public List<Resource> discoverResources(Collection<String> repositories, String filter) {
        InputStream is = null;
        try {
            URLConnection con = open("resources");
            con.setDoOutput(true);
            Writer w = new OutputStreamWriter(con.getOutputStream());
            XmlWriter xw = new XmlWriter(w);
            xw.element("discover");
            for (String repo : repositories) {
                xw.element("repository").text(repo).end();
            }
            xw.element("filter").text(filter).end();
            xw.end();
            w.close();
            is = con.getInputStream();
            return parseResourceRefs(new InputStreamReader(is), null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            close(is);
        }
    }

    public List<Resource> discoverResources(Collection<String> repositories, Requirement[] reqs) {
        InputStream is = null;
        try {
            URLConnection con = open("resources");
            con.setDoOutput(true);
            Writer w = new OutputStreamWriter(con.getOutputStream());
            XmlWriter xw = new XmlWriter(w);
            xw.element("discover");
            for (String repo : repositories) {
                xw.element("repository").text(repo).end();
            }
            for (Requirement req : reqs) {
                toXml(xw, req);
            }
            xw.end();
            w.close();
            is = con.getInputStream();
            return parseResourceRefs(new InputStreamReader(is), null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            close(is);
        }
    }

    public RepositoryImpl remoteAddRepository(URL url) {
        InputStream is = null;
        try {
            URLConnection con = open("repositories");
            con.setDoOutput(true);
            Writer w = new OutputStreamWriter(con.getOutputStream());
            XmlWriter xw = new XmlWriter(w);
            xw.element("repository").attribute("id", url.toExternalForm()).end();
            w.close();
            is = con.getInputStream();
            return parseRepository(new InputStreamReader(is), url.toExternalForm());
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            close(is);
        }
    }

    public ResolutionResults resolve(ResolutionParams params) {
        InputStream is = null;
        try {
            URLConnection con = open("resolver");
            con.setDoOutput(true);
            Writer w = new OutputStreamWriter(con.getOutputStream());
            XmlWriter xw = new XmlWriter(w);
            xw.element("resolve");
            xw.element("flags");
            xw.text(params.getFlags());
            xw.end();
            xw.element("repositories");
            for (String rep : params.getRepositories()) {
                xw.element("repository").attribute("id", rep).end();
            }
            xw.end();
            xw.element("added-resources");
            xw.text("");
            for (Resource res : params.getAddedResources()) {
                if (res instanceof ResourceProxy) {
                    xw.element("resource-ref").attribute("id", res.getId()).attribute("repository", ((ResourceProxy) res).getRepository()).end();
                } else {
                    helper.writeResource(res, w);
                }
            }
            xw.end();
            xw.element("added-requirements");
            xw.text("");
            for (Requirement req : params.getAddedRequirements()) {
                toXml(xw, req);
            }
            xw.end();
            xw.element("global-capabilities");
            xw.text("");
            for (Capability cap : params.getGlobalCapabilities()) {
                helper.writeCapability(cap, w);
            }
            xw.end();
            xw.element("local-resources");
            xw.text("");
            for (Resource res : params.getLocalResources()) {
                helper.writeResource(res, w);
            }
            xw.end();
            xw.end();
            w.close();
            is = con.getInputStream();
            for (;;) {
                if (Thread.interrupted()) {
                    throw new InterruptedResolutionException();
                }
                if (is.available() > 0) {
                    break;
                }
                Thread.sleep(1);
            }
            return parseResolution(new InputStreamReader(is));
        } catch (InterruptedResolutionException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            close(is);
        }
    }

    private static void toXml(XmlWriter w, Requirement requirement) throws IOException
    {
        w.element(RepositoryParser.REQUIRE)
            .attribute(RepositoryParser.NAME, requirement.getName())
            .attribute(RepositoryParser.FILTER, requirement.getFilter())
            .attribute(RepositoryParser.EXTEND, Boolean.toString(requirement.isExtend()))
            .attribute(RepositoryParser.MULTIPLE, Boolean.toString(requirement.isMultiple()))
            .attribute(RepositoryParser.OPTIONAL, Boolean.toString(requirement.isOptional()));
        if (requirement.getComment() != null) {
            w.text(requirement.getComment().trim());
        }
        w.end();
    }

    private URLConnection open(String url) throws IOException {
        URLConnection con = new URL(new URL(remoteObrUrl), url).openConnection();
        con.setRequestProperty("Content-Type", "application/xml");
        return con;
    }


    private void close(Closeable is) {
        if (is != null) {
            try {
                is.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    private static final String RESOURCES = "resources";
    private static final String RESOURCE = "resource";
    private static final String ID = "id";
    private static final String REPOSITORY = "repository";
    private static final String NAME = "name";
    private static final String LASTMODIFIED = "lastmodified";
    private static final String REFERRAL = "referral";
    private static final String DEPTH = "depth";
    private static final String URL = "url";
    private static final String RESOLUTION = "resolution";
    public static final String REQUIRE = "require";
    public static final String FILTER = "filter";
    public static final String EXTEND = "extend";
    public static final String MULTIPLE = "multiple";
    public static final String OPTIONAL = "optional";

    private ResolutionResults parseResolution(Reader r) throws Exception {
        XmlPullParser reader = new KXmlParser();
        reader.setInput(r);
        int event = reader.nextTag();
        if (event != XmlPullParser.START_TAG || !RESOLUTION.equals(reader.getName())) {
            throw new Exception("Expected element '" + RESOLUTION + "' at the root of the document");
        }
        return parseResolution(reader);
    }

    private ResolutionResults parseResolution(XmlPullParser reader) throws Exception {
        int event;
        ResolutionResults resolutionResults = new ResolutionResults();
        while ((event = reader.nextTag()) == XmlPullParser.START_TAG) {
            String element = reader.getName();
            if (REQUIRED_RESOURCES.equals(element)) {
                while ((event = reader.nextTag()) == XmlPullParser.START_TAG) {
                    element = reader.getName();
                    if (RESOURCE.equals(element)) {
                        Resource res = parseResourceRef(reader, null);
                        resolutionResults.getRequiredResources().add(res);
                    } else {
                        ignoreTag(reader);
                    }
                }
                // Sanity check
                sanityCheckEndElement(reader, event, REQUIRED_RESOURCES);
            } else if (OPTIONAL_RESOURCES.equals(element)) {
                while ((event = reader.nextTag()) == XmlPullParser.START_TAG) {
                    element = reader.getName();
                    if (RESOURCE.equals(element)) {
                        Resource res = parseResourceRef(reader, null);
                        resolutionResults.getOptionalResources().add(res);
                    } else {
                        ignoreTag(reader);
                    }
                }
                // Sanity check
                sanityCheckEndElement(reader, event, OPTIONAL_RESOURCES);
            } else if (REASONS.equals(element)) {

                // TODO
                ignoreTag(reader);

            } else if (UNSATISFIED_REQUIREMENTS.equals(element)) {
                while ((event = reader.nextTag()) == XmlPullParser.START_TAG) {
                    element = reader.getName();
                    if (REASON.equals(element)) {
                        Reason reason = parseReason(reader);
                        resolutionResults.getUnsatisfiedRequirements().add(reason);
                    } else {
                        ignoreTag(reader);
                    }
                }
                // Sanity check
                sanityCheckEndElement(reader, event, UNSATISFIED_REQUIREMENTS);
            } else {
                ignoreTag(reader);
            }
        }
        // Sanity check
        sanityCheckEndElement(reader, event, RESOLUTION);
        return resolutionResults;
    }

    private ReasonImpl parseReason(XmlPullParser reader) throws Exception {
        int event;
        Resource resource = null;
        Requirement requirement = null;
        while ((event = reader.nextTag()) == XmlPullParser.START_TAG) {
            String element = reader.getName();
            if (RESOURCE.equals(element)) {
                resource = new PullParser().parseResource(reader);
            } else if ("resource-ref".equals(element)) {
                resource = parseResourceRef(reader, null);
            } else if (REQUIRE.equals(element)) {
                requirement = parseRequire(reader);
            } else {
                ignoreTag(reader);
            }
        }
        // Sanity check
        sanityCheckEndElement(reader, event, REASON);
        return new ReasonImpl(resource, requirement);
    }

    private RepositoryImpl parseRepository(Reader r, String repositoryId) throws Exception {
        XmlPullParser reader = new KXmlParser();
        reader.setInput(r);
        int event = reader.nextTag();
        if (event != XmlPullParser.START_TAG || !REPOSITORY.equals(reader.getName()))
        {
            throw new Exception("Expected element '" + REPOSITORY + "' at the root of the document");
        }
        return parseRepository(reader, repositoryId);
    }

    private RepositoryImpl parseRepository(XmlPullParser reader, String repositoryId) throws Exception
    {
        RepositoryImpl repository = new RepositoryImpl();
        for (int i = 0, nb = reader.getAttributeCount(); i < nb; i++)
        {
            String name = reader.getAttributeName(i);
            String value = reader.getAttributeValue(i);
            if (NAME.equals(name))
            {
                repository.setName(value);
            }
            if (URL.equals(name))
            {
                Method mth = repository.getClass().getDeclaredMethod("setURI", String.class);
                mth.setAccessible(true);
                mth.invoke(repository, value);
//                repository.setURI(value);
            }
            else if (LASTMODIFIED.equals(name))
            {
                repository.setLastModified(value);
            }
        }
        int event;
        while ((event = reader.nextTag()) == XmlPullParser.START_TAG)
        {
            String element = reader.getName();
            if (REFERRAL.equals(element))
            {
                Referral referral = parseReferral(reader);
                repository.addReferral(referral);
            }
            else if (RESOURCE.equals(element))
            {
                Resource res = parseResourceRef(reader, repositoryId);
                repository.addResource(res);
            }
            else
            {
                ignoreTag(reader);
            }
        }
        // Sanity check
        sanityCheckEndElement(reader, event, REPOSITORY);
        return repository;
    }

    private Referral parseReferral(XmlPullParser reader) throws Exception
    {
        Referral referral = new Referral();
        for (int i = 0, nb = reader.getAttributeCount(); i < nb; i++)
        {
            String name = reader.getAttributeName(i);
            String value = reader.getAttributeValue(i);
            if (DEPTH.equals(name))
            {
                referral.setDepth(value);
            }
            else if (URL.equals(name))
            {
                referral.setUrl(value);
            }
        }
        sanityCheckEndElement(reader, reader.nextTag(), REFERRAL);
        return referral;
    }

    private List<Resource> parseResourceRefs(Reader r, String repositoryId) throws Exception
    {
        XmlPullParser reader = new KXmlParser();
        reader.setInput(r);
        int event = reader.nextTag();
        if (event != XmlPullParser.START_TAG || !RESOURCES.equals(reader.getName()))
        {
            throw new Exception("Expected element '" + RESOURCES + "'");
        }
        return parseResourceRefs(reader, repositoryId);
    }

    private List<Resource> parseResourceRefs(XmlPullParser reader, String repositoryId) throws Exception {
        List<Resource> refs = new ArrayList<Resource>();
        int event;
        while ((event = reader.nextTag()) == XmlPullParser.START_TAG)
        {
            String element = reader.getName();
            if (RESOURCE.equals(element))
            {
                Resource ref = parseResourceRef(reader, repositoryId);
                refs.add(ref);
            }
            else
            {
                ignoreTag(reader);
            }
        }
        // Sanity check
        sanityCheckEndElement(reader, event, RESOURCES);
        return refs;
    }

    private Resource parseResourceRef(XmlPullParser reader, String repositoryId) throws Exception
    {
        Resource resource = null;
        String id = reader.getAttributeValue(null, ID);
        if (repositoryId == null) {
            String repository = reader.getAttributeValue(null, REPOSITORY);
            RepositoryImpl repo = repositories.get(repository);
            if (repo == null) {
                throw new IllegalStateException("Unknown repository '" + repository + "'");
            }
            for (Resource res : repo.getResources()) {
                if (res.getId().equals(id)) {
                    resource = res;
                    break;
                }
            }
            if (resource == null) {
                throw new IllegalStateException("Unknown resource '" + id + "' in repository '" + repository + "'");
            }
        } else {
            resource = new ResourceProxy(this, id, repositoryId);
        }
        sanityCheckEndElement(reader, reader.nextTag(), RESOURCE);
        return resource;
    }

    public RequirementImpl parseRequire(XmlPullParser reader) throws Exception
    {
        RequirementImpl requirement = new RequirementImpl();
        for (int i = 0, nb = reader.getAttributeCount(); i < nb; i++)
        {
            String name = reader.getAttributeName(i);
            String value = reader.getAttributeValue(i);
            if (NAME.equals(name))
            {
                requirement.setName(value);
            }
            else if (FILTER.equals(name))
            {
                requirement.setFilter(value);
            }
            else if (EXTEND.equals(name))
            {
                requirement.setExtend(Boolean.parseBoolean(value));
            }
            else if (MULTIPLE.equals(name))
            {
                requirement.setMultiple(Boolean.parseBoolean(value));
            }
            else if (OPTIONAL.equals(name))
            {
                requirement.setOptional(Boolean.parseBoolean(value));
            }
        }
        int event;
        StringBuffer sb = null;
        while ((event = reader.next()) != XmlPullParser.END_TAG)
        {
            switch (event)
            {
                case XmlPullParser.START_TAG:
                    throw new Exception("Unexpected element inside <require/> element");
                case XmlPullParser.TEXT:
                    if (sb == null)
                    {
                        sb = new StringBuffer();
                    }
                    sb.append(reader.getText());
                    break;
            }
        }
        if (sb != null)
        {
            requirement.addText(sb.toString());
        }
        // Sanity check
        sanityCheckEndElement(reader, event, REQUIRE);
        return requirement;
    }

    private void ignoreTag(XmlPullParser reader) throws IOException, XmlPullParserException {
        int level = 1;
        while (level > 0)
        {
            int eventType = reader.next();
            if (eventType == XmlPullParser.START_TAG)
            {
                level++;
            }
            else if (eventType == XmlPullParser.END_TAG)
            {
                level--;
            }
        }
    }

    private void sanityCheckEndElement(XmlPullParser reader, int event, String element)
    {
        if (event != XmlPullParser.END_TAG || !element.equals(reader.getName()))
        {
            throw new IllegalStateException("Unexpected state while finishing element " + element);
        }
    }

}
