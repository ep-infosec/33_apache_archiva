package org.apache.archiva.webdav;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import junit.framework.TestCase;
import org.apache.archiva.admin.model.RepositoryAdminException;
import org.apache.archiva.admin.model.beans.ManagedRepository;
import org.apache.archiva.admin.model.beans.RepositoryGroup;
import org.apache.archiva.admin.model.remote.RemoteRepositoryAdmin;
import org.apache.archiva.admin.repository.DefaultRepositoryCommonValidator;
import org.apache.archiva.admin.repository.group.DefaultRepositoryGroupAdmin;
import org.apache.archiva.admin.repository.managed.DefaultManagedRepositoryAdmin;
import org.apache.archiva.common.filelock.FileLockManager;
import org.apache.archiva.configuration.provider.ArchivaConfiguration;
import org.apache.archiva.configuration.model.Configuration;
import org.apache.archiva.configuration.provider.FileTypes;
import org.apache.archiva.configuration.model.RepositoryGroupConfiguration;
import org.apache.archiva.metadata.repository.storage.RepositoryPathTranslator;
import org.apache.archiva.proxy.ProxyRegistry;
import org.apache.archiva.repository.EditableManagedRepository;
import org.apache.archiva.repository.ManagedRepositoryContent;
import org.apache.archiva.repository.RemoteRepository;
import org.apache.archiva.repository.RemoteRepositoryContent;
import org.apache.archiva.repository.Repository;
import org.apache.archiva.repository.RepositoryContent;
import org.apache.archiva.repository.RepositoryContentFactory;
import org.apache.archiva.repository.RepositoryContentProvider;
import org.apache.archiva.repository.RepositoryException;
import org.apache.archiva.repository.RepositoryRegistry;
import org.apache.archiva.repository.RepositoryType;
import org.apache.archiva.repository.base.RepositoryHandlerDependencies;
import org.apache.archiva.maven.repository.content.ManagedDefaultRepositoryContent;
import org.apache.archiva.maven.repository.content.MavenContentHelper;
import org.apache.archiva.maven.repository.content.MavenRepositoryRequestInfo;
import org.apache.archiva.maven.repository.metadata.storage.ArtifactMappingProvider;
import org.apache.archiva.test.utils.ArchivaSpringJUnit4ClassRunner;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavResourceLocator;
import org.apache.jackrabbit.webdav.DavServletRequest;
import org.apache.jackrabbit.webdav.DavServletResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;

import javax.inject.Inject;
import javax.inject.Named;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.*;


/**
 * ArchivaDavResourceFactoryTest
 */
@RunWith( ArchivaSpringJUnit4ClassRunner.class )
@ContextConfiguration( locations = { "classpath*:/META-INF/spring-context.xml", "classpath*:/spring-context.xml" } )
public class ArchivaDavResourceFactoryTest
    extends TestCase
{
    private AtomicReference<Path> projectBase = new AtomicReference<>();

    private static final String RELEASES_REPO = "releases";

    private static final String INTERNAL_REPO = "internal";

    private static final String LOCAL_MIRROR_REPO = "local-mirror";

    private static final String LEGACY_REPO = "legacy-repo";

    private static final String LOCAL_REPO_GROUP = "local";

    private OverridingArchivaDavResourceFactory resourceFactory;

    private DavServletRequest request;

    private MavenRepositoryRequestInfo repoRequest;

    private DavServletResponse response;

    private ArchivaConfiguration archivaConfiguration;

    private Configuration config;

    private RepositoryContentFactory repoFactory;

    @Inject
    ApplicationContext applicationContext;

    @Inject
    DefaultManagedRepositoryAdmin defaultManagedRepositoryAdmin;

    @Inject
    RepositoryRegistry repositoryRegistry;

    @Inject
    RemoteRepositoryAdmin remoteRepositoryAdmin;

    @Inject
    ProxyRegistry proxyRegistry;

    @Inject
    @Named( "MavenContentHelper" )
    MavenContentHelper mavenContentHelper;

    @Inject
    DefaultRepositoryGroupAdmin defaultRepositoryGroupAdmin;

    @Inject
    List<? extends ArtifactMappingProvider> artifactMappingProviders;

    @Inject
    @Named( "repositoryPathTranslator#maven2" )
    RepositoryPathTranslator pathTranslator;

    @Inject
    FileLockManager fileLockManager;

    @Inject
    FileTypes fileTypes;

    @SuppressWarnings( "unused" )
    @Inject
    RepositoryHandlerDependencies repositoryHandlerDependencies;

    public Path getProjectBase() {
        if (this.projectBase.get()==null) {
            String pathVal = System.getProperty("mvn.project.base.dir");
            Path baseDir;
            if (StringUtils.isEmpty(pathVal)) {
                baseDir= Paths.get("").toAbsolutePath();
            } else {
                baseDir = Paths.get(pathVal).toAbsolutePath();
            }
            this.projectBase.compareAndSet(null, baseDir);
        }
        return this.projectBase.get();
    }

    @Before
    @Override
    public void setUp()
        throws Exception
    {
        super.setUp();

        request = mock( DavServletRequest.class );

        response = mock( DavServletResponse.class );
        //responseControl.setDefaultMatcher( MockControl.ALWAYS_MATCHER );

        archivaConfiguration = mock( ArchivaConfiguration.class );

        config = new Configuration();
        when( archivaConfiguration.getConfiguration() ).thenReturn( config );
        when(archivaConfiguration.getDefaultLocale()).thenReturn( Locale.getDefault() );
        archivaConfiguration.addListener( any() );
        archivaConfiguration.save( eq(config));
        archivaConfiguration.save( eq(config), anyString());

        defaultManagedRepositoryAdmin.setArchivaConfiguration( archivaConfiguration );
        repositoryRegistry.setArchivaConfiguration( archivaConfiguration );
        repositoryRegistry.reload();
        ( (DefaultRepositoryCommonValidator) defaultManagedRepositoryAdmin.getRepositoryCommonValidator() ).setArchivaConfiguration(
            archivaConfiguration );
        if ( defaultManagedRepositoryAdmin.getManagedRepository( RELEASES_REPO ) == null )
        {
            defaultManagedRepositoryAdmin.addManagedRepository(
                createManagedRepository( RELEASES_REPO, getProjectBase().resolve( "target/test-classes/" + RELEASES_REPO ).toString(),
                                         "default" ), false, null );
        }
        if ( defaultManagedRepositoryAdmin.getManagedRepository( INTERNAL_REPO ) == null )
        {
            defaultManagedRepositoryAdmin.addManagedRepository(
                createManagedRepository( INTERNAL_REPO, getProjectBase().resolve( "target/test-classes/" + INTERNAL_REPO ).toString(),
                                         "default" ), false, null );
        }
        RepositoryGroup repoGroupConfig = new RepositoryGroup();
        repoGroupConfig.setId( LOCAL_REPO_GROUP );
        repoGroupConfig.addRepository( RELEASES_REPO );
        repoGroupConfig.addRepository( INTERNAL_REPO );

        defaultRepositoryGroupAdmin.setArchivaConfiguration( archivaConfiguration );
        if ( defaultManagedRepositoryAdmin.getManagedRepository( LOCAL_REPO_GROUP ) == null )
        {
            defaultRepositoryGroupAdmin.addRepositoryGroup( repoGroupConfig, null );
        }

        repoFactory = mock( RepositoryContentFactory.class );

        repoRequest = mock( MavenRepositoryRequestInfo.class );

        resourceFactory =
            new OverridingArchivaDavResourceFactory( applicationContext, archivaConfiguration );
        resourceFactory.setArchivaConfiguration( archivaConfiguration );
        proxyRegistry.getAllHandler().get(RepositoryType.MAVEN).clear();
        proxyRegistry.getAllHandler().get(RepositoryType.MAVEN).add(new OverridingRepositoryProxyHandler(this));
        resourceFactory.setProxyRegistry(proxyRegistry);
        resourceFactory.setRemoteRepositoryAdmin( remoteRepositoryAdmin );
        resourceFactory.setManagedRepositoryAdmin( defaultManagedRepositoryAdmin );
        resourceFactory.setRepositoryRegistry( repositoryRegistry );
        verify( archivaConfiguration,    atLeast( 2 )).getConfiguration();
        verify( archivaConfiguration,    atMost( 25 )).getConfiguration();
        verify( archivaConfiguration, atMost( 4 ) ).addListener( any() );
        verify( archivaConfiguration, atMost( 5 ) ).save( eq(config) );
        verify( archivaConfiguration, atMost( 5 ) ).save( eq(config), anyString() );

    }

    private ManagedRepository createManagedRepository( String id, String location, String layout )
    {
        ManagedRepository repoConfig = new ManagedRepository( Locale.getDefault());
        repoConfig.setId( id );
        repoConfig.setName( id );
        repoConfig.setLocation( location );
        repoConfig.setLayout( layout );

        return repoConfig;
    }

    private ManagedRepositoryContent createManagedRepositoryContent( String repoId )
        throws RepositoryAdminException
    {
        org.apache.archiva.repository.ManagedRepository repo = repositoryRegistry.getManagedRepository( repoId );
        ManagedDefaultRepositoryContent repoContent = new ManagedDefaultRepositoryContent(repo, fileTypes, fileLockManager);
        if (repo!=null && repo instanceof EditableManagedRepository)
        {
            ( (EditableManagedRepository) repo ).setContent( repoContent );
        }
        repoContent.setMavenContentHelper( mavenContentHelper );
        repoContent.setArtifactMappingProviders( artifactMappingProviders );
        repoContent.setPathTranslator( pathTranslator );
        return repoContent;
    }

    private RepositoryContentProvider createRepositoryContentProvider( ManagedRepositoryContent content) {
        Set<RepositoryType> TYPES = new HashSet<>(  );
        TYPES.add(RepositoryType.MAVEN);
        return new RepositoryContentProvider( )
        {


            @Override
            public boolean supportsLayout( String layout )
            {
                return true;
            }

            @Override
            public Set<RepositoryType> getSupportedRepositoryTypes( )
            {
                return TYPES;
            }

            @Override
            public boolean supports( RepositoryType type )
            {
                return true;
            }

            @Override
            public RemoteRepositoryContent createRemoteContent( RemoteRepository repository ) throws RepositoryException
            {
                return null;
            }

            @Override
            public ManagedRepositoryContent createManagedContent( org.apache.archiva.repository.ManagedRepository repository ) throws RepositoryException
            {
                content.setRepository( repository );
                return content;
            }

            @Override
            public <T extends RepositoryContent, V extends Repository> T createContent( Class<T> clazz, V repository ) throws RepositoryException
            {
                return null;
            }
        };
    }

    @After
    @Override
    public void tearDown()
        throws Exception
    {
        super.tearDown();
        String appserverBase = System.getProperty( "appserver.base" );
        if ( StringUtils.isNotEmpty( appserverBase ) )
        {
            org.apache.archiva.common.utils.FileUtils.deleteDirectory( Paths.get( appserverBase ) );
        }
    }

    // MRM-1232 - Unable to get artifacts from repositories which requires Repository Manager role using repository group
    @Test
    public void testRepositoryGroupFirstRepositoryRequiresAuthentication()
        throws Exception
    {
        DavResourceLocator locator = new ArchivaDavResourceLocator( "", "/repository/" + LOCAL_REPO_GROUP
            + "/org/apache/archiva/archiva/1.2-SNAPSHOT/archiva-1.2-SNAPSHOT.jar", LOCAL_REPO_GROUP,
                                                                    new ArchivaDavLocatorFactory() );

        ManagedRepositoryContent internalRepo = createManagedRepositoryContent( INTERNAL_REPO );
        ManagedRepositoryContent releasesRepo = createManagedRepositoryContent( RELEASES_REPO );

        try
        {
            reset( archivaConfiguration );
            reset( request );
            reset( repoFactory );
            when( archivaConfiguration.getConfiguration( ) ).thenReturn( config );

            when( request.getMethod() ).thenReturn( "GET" );

            when( request.getPathInfo() ).thenReturn( "org/apache/archiva" );

            when( repoFactory.getManagedRepositoryContent( RELEASES_REPO ) ).thenReturn( releasesRepo );

            when( request.getRemoteAddr( ) ).thenReturn( "http://localhost:8080" );

            when( request.getDavSession( ) ).thenReturn( new ArchivaDavSession( ) );

            when( request.getContextPath( ) ).thenReturn( "" );

            when( repoRequest.isSupportFile( "org/apache/archiva/archiva/1.2-SNAPSHOT/archiva-1.2-SNAPSHOT.jar" ) ).thenReturn( true );

            when(
                repoRequest.getLayout( "org/apache/archiva/archiva/1.2-SNAPSHOT/archiva-1.2-SNAPSHOT.jar" ) ).thenReturn(
                "legacy" );

            when( repoRequest.toItemSelector(
                "org/apache/archiva/archiva/1.2-SNAPSHOT/archiva-1.2-SNAPSHOT.jar" ) ).thenReturn( null );

            when( repoRequest.toNativePath( "org/apache/archiva/archiva/1.2-SNAPSHOT/archiva-1.2-SNAPSHOT.jar"
            ) ).thenReturn(
                Paths.get( config.findManagedRepositoryById( INTERNAL_REPO ).getLocation(),
                          "target/test-classes/internal/org/apache/archiva/archiva/1.2-SNAPSHOT/archiva-1.2-SNAPSHOT.jar" ).toString());

            when( repoFactory.getManagedRepositoryContent( INTERNAL_REPO ) ).thenReturn( internalRepo );

            when( repoRequest.isArchetypeCatalog(
                "org/apache/archiva/archiva/1.2-SNAPSHOT/archiva-1.2-SNAPSHOT.jar" ) ).thenReturn( false );

            resourceFactory.createResource( locator, request, response );

            verify(archivaConfiguration, times( 3 )).getConfiguration();
            verify( request, times( 3 ) ).getMethod( );
            verify( request, atMost( 2 ) ).getPathInfo( );
            verify(request,times( 2 )).getRemoteAddr();
            verify( request, times( 2 ) ).getDavSession( );
            verify( request, times( 2 ) ).getContextPath( );


            fail( "A DavException with 401 error code should have been thrown." );
        }
        catch ( DavException e )
        {
            assertEquals( 401, e.getErrorCode() );
        }
    }

    @Test
    public void testRepositoryGroupLastRepositoryRequiresAuthentication()
        throws Exception
    {
        DavResourceLocator locator = new ArchivaDavResourceLocator( "", "/repository/" + LOCAL_REPO_GROUP
            + "/org/apache/archiva/archiva/1.2-SNAPSHOT/archiva-1.2-SNAPSHOT.jar", LOCAL_REPO_GROUP,
                                                                    new ArchivaDavLocatorFactory() );

        List<RepositoryGroupConfiguration> repoGroups = new ArrayList<>();
        RepositoryGroupConfiguration repoGroup = new RepositoryGroupConfiguration();
        repoGroup.setId( LOCAL_REPO_GROUP );
        repoGroup.addRepository( INTERNAL_REPO );
        repoGroup.addRepository( RELEASES_REPO );

        repoGroups.add( repoGroup );

        config.setRepositoryGroups( repoGroups );

        ManagedRepositoryContent internalRepo = createManagedRepositoryContent( INTERNAL_REPO );

        ManagedRepositoryContent releasesRepo = createManagedRepositoryContent( RELEASES_REPO );

        try
        {
            reset( archivaConfiguration );
            reset( request );
            reset( repoFactory );

            when( archivaConfiguration.getConfiguration( ) ).thenReturn( config );

            when( request.getMethod() ).thenReturn( "GET" );

            when( request.getPathInfo() ).thenReturn( "org/apache/archiva" );

            when( repoFactory.getManagedRepositoryContent( INTERNAL_REPO ) ).thenReturn( internalRepo );

            when( repoFactory.getManagedRepositoryContent( RELEASES_REPO ) ).thenReturn( releasesRepo );

            when( request.getRemoteAddr() ).thenReturn( "http://localhost:8080" );

            when( request.getDavSession() ).thenReturn( new ArchivaDavSession() );

            when( request.getContextPath() ).thenReturn( "" );

            when( repoRequest.isSupportFile( "org/apache/archiva/archiva/1.2-SNAPSHOT/archiva-1.2-SNAPSHOT.jar" ) ).thenReturn( false );

            when(
                repoRequest.getLayout( "org/apache/archiva/archiva/1.2-SNAPSHOT/archiva-1.2-SNAPSHOT.jar" ) ).thenReturn(
                "legacy" );

            when( repoRequest.toItemSelector(
                "org/apache/archiva/archiva/1.2-SNAPSHOT/archiva-1.2-SNAPSHOT.jar" ) ).thenReturn( null );

            when( repoRequest.toNativePath( "org/apache/archiva/archiva/1.2-SNAPSHOT/archiva-1.2-SNAPSHOT.jar"
            ) ).thenReturn(
                Paths.get( config.findManagedRepositoryById( INTERNAL_REPO ).getLocation(),
                          "target/test-classes/internal/org/apache/archiva/archiva/1.2-SNAPSHOT/archiva-1.2-SNAPSHOT.jar" ).toString());


            when( repoRequest.isArchetypeCatalog(
                "org/apache/archiva/archiva/1.2-SNAPSHOT/archiva-1.2-SNAPSHOT.jar" ) ).thenReturn( false );

            resourceFactory.createResource( locator, request, response );
            verify( archivaConfiguration, times( 3 ) ).getConfiguration( );
            verify( request, times( 3 ) ).getMethod();
            verify( request, atMost( 2 ) ).getPathInfo( );
            verify( request, times( 2 ) ).getRemoteAddr( );
            verify( request, times( 2 ) ).getDavSession( );
            verify( request, times( 2 ) ).getContextPath( );

            fail( "A DavException with 401 error code should have been thrown." );
        }
        catch ( DavException e )
        {
            assertEquals( 401, e.getErrorCode() );
        }
    }

    @Test
    public void testRepositoryGroupArtifactDoesNotExistInAnyOfTheReposAuthenticationDisabled()
        throws Exception
    {
        DavResourceLocator locator = new ArchivaDavResourceLocator( "", "/repository/" + LOCAL_REPO_GROUP
            + "/org/apache/archiva/archiva/1.2-SNAPSHOT/archiva-1.2-SNAPSHOT.jar", LOCAL_REPO_GROUP,
                                                                    new ArchivaDavLocatorFactory() );

        defaultManagedRepositoryAdmin.addManagedRepository(
            createManagedRepository( LOCAL_MIRROR_REPO, Paths.get( "target/test-classes/local-mirror" ).toString(),
                                     "default" ), false, null );

        List<RepositoryGroupConfiguration> repoGroups = new ArrayList<>();
        RepositoryGroupConfiguration repoGroup = new RepositoryGroupConfiguration();
        repoGroup.setId( LOCAL_REPO_GROUP );
        repoGroup.addRepository( INTERNAL_REPO );
        repoGroup.addRepository( LOCAL_MIRROR_REPO );

        repoGroups.add( repoGroup );

        config.setRepositoryGroups( repoGroups );

        ManagedRepositoryContent internalRepo = createManagedRepositoryContent( INTERNAL_REPO );
        ManagedRepositoryContent localMirrorRepo = createManagedRepositoryContent( LOCAL_MIRROR_REPO );

        repositoryRegistry.putRepositoryGroup( repoGroup );

        try
        {
            reset( archivaConfiguration );
            reset( request );
            reset( repoFactory );

            when( archivaConfiguration.getConfiguration() ).thenReturn( config );

            when( request.getMethod() ).thenReturn( "GET" );

            when( request.getPathInfo() ).thenReturn( "org/apache/archiva" );

            when( repoFactory.getManagedRepositoryContent( INTERNAL_REPO ) ).thenReturn( internalRepo );

            when( repoFactory.getManagedRepositoryContent( LOCAL_MIRROR_REPO ) ).thenReturn( localMirrorRepo );

            when( request.getRemoteAddr() ).thenReturn( "http://localhost:8080" );

            when( request.getDavSession() ).thenReturn( new ArchivaDavSession() );

            when( request.getContextPath() ).thenReturn( "" );

            when( repoRequest.isSupportFile( "org/apache/archiva/archiva/1.2-SNAPSHOT/archiva-1.2-SNAPSHOT.jar" ) ).thenReturn( false );

            when(
                repoRequest.getLayout( "org/apache/archiva/archiva/1.2-SNAPSHOT/archiva-1.2-SNAPSHOT.jar" ) ).thenReturn(
                "legacy" );

            when( repoRequest.toItemSelector(
                "org/apache/archiva/archiva/1.2-SNAPSHOT/archiva-1.2-SNAPSHOT.jar" ) ).thenReturn( null );

            when( repoRequest.toNativePath( "org/apache/archiva/archiva/1.2-SNAPSHOT/archiva-1.2-SNAPSHOT.jar"
            ) ).thenReturn(
                Paths.get( config.findManagedRepositoryById( INTERNAL_REPO ).getLocation(),
                          "target/test-classes/internal/org/apache/archiva/archiva/1.2-SNAPSHOT/archiva-1.2-SNAPSHOT.jar" ).toString() );

            when( repoRequest.toNativePath( "org/apache/archiva/archiva/1.2-SNAPSHOT/archiva-1.2-SNAPSHOT.jar"
            ) )
                .thenReturn( Paths.get( config.findManagedRepositoryById( LOCAL_MIRROR_REPO ).getLocation(),
                                      "target/test-classes/internal/org/apache/archiva/archiva/1.2-SNAPSHOT/archiva-1.2-SNAPSHOT.jar" ).toString());

            when( repoRequest.isArchetypeCatalog( "org/apache/archiva/archiva/1.2-SNAPSHOT/archiva-1.2-SNAPSHOT.jar" ) ).thenReturn( false );

            resourceFactory.createResource( locator, request, response );
            verify( archivaConfiguration, times( 3 ) ).getConfiguration( );
            verify( request, times( 5 ) ).getMethod( );
            verify( request, atMost( 2 ) ).getPathInfo( );
            verify( request, times( 4 ) ).getRemoteAddr( );
            verify( request, times( 4 ) ).getDavSession( );
            verify( request, times( 2 ) ).getContextPath( );
            verify( repoRequest, times( 2 ) ).isSupportFile( "org/apache/archiva/archiva/1.2-SNAPSHOT/archiva-1.2-SNAPSHOT.jar" );
            verify(repoRequest, times( 2 )).getLayout( "org/apache/archiva/archiva/1.2-SNAPSHOT/archiva-1.2-SNAPSHOT.jar" );
            verify( repoRequest, times( 2 ) ).toItemSelector(
                "org/apache/archiva/archiva/1.2-SNAPSHOT/archiva-1.2-SNAPSHOT.jar" );
            verify( repoRequest, times( 2 ) ).isArchetypeCatalog( "org/apache/archiva/archiva/1.2-SNAPSHOT/archiva-1.2-SNAPSHOT.jar" );

            fail( "A DavException with 404 error code should have been thrown." );
        }
        catch ( DavException e )
        {
            assertEquals( 404, e.getErrorCode() );
        }
    }

    // MRM-1239
    @Test
    public void testRequestArtifactMetadataThreePartsRepoHasDefaultLayout()
        throws Exception
    {
        // should fetch metadata 
        DavResourceLocator locator =
            new ArchivaDavResourceLocator( "", "/repository/" + INTERNAL_REPO + "/eclipse/jdtcore/maven-metadata.xml",
                                           INTERNAL_REPO, new ArchivaDavLocatorFactory() );

        ManagedRepositoryContent internalRepo = createManagedRepositoryContent( INTERNAL_REPO );

        // use actual object (this performs the isMetadata, isDefault and isSupportFile check!)
        MavenRepositoryRequestInfo repoRequest = new MavenRepositoryRequestInfo(internalRepo.getRepository() );

        try
        {
            reset( request );

            when( request.getMethod() ).thenReturn( "GET" );

            when( request.getRemoteAddr() ).thenReturn( "http://localhost:8080" );

            when( request.getContextPath() ).thenReturn( "" );

            when( request.getDavSession() ).thenReturn( new ArchivaDavSession() );

            when( request.getRequestURI() ).thenReturn( "http://localhost:8080/archiva/repository/" + INTERNAL_REPO + "/eclipse/jdtcore/maven-metadata.xml" );
            response.setHeader( "Pragma", "no-cache" );
            response.setHeader( "Cache-Control", "no-cache" );
            response.setDateHeader( eq("Last-Modified"), anyLong() );

            resourceFactory.createResource( locator, request, response );
            verify( request, times( 4 ) ).getMethod( );
            verify( request, times( 3 ) ).getRemoteAddr( );
            verify( request, times( 1 ) ).getContextPath( );
            verify( request, times( 2 ) ).getDavSession( );

        }
        catch ( DavException e )
        {
            e.printStackTrace();
            fail( "A DavException should not have been thrown! "+e.getMessage() );
        }
    }

    @Test
    public void testRequestArtifactMetadataTwoPartsRepoHasDefaultLayout()
        throws Exception
    {
        // should not fetch metadata
        DavResourceLocator locator =
            new ArchivaDavResourceLocator( "", "/repository/" + INTERNAL_REPO + "/eclipse/maven-metadata.xml",
                                           INTERNAL_REPO, new ArchivaDavLocatorFactory() );

        ManagedRepositoryContent internalRepo = createManagedRepositoryContent( INTERNAL_REPO );

        try
        {
            reset( archivaConfiguration );
            reset( request );
            reset( repoFactory );

            when( archivaConfiguration.getConfiguration() ).thenReturn( config );

            when( repoFactory.getManagedRepositoryContent( INTERNAL_REPO ) ).thenReturn( internalRepo );

            when( request.getMethod() ).thenReturn( "GET" );

            when( request.getRemoteAddr() ).thenReturn( "http://localhost:8080" );

            when( request.getDavSession() ).thenReturn( new ArchivaDavSession() );

            when( request.getContextPath() ).thenReturn( "" );

            resourceFactory.createResource( locator, request, response );
            verify( archivaConfiguration, times( 2 ) ).getConfiguration( );
            verify( request, times( 3 ) ).getMethod( );
            verify( request, times( 3 ) ).getRemoteAddr( );
            verify( request, times( 2 ) ).getDavSession( );
            verify( request, times( 2 ) ).getContextPath( );

            fail( "A 404 error should have been thrown!" );
        }
        catch ( DavException e )
        {
            assertEquals( 404, e.getErrorCode() );
        }
    }

    @Test
    public void testRequestMetadataRepoIsLegacy()
        throws Exception
    {
        ManagedRepositoryContent legacyRepo = createManagedRepositoryContent( LEGACY_REPO );
        ConfigurableListableBeanFactory beanFactory = ((ConfigurableApplicationContext) applicationContext).getBeanFactory();
        RepositoryContentProvider provider = createRepositoryContentProvider(legacyRepo );
        beanFactory.registerSingleton("repositoryContentProvider#legacy", provider);
        RepositoryContentFactory repoContentFactory = applicationContext.getBean( "repositoryContentFactory#default", RepositoryContentFactory.class );
        repoContentFactory.getRepositoryContentProviders().add(provider);
        defaultManagedRepositoryAdmin.addManagedRepository(
            createManagedRepository( LEGACY_REPO, getProjectBase().resolve( "target/test-classes/" + LEGACY_REPO ).toString(),
                "legacy" ), false, null );

        DavResourceLocator locator =
            new ArchivaDavResourceLocator( "", "/repository/" + LEGACY_REPO + "/eclipse/maven-metadata.xml",
                                           LEGACY_REPO, new ArchivaDavLocatorFactory() );


        try
        {
            reset( archivaConfiguration );
            reset( request );
            reset( repoFactory );

            when( archivaConfiguration.getConfiguration() ).thenReturn( config );

            when( repoFactory.getManagedRepositoryContent( LEGACY_REPO ) ).thenReturn( legacyRepo );

            when( request.getMethod() ).thenReturn( "GET" );

            when( request.getRemoteAddr() ).thenReturn( "http://localhost:8080" );

            when( request.getDavSession() ).thenReturn( new ArchivaDavSession() );

            when( request.getContextPath() ).thenReturn( "" );

            resourceFactory.createResource( locator, request, response );

            verify( archivaConfiguration,
                times( 2 ) ).getConfiguration( );
            verify( request, times( 3 ) ).getMethod( );
            verify( request, times( 3 ) ).getRemoteAddr( );
            verify( request, times( 2 ) ).getDavSession( );
            verify( request, times( 2 ) ).getContextPath( );

            fail( "A 404 error should have been thrown!" );
        }
        catch ( DavException e )
        {
            assertEquals( 404, e.getErrorCode() );
        }
    }

    class OverridingArchivaDavResourceFactory
        extends ArchivaDavResourceFactory
    {

        OverridingArchivaDavResourceFactory( ApplicationContext applicationContext,
                                             ArchivaConfiguration archivaConfiguration )
        {
            super( applicationContext, archivaConfiguration );
        }

        @Override
        protected boolean isAuthorized( DavServletRequest request, String repositoryId )
            throws DavException
        {
            if ( RELEASES_REPO.equals( repositoryId ) )
            {
                throw new UnauthorizedDavException( repositoryId,
                                                    "You are not authenticated and authorized to access any repository." );
            }
            else
            {
                return true;
            }
        }

        @Override
        protected String getActivePrincipal( DavServletRequest request )
        {
            return "guest";
        }
    }

}
