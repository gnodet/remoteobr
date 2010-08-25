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
        for (org.apache.felix.bundlerepository.Repository repo : admin.listRepositories()) {
            repos.add(new RepositoryRef(repo.getURI()));
        }
        return new RepositoryRefs(repos);
    }

    @GET
    @Path("repositories/{repId}")
    @Produces("application/xml")
    public Repository getRepository(@PathParam("repId") String repId) {
        for (org.apache.felix.bundlerepository.Repository repo : admin.listRepositories()) {
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
        for (org.apache.felix.bundlerepository.Repository repo : admin.listRepositories()) {
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
        resolver.resolve(params.getFlags());
        Resolution res = new Resolution();
        res.setRequiredResources(new ArrayList<ResourceRef>());
        for (Resource resource : resolver.getRequiredResources()) {
            ResourceImpl r = (ResourceImpl) resource;
            res.getRequiredResources().add(new ResourceRef(r.getRepository().getURI(), r.getId()));
        }
        res.setOptionalResources(new ArrayList<ResourceRef>());
        for (Resource resource : resolver.getOptionalResources()) {
            ResourceImpl r = (ResourceImpl) resource;
            res.getOptionalResources().add(new ResourceRef(r.getRepository().getURI(), r.getId()));
        }
        res.setUnsatisfiedRequirements(new ArrayList<Reason>());
        for (Reason reason : resolver.getUnsatisfiedRequirements()) {
            res.getUnsatisfiedRequirements().add(reason);
        }
        return res;
    }

    private Filter createFilter(String filter) {
        try {
            return filter != null ? FilterImpl.newInstance(filter) : null;
        } catch (InvalidSyntaxException e) {
            return null;
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
                    if (REPOSITORIES.equals(parser.getName())) {
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
                    } else if ("local-resources".equals(parser.getName())) {
                        while (parser.nextTag() == KXmlParser.START_TAG && RESOURCE.equals(parser.getName())) {
                            Resource res = new PullParser().parseResource(parser);
                            params.getLocalResources().add(res);
                        }
                    }
                }
                // TODO
                return params;
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
    }

    @Provider
    public static class ResolutionProvider implements MessageBodyWriter<Resolution> {
        public boolean isWriteable(Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
            return Resolution.class.isAssignableFrom(aClass);
        }

        public long getSize(Resolution resolution, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
            return -1;
        }

        public void writeTo(Resolution resolution, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> stringObjectMultivaluedMap, OutputStream outputStream) throws IOException, WebApplicationException {
            Writer osw = new OutputStreamWriter(outputStream);
            XmlWriter w = new XmlWriter(osw);
            w.element(RESOLUTION);
            // TODO
            w.end();
            osw.flush();
        }
    }

    @Provider
    public static class ResourceProvider implements MessageBodyWriter<Resource> {

        public boolean isWriteable(Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
            return Resource.class.isAssignableFrom(aClass);
        }

        public long getSize(Resource resource, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
            return -1;
        }

        public void writeTo(Resource resource, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> stringObjectMultivaluedMap, OutputStream outputStream) throws IOException, WebApplicationException {
            Writer w = new OutputStreamWriter(outputStream);
            new DataModelHelperImpl().writeResource(resource, w);
            w.flush();
        }
    }

    @Provider
    public static class ResourceRefsProvider implements MessageBodyWriter<ResourceRefs> {

        public boolean isWriteable(Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
            return ResourceRefs.class.isAssignableFrom(aClass);
        }

        public long getSize(ResourceRefs resourceRefs, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
            return -1;
        }

        public void writeTo(ResourceRefs refs, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> stringObjectMultivaluedMap, OutputStream outputStream) throws IOException, WebApplicationException {
            Writer osw = new OutputStreamWriter(outputStream);
            XmlWriter w = new XmlWriter(osw);
            w.element(RESOURCES);
            for (ResourceRef ref : refs.getResources()) {
                w.element(RESOURCE);
                w.attribute(ID, ref.getId());
                if (ref.getRepository() != null) {
                    w.attribute(REPOSITORY, ref.getRepository());
                }
                w.end();
            }
            w.end();
            osw.flush();
        }
    }

    @Provider
    public static class ResourceRefProvider implements MessageBodyWriter<ResourceRef> {

        public boolean isWriteable(Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
            return ResourceRef.class.isAssignableFrom(aClass);
        }

        public long getSize(ResourceRef resourceRef, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
            return -1;
        }

        public void writeTo(ResourceRef ref, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> stringObjectMultivaluedMap, OutputStream outputStream) throws IOException, WebApplicationException {
            Writer osw = new OutputStreamWriter(outputStream);
            XmlWriter w = new XmlWriter(osw);
            w.element(RESOURCE);
            w.attribute(ID, ref.getId());
            if (ref.getRepository() != null) {
                w.attribute(REPOSITORY, ref.getRepository());
            }
            w.end();
            osw.flush();
        }
    }

    @Provider
    public static class RepositoryRefProvider implements MessageBodyWriter<RepositoryRef>, MessageBodyReader<RepositoryRef> {

        public boolean isWriteable(Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
            return RepositoryRef.class.isAssignableFrom(aClass);
        }

        public long getSize(RepositoryRef repositoryRef, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
            return -1;
        }

        public void writeTo(RepositoryRef ref, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> stringObjectMultivaluedMap, OutputStream outputStream) throws IOException, WebApplicationException {
            Writer osw = new OutputStreamWriter(outputStream);
            XmlWriter w = new XmlWriter(osw);
            w.element(REPOSITORY);
            w.attribute(ID, ref.getId());
            w.end();
            osw.flush();
        }

        public boolean isReadable(Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
            return RepositoryRef.class.isAssignableFrom(aClass);
        }

        public RepositoryRef readFrom(Class<RepositoryRef> repositoryRefClass, Type type, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> stringStringMultivaluedMap, InputStream inputStream) throws IOException, WebApplicationException {
            try {
                KXmlParser parser = new KXmlParser();
                parser.setInput(new InputStreamReader(inputStream));
                if (parser.nextTag() != KXmlParser.START_TAG || !parser.getName().equals(REPOSITORY)) {
                    throw new IOException("Expected tag '" + REPOSITORY + "'");
                }
                String id = parser.getAttributeValue(null, ID);
                if (id == null) {
                    throw new IllegalStateException("Missing attribute " + ID);
                }
                return new RepositoryRef(id);
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
    }

    @Provider
    public static class RepositoryRefsProvider implements MessageBodyWriter<RepositoryRefs> {

        public boolean isWriteable(Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
            return RepositoryRefs.class.isAssignableFrom(aClass);
        }

        public long getSize(RepositoryRefs repositoryRefs, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
            return -1;
        }

        public void writeTo(RepositoryRefs refs, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> stringObjectMultivaluedMap, OutputStream outputStream) throws IOException, WebApplicationException {
            Writer osw = new OutputStreamWriter(outputStream);
            XmlWriter w = new XmlWriter(osw);
            w.element(REPOSITORIES);
            for (RepositoryRef repositoryRef : refs.getRepositories()) {
                w.element(REPOSITORY);
                w.attribute(ID, repositoryRef.getId());
                w.end();
            }
            w.end();
            osw.flush();
        }
    }

    @Provider
    public static class RepositoryProvider implements MessageBodyWriter<Repository> {

        public boolean isWriteable(Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
            return Repository.class.isAssignableFrom(aClass);
        }

        public long getSize(Repository repository, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
            return -1;
        }

        public void writeTo(Repository repository, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> stringObjectMultivaluedMap, OutputStream outputStream) throws IOException, WebApplicationException {
            Writer osw = new OutputStreamWriter(outputStream);
            XmlWriter w = new XmlWriter(osw);
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
                w.element(RESOURCE);
                w.attribute(ID, res.getId());
                w.attribute(NAME, res.getPresentationName());
                w.end();
            }
            w.end();
            osw.flush();
        }
    }

    @Provider
    public static class DiscoverProvider implements MessageBodyWriter<Discover>, MessageBodyReader<Discover> {

        public boolean isWriteable(Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
            return Discover.class.isAssignableFrom(aClass);
        }

        public long getSize(Discover resource, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
            return -1;
        }

        public void writeTo(Discover resource, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> stringObjectMultivaluedMap, OutputStream outputStream) throws IOException, WebApplicationException {
            throw new UnsupportedOperationException();
        }

        public boolean isReadable(Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
            return Discover.class.isAssignableFrom(aClass);
        }

        public Discover readFrom(Class<Discover> discoverClass, Type type, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> stringStringMultivaluedMap, InputStream inputStream) throws IOException, WebApplicationException {
            try {
                KXmlParser parser = new KXmlParser();
                parser.setInput(new InputStreamReader(inputStream));
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
                        reqs.add(new PullParser().parseRequire(parser));
                    }
                }
                return new Discover(repos, reqs, filter);
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
    }

    public static class Resolve {

        private List<Resource> addedResources = new ArrayList<Resource>();
        private List<Requirement> addedRequirements = new ArrayList<Requirement>();
        private List<Capability> globalCapabilities = new ArrayList<Capability>();
        private List<String> repositories = new ArrayList<String>();
        private List<Resource> localResources = new ArrayList<Resource>();
        private int flags;

        public List<Resource> getAddedResources() {
            return addedResources;
        }

        public void setAddedResources(List<Resource> addedResources) {
            this.addedResources = addedResources;
        }

        public List<Requirement> getAddedRequirements() {
            return addedRequirements;
        }

        public void setAddedRequirements(List<Requirement> addedRequirements) {
            this.addedRequirements = addedRequirements;
        }

        public List<Capability> getGlobalCapabilities() {
            return globalCapabilities;
        }

        public void setGlobalCapabilities(List<Capability> globalCapabilities) {
            this.globalCapabilities = globalCapabilities;
        }

        public List<String> getRepositories() {
            return repositories;
        }

        public void setRepositories(List<String> repositories) {
            this.repositories = repositories;
        }

        public List<Resource> getLocalResources() {
            return localResources;
        }

        public void setLocalResources(List<Resource> localResources) {
            this.localResources = localResources;
        }

        public int getFlags() {
            return flags;
        }

        public void setFlags(int flags) {
            this.flags = flags;
        }
    }

    public static class Resolution {

        private List<ResourceRef> requiredResources;
        private List<ResourceRef> optionalResources;
        private Map<Resource, List<Reason>> reasons;
        private List<Reason> unsatisfiedRequirements;

        public List<ResourceRef> getRequiredResources() {
            return requiredResources;
        }

        public void setRequiredResources(List<ResourceRef> requiredResources) {
            this.requiredResources = requiredResources;
        }

        public List<ResourceRef> getOptionalResources() {
            return optionalResources;
        }

        public void setOptionalResources(List<ResourceRef> optionalResources) {
            this.optionalResources = optionalResources;
        }

        public Map<Resource, List<Reason>> getReasons() {
            return reasons;
        }

        public void setReasons(Map<Resource, List<Reason>> reasons) {
            this.reasons = reasons;
        }

        public List<Reason> getUnsatisfiedRequirements() {
            return unsatisfiedRequirements;
        }

        public void setUnsatisfiedRequirements(List<Reason> unsatisfiedRequirements) {
            this.unsatisfiedRequirements = unsatisfiedRequirements;
        }
    }

    public static class Discover {

        private List<RepositoryRef> repositories = new ArrayList<RepositoryRef>();
        private List<Requirement> requirements = new ArrayList<Requirement>();
        private String filter;

        public Discover() {
        }

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

        private List<ResourceRef> resources;

        public ResourceRefs() {
        }

        public ResourceRefs(List<ResourceRef> resources) {
            this.resources = resources;
        }

        public List<ResourceRef> getResources() {
            return resources;
        }
    }

    public static class ResourceRef {

        private String id;
        private String repository;

        public ResourceRef() {
            this(null, null);
        }

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

        private List<RepositoryRef> repositories;

        public RepositoryRefs() {
        }

        public RepositoryRefs(List<RepositoryRef> repositories) {
            this.repositories = repositories;
        }

        public List<RepositoryRef> getRepositories() {
            return repositories;
        }
    }

    public static class RepositoryRef {

        private String id;

        public RepositoryRef() {
        }

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
