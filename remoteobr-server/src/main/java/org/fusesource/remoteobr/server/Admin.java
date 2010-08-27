package org.fusesource.remoteobr.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import org.apache.felix.bundlerepository.Capability;
import org.apache.felix.bundlerepository.Reason;
import org.apache.felix.bundlerepository.Repository;
import org.apache.felix.bundlerepository.RepositoryAdmin;
import org.apache.felix.bundlerepository.Requirement;
import org.apache.felix.bundlerepository.Resolver;
import org.apache.felix.bundlerepository.Resource;
import org.apache.felix.bundlerepository.impl.DataModelHelperImpl;
import org.apache.felix.bundlerepository.impl.PullParser;
import org.apache.felix.bundlerepository.impl.Referral;
import org.apache.felix.bundlerepository.impl.RepositoryImpl;
import org.apache.felix.bundlerepository.impl.RepositoryParser;
import org.apache.felix.bundlerepository.impl.ResourceImpl;
import org.apache.felix.bundlerepository.impl.XmlWriter;
import org.apache.felix.utils.collections.MapToDictionary;
import org.apache.felix.utils.filter.FilterImpl;
import org.kxml2.io.KXmlParser;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;


@Path("/obr")
public class Admin {

    private static final String RESOURCE = "resource";
    private static final String RESOURCES = "resources";
    private static final String ID = "id";
    private static final String REPOSITORY = "repository";
    private static final String REPOSITORIES = "repositories";
    private static final String NAME = "name";
    private static final String LASTMODIFIED = "lastmodified";
    private static final String REFERRAL = "referral";
    private static final String DEPTH = "depth";
    private static final String URL = "url";
    private static final String DISCOVER = "discover";
    private static final String RESOLVE = "resolve";
    private static final String RESOLUTION = "resolution";

    private final RepositoryAdmin admin;

    public Admin(RepositoryAdmin admin) {
        this.admin = admin;
    }

    @GET
    @Path("repositories")
    @Produces("application/xml")
    public RepositoryRefs getRepositories() {
        List<RepositoryRef> repos = new ArrayList<RepositoryRef>();
        for (Repository repo : admin.listRepositories()) {
            repos.add(new RepositoryRef(repo.getURI()));
        }
        return new RepositoryRefs(repos);
    }

    @GET
    @Path("repositories/{repId}")
    @Produces("application/xml")
    public Repository getRepository(@PathParam("repId") String repId) {
        for (Repository repo : admin.listRepositories()) {
            if (repo.getURI().equals(repId)) {
                return repo;
            }
        }
        throw new WebApplicationException(new IllegalArgumentException("Unknown repository: " + repId), Response.Status.NOT_FOUND);
    }

    @POST
    @Path("repositories")
    @Consumes("application/xml")
    public Repository addRepository(RepositoryRef ref) throws Exception {
        return admin.addRepository(ref.getId());
    }

    @DELETE
    @Path("repositories/{repId}")
    @Consumes("application/xml")
    public void deleteRepository(@PathParam("repId") String repId) {
        admin.removeRepository(repId);
    }

    @GET
    @Path("repositories/{repId}/resources")
    @Produces("application/xml")
    public ResourceRefs getResources(@PathParam("repId") String repId) {
        for (Repository repo : admin.listRepositories()) {
            if (repo.getURI().equals(repId)) {
                List<ResourceRef> res = new ArrayList<ResourceRef>();
                for (org.apache.felix.bundlerepository.Resource r : repo.getResources()) {
                    res.add(new ResourceRef(repo.getURI(), r.getId()));
                }
                return new ResourceRefs(res);
            }
        }
        throw new WebApplicationException(new IllegalArgumentException("Unknown repository: " + repId), Response.Status.NOT_FOUND);
    }

    @GET
    @Path("resources")
    @Produces("application/xml")
    public Resource getResource(@QueryParam("repId") String repId, @QueryParam("resId") String resId) {
        for (Repository repo : admin.listRepositories()) {
            if (repo.getURI().equals(repId)) {
                for (Resource r : repo.getResources()) {
                    if (r.getId().equals(resId)) {
                        return r;
                    }
                }
                throw new WebApplicationException(new IllegalArgumentException("Unknown resource: " + resId), Response.Status.NOT_FOUND);
            }
        }
        throw new WebApplicationException(new IllegalArgumentException("Unknown repository: " + repId), Response.Status.NOT_FOUND);
    }

    @POST
    @Path("resources")
    @Consumes("application/xml")
    @Produces("application/xml")
    public ResourceRefs discover(Discover params) {
        List<ResourceRef> resources = new ArrayList<ResourceRef>();
        Filter filter = createFilter(params.getFilter());
        MapToDictionary dict = new MapToDictionary(null);
        for (Repository repo : admin.listRepositories()) {
            if (params.getRepositories().contains(new RepositoryRef(repo.getURI()))) {
                for (Resource r : repo.getResources()) {
                    dict.setSourceMap(r.getProperties());
                    boolean match = true;
                    if (filter != null) {
                        match = filter.match(dict);
                    }
                    for (Iterator<Requirement> it = params.getRequirements().iterator(); match && it.hasNext();) {
                        Requirement req = it.next();
                        boolean reqMatch = false;
                        Capability[] caps = r.getCapabilities();
                        for (int capIdx = 0; (caps != null) && (capIdx < caps.length); capIdx++) {
                            if (req.isSatisfied(caps[capIdx])) {
                                reqMatch = true;
                                break;
                            }
                        }
                        match = reqMatch;
                        if (!match) {
                            break;
                        }
                    }
                    if (match) {
                        resources.add(new ResourceRef(repo.getURI(), r.getId()));
                    }
                }
            }
        }
        return new ResourceRefs(resources);
    }

    @POST
    @Path("resolver")
    @Consumes("application/xml")
    @Produces("application/xml")
    public Resolution resolve(Resolve params) {
        List<Repository> repos = new ArrayList<Repository>();
        RepositoryImpl localRepo = new RepositoryImpl(params.getLocalResources().toArray(new Resource[params.getLocalResources().size()]));
        repos.add(localRepo);
        Repository[] allRepos = admin.listRepositories();
        for (String r : params.getRepositories()) {
            for (Repository rep : allRepos) {
                if (rep.getURI().equals(r)) {
                    repos.add(rep);
                }
            }
        }
        Resolver resolver = admin.resolver(repos.toArray(new Repository[repos.size()]));
        for (Requirement req : params.getAddedRequirements()) {
            resolver.add(req);
        }
        for (Resource res : params.getAddedResources()) {
            resolver.add(res);
        }
        Resolution res = new Resolution();
        if (resolver.resolve(params.getFlags())) {
            for (Resource resource : resolver.getRequiredResources()) {
                ResourceImpl r = (ResourceImpl) resource;
                ResourceRef ref = new ResourceRef(r.getRepository().getURI(), r.getId());
                res.getRequiredResources().add(ref);
                List<ReasonRef> reasons = new ArrayList<ReasonRef>();
                for (Reason reason : resolver.getReason(r)) {
                    reasons.add(getReasonRef(reason));
                }
                res.getReasons().put(ref, reasons);
            }
            for (Resource resource : resolver.getOptionalResources()) {
                ResourceImpl r = (ResourceImpl) resource;
                res.getOptionalResources().add(new ResourceRef(r.getRepository().getURI(), r.getId()));
            }
        } else {
            for (Reason reason : resolver.getUnsatisfiedRequirements()) {
                res.getUnsatisfiedRequirements().add(getReasonRef(reason));
            }
        }
        return res;
    }

    private ReasonRef getReasonRef(Reason reason) {
        ResourceImpl r = (ResourceImpl) reason.getResource();
        ResourceRef ref = new ResourceRef(r.getRepository() != null ? r.getRepository().getURI() : null, r.getId());
        return new ReasonRef(reason.getRequirement(), ref);
    }

    private Filter createFilter(String filter) {
        try {
            return filter != null ? FilterImpl.newInstance(filter) : null;
        } catch (InvalidSyntaxException e) {
            return null;
        }
    }

    private static void writeResourceRefs(XmlWriter w, ResourceRefs refs) throws IOException {
        w.element(RESOURCES);
        for (ResourceRef ref : refs.getResources()) {
            writeResourceRef(w, ref);
        }
        w.end();
    }
    private static void writeResourceRef(XmlWriter w, ResourceRef resourceRef) throws IOException
    {
        w.element(RESOURCE);
        w.attribute(ID, resourceRef.getId());
        if (resourceRef.getRepository() != null) {
            w.attribute(REPOSITORY, resourceRef.getRepository());
        }
        w.end();
    }

    private static void writeRequirement(XmlWriter w, Requirement requirement) throws IOException
    {
        w.element(RepositoryParser.REQUIRE)
            .attribute(RepositoryParser.NAME, requirement.getName())
            .attribute(RepositoryParser.FILTER, requirement.getFilter())
            .attribute(RepositoryParser.EXTEND, Boolean.toString(requirement.isExtend()))
            .attribute(RepositoryParser.MULTIPLE, Boolean.toString(requirement.isMultiple()))
            .attribute(RepositoryParser.OPTIONAL, Boolean.toString(requirement.isOptional()))
            .text(requirement.getComment().trim())
            .end();
    }

    private static void writeResolution(XmlWriter w, Resolution resolution) throws IOException {
        w.element(RESOLUTION);
        w.element("required-resources");
        for (ResourceRef ref : resolution.getRequiredResources()) {
            writeResourceRef(w, ref);
        }
        w.end();
        w.element("optional-resources");
        for (ResourceRef ref : resolution.getOptionalResources()) {
            writeResourceRef(w, ref);
        }
        w.end();
        w.element("reasons");
        // TODO
        w.end();
        w.element("unsatisfied-requirements");
        for (ReasonRef reason : resolution.getUnsatisfiedRequirements()) {
            w.element("reason");
            writeResourceRef(w, reason.getResource());
            writeRequirement(w, reason.getRequirement());
            w.end();
        }
        w.end();
        w.end();
    }

    private static void writeRepositoryRefs(XmlWriter w, RepositoryRefs refs) throws IOException {
        w.element(REPOSITORIES);
        for (RepositoryRef repositoryRef : refs.getRepositories()) {
            writeRepositoryRef(w, repositoryRef);
        }
        w.end();
    }

    private static void writeRepositoryRef(XmlWriter w, RepositoryRef repositoryRef) throws IOException {
        w.element(REPOSITORY);
        w.attribute(ID, repositoryRef.getId());
        w.end();
    }

    private static void writeRepository(XmlWriter w, Repository repository) throws IOException {
        w.element(REPOSITORY);
        w.attribute(NAME, repository.getName());
        w.attribute(URL, repository.getURI());
        w.attribute(LASTMODIFIED, repository.getLastModified());
        if (repository instanceof RepositoryImpl) {
            Referral[] referrals = ((RepositoryImpl) repository).getReferrals();
            if (referrals != null) {
                for (Referral referral : referrals) {
                    w.element(REFERRAL);
                    w.attribute(DEPTH, referral.getDepth());
                    w.attribute(URL, referral.getUrl());
                    w.end();
                }
            }
        }
        for (Resource res : repository.getResources()) {
            writeResourceRef(w, new ResourceRef(null, res.getId()));
        }
        w.end();
    }

    private static void sanityCheckEndElement(XmlPullParser reader, int event, String element) {
        if (event != XmlPullParser.END_TAG || !element.equals(reader.getName())) {
            throw new IllegalStateException("Unexpected state while finishing element " + element);
        }
    }

    public static abstract class AbstractProvider<T> implements MessageBodyWriter<T>, MessageBodyReader<T> {

        public boolean isWriteable(Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
            return  getMessageClass().isAssignableFrom(aClass)
                        && MediaType.valueOf(MediaType.APPLICATION_XML).equals(mediaType);
        }

        public long getSize(T t, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
            return -1;
        }

        public void writeTo(T t, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> stringObjectMultivaluedMap, OutputStream outputStream) throws IOException, WebApplicationException {
            Writer osw = new OutputStreamWriter(outputStream);
            XmlWriter w = new XmlWriter(osw);
            writeXml(w, t);
            osw.flush();
        }

        public boolean isReadable(Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
            return  getMessageClass().isAssignableFrom(aClass)
                        && MediaType.valueOf(MediaType.APPLICATION_XML).equals(mediaType);
        }

        public T readFrom(Class<T> t, Type type, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> stringStringMultivaluedMap, InputStream inputStream) throws IOException, WebApplicationException {
            try {
                KXmlParser parser = new KXmlParser();
                parser.setInput(new InputStreamReader(inputStream));
                return parseXml(parser);
            } catch (Exception e) {
                throw new IOException(e);
            }
        }

        protected abstract Class<T> getMessageClass();

        protected void writeXml(XmlWriter w, T t) throws IOException {
            throw new UnsupportedOperationException();
        }

        protected T parseXml(KXmlParser parser) throws IOException, XmlPullParserException {
            throw new UnsupportedOperationException();
        }
    }

    @Provider
    public static class ResolveProvider implements MessageBodyReader<Resolve> {

        public boolean isReadable(Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
            return Resolve.class.isAssignableFrom(aClass);
        }

        public Resolve readFrom(Class<Resolve> repositoryRefClass, Type type, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> stringStringMultivaluedMap, InputStream inputStream) throws IOException, WebApplicationException {
            try {
                KXmlParser parser = new KXmlParser();
                parser.setInput(new InputStreamReader(inputStream));
                if (parser.nextTag() != KXmlParser.START_TAG || !parser.getName().equals(RESOLVE)) {
                    throw new IOException("Expected tag '" + RESOLVE + "'");
                }
                Resolve params = new Resolve();
                while (parser.nextTag() == KXmlParser.START_TAG) {
                    if ("flags".equals(parser.getName())) {
                        params.setFlags(Integer.parseInt(parser.nextText()));
                    } else if (REPOSITORIES.equals(parser.getName())) {
                        while (parser.nextTag() == KXmlParser.START_TAG && REPOSITORY.equals(parser.getName())) {
                            String id = parser.getAttributeValue(null, ID);
                            params.getRepositories().add(id);
                        }
                        parser.nextTag();
                    } else if ("added-resources".equals(parser.getName())) {
                        while (parser.nextTag() == KXmlParser.START_TAG && RESOURCE.equals(parser.getName())) {
                            Resource res = new PullParser().parseResource(parser);
                            params.getAddedResources().add(res);
                        }
                    } else if ("added-requirements".equals(parser.getName())) {
                        while (parser.nextTag() == KXmlParser.START_TAG && RepositoryParser.REQUIRE.equals(parser.getName())) {
                            Requirement req = new PullParser().parseRequire(parser);
                            params.getAddedRequirements().add(req);
                        }
                    } else if ("global-capabilities".equals(parser.getName())) {
                        while (parser.nextTag() == KXmlParser.START_TAG && RepositoryParser.CAPABILITY.equals(parser.getName())) {
                            Capability cap = new PullParser().parseCapability(parser);
                            params.getGlobalCapabilities().add(cap);
                        }
                    } else if ("local-resources".equals(parser.getName())) {
                        while (parser.nextTag() == KXmlParser.START_TAG && RESOURCE.equals(parser.getName())) {
                            Resource res = new PullParser().parseResource(parser);
                            params.getLocalResources().add(res);
                        }
                    }
                }
                return params;
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
    }

    @Provider
    public static class ResolutionProvider extends AbstractProvider<Resolution> {
        @Override
        protected Class<Resolution> getMessageClass() {
            return Resolution.class;
        }
        @Override
        protected void writeXml(XmlWriter w, Resolution resolution) throws IOException {
            writeResolution(w, resolution);
        }
    }

    @Provider
    public static class ResourceProvider extends AbstractProvider<Resource> {
        @Override
        protected Class<Resource> getMessageClass() {
            return Resource.class;  //To change body of implemented methods use File | Settings | File Templates.
        }
        public void writeTo(Resource resource, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> stringObjectMultivaluedMap, OutputStream outputStream) throws IOException, WebApplicationException {
            Writer w = new OutputStreamWriter(outputStream);
            new DataModelHelperImpl().writeResource(resource, w);
            w.flush();
        }
    }

    @Provider
    public static class ResourceRefsProvider extends AbstractProvider<ResourceRefs> {
        @Override
        protected Class<ResourceRefs> getMessageClass() {
            return ResourceRefs.class;
        }
        @Override
        protected void writeXml(XmlWriter w, ResourceRefs resourceRefs) throws IOException {
            writeResourceRefs(w, resourceRefs);
        }
    }

    @Provider
    public static class ResourceRefProvider extends AbstractProvider<ResourceRef> {
        @Override
        protected Class<ResourceRef> getMessageClass() {
            return ResourceRef.class;
        }
        @Override
        protected void writeXml(XmlWriter w, ResourceRef resourceRef) throws IOException {
            writeResourceRef(w, resourceRef);
        }
    }

    @Provider
    public static class RepositoryRefProvider extends AbstractProvider<RepositoryRef> {
        @Override
        protected Class<RepositoryRef> getMessageClass() {
            return RepositoryRef.class;
        }
        @Override
        protected void writeXml(XmlWriter w, RepositoryRef repositoryRef) throws IOException {
            writeRepositoryRef(w, repositoryRef);
        }
        @Override
        protected RepositoryRef parseXml(KXmlParser parser) throws IOException, XmlPullParserException {
            if (parser.nextTag() != KXmlParser.START_TAG || !parser.getName().equals(REPOSITORY)) {
                throw new IOException("Expected tag '" + REPOSITORY + "'");
            }
            String id = parser.getAttributeValue(null, ID);
            if (id == null) {
                throw new IllegalStateException("Missing attribute " + ID);
            }
            return new RepositoryRef(id);
        }
    }

    @Provider
    public static class RepositoryRefsProvider extends AbstractProvider<RepositoryRefs> {
        @Override
        protected Class<RepositoryRefs> getMessageClass() {
            return RepositoryRefs.class;
        }
        @Override
        protected void writeXml(XmlWriter w, RepositoryRefs repositoryRefs) throws IOException {
            writeRepositoryRefs(w, repositoryRefs);
        }
    }

    @Provider
    public static class RepositoryProvider extends AbstractProvider<Repository> {
        @Override
        protected Class<Repository> getMessageClass() {
            return Repository.class;
        }
        @Override
        protected void writeXml(XmlWriter w, Repository repository) throws IOException {
            writeRepository(w, repository);
        }
    }

    @Provider
    public static class DiscoverProvider extends AbstractProvider<Discover> {
        @Override
        protected Class<Discover> getMessageClass() {
            return Discover.class;
        }
        @Override
        protected Discover parseXml(KXmlParser parser) throws IOException, XmlPullParserException {
            if (parser.nextTag() != KXmlParser.START_TAG || !parser.getName().equals(DISCOVER)) {
                throw new IOException("Expected tag '" + DISCOVER + "'");
            }
            List<RepositoryRef> repos = new ArrayList<RepositoryRef>();
            String filter = null;
            List<Requirement> reqs = new ArrayList<Requirement>();
            while (parser.nextTag() == KXmlParser.START_TAG) {
                if (REPOSITORY.equals(parser.getName())) {
                    repos.add(new RepositoryRef(parser.nextText()));
                } else if (RepositoryParser.FILTER.equals(parser.getName())) {
                    filter = parser.nextText();
                } else if (RepositoryParser.REQUIRE.equals(parser.getName())) {
                    try {
                        reqs.add(new PullParser().parseRequire(parser));
                    } catch (IOException e) {
                        throw e;
                    } catch (XmlPullParserException e) {
                        throw e;
                    } catch (Exception e) {
                        throw (IOException) new IOException().initCause(e);
                    }
                }
            }
            sanityCheckEndElement(parser, parser.getEventType(), DISCOVER);
            return new Discover(repos, reqs, filter);
        }
    }

    public static class Resolve {

        private final List<Resource> addedResources = new ArrayList<Resource>();
        private final List<Requirement> addedRequirements = new ArrayList<Requirement>();
        private final List<Capability> globalCapabilities = new ArrayList<Capability>();
        private final List<String> repositories = new ArrayList<String>();
        private final List<Resource> localResources = new ArrayList<Resource>();
        private int flags;

        public List<Resource> getAddedResources() {
            return addedResources;
        }

        public List<Requirement> getAddedRequirements() {
            return addedRequirements;
        }

        public List<Capability> getGlobalCapabilities() {
            return globalCapabilities;
        }

        public List<String> getRepositories() {
            return repositories;
        }

        public List<Resource> getLocalResources() {
            return localResources;
        }

        public int getFlags() {
            return flags;
        }

        public void setFlags(int flags) {
            this.flags = flags;
        }
    }

    public static class ReasonRef {
        private final Requirement requirement;
        private final ResourceRef resource;

        public ReasonRef(Requirement requirement, ResourceRef resource) {
            this.requirement = requirement;
            this.resource = resource;
        }

        public Requirement getRequirement() {
            return requirement;
        }

        public ResourceRef getResource() {
            return resource;
        }
    }

    public static class Resolution {

        private final List<ResourceRef> requiredResources = new ArrayList<ResourceRef>();
        private final List<ResourceRef> optionalResources = new ArrayList<ResourceRef>();
        private final Map<ResourceRef, List<ReasonRef>> reasons = new LinkedHashMap<ResourceRef, List<ReasonRef>>();
        private final List<ReasonRef> unsatisfiedRequirements = new ArrayList<ReasonRef>();

        public List<ResourceRef> getRequiredResources() {
            return requiredResources;
        }

        public List<ResourceRef> getOptionalResources() {
            return optionalResources;
        }

        public Map<ResourceRef, List<ReasonRef>> getReasons() {
            return reasons;
        }

        public List<ReasonRef> getUnsatisfiedRequirements() {
            return unsatisfiedRequirements;
        }

    }

    public static class Discover {

        private final List<RepositoryRef> repositories;
        private final List<Requirement> requirements;
        private final String filter;

        public Discover(List<RepositoryRef> repositories, List<Requirement> requirements, String filter) {
            this.repositories = repositories;
            this.requirements = requirements;
            this.filter = filter;
        }

        public List<RepositoryRef> getRepositories() {
            return repositories;
        }

        public List<Requirement> getRequirements() {
            return requirements;
        }

        public String getFilter() {
            return filter;
        }

    }

    public static class ResourceRefs {

        private final List<ResourceRef> resources;

        public ResourceRefs(List<ResourceRef> resources) {
            this.resources = resources;
        }

        public List<ResourceRef> getResources() {
            return resources;
        }
    }

    public static class ResourceRef {

        private final String id;
        private final String repository;

        public ResourceRef(String repository, String id) {
            this.id = id;
            this.repository = repository;
        }

        public String getId() {
            return id;
        }

        public String getRepository() {
            return repository;
        }

    }

    public static class RepositoryRefs {

        private final List<RepositoryRef> repositories;

        public RepositoryRefs(List<RepositoryRef> repositories) {
            this.repositories = repositories;
        }

        public List<RepositoryRef> getRepositories() {
            return repositories;
        }
    }

    public static class RepositoryRef {

        private final String id;

        public RepositoryRef(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            RepositoryRef that = (RepositoryRef) o;

            if (id != null ? !id.equals(that.id) : that.id != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return id != null ? id.hashCode() : 0;
        }
    }

}
