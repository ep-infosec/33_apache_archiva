package org.apache.archiva.repository.mock;

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

import org.apache.archiva.configuration.model.ManagedRepositoryConfiguration;
import org.apache.archiva.configuration.model.RemoteRepositoryConfiguration;
import org.apache.archiva.configuration.model.RepositoryGroupConfiguration;
import org.apache.archiva.event.EventHandler;
import org.apache.archiva.repository.*;
import org.apache.archiva.event.Event;
import org.apache.archiva.repository.base.group.BasicRepositoryGroup;
import org.apache.archiva.repository.event.RepositoryEvent;
import org.apache.archiva.repository.features.ArtifactCleanupFeature;
import org.apache.archiva.repository.features.IndexCreationFeature;
import org.apache.archiva.repository.features.RemoteIndexFeature;
import org.apache.archiva.repository.features.StagingRepositoryFeature;
import org.apache.archiva.repository.base.managed.BasicManagedRepository;
import org.apache.archiva.repository.base.remote.BasicRemoteRepository;
import org.apache.archiva.repository.base.PasswordCredentials;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Period;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Just a simple mock class for the repository provider
 */
@Service("mockRepositoryProvider")
public class RepositoryProviderMock implements RepositoryProvider
{

    private static final Set<RepositoryType> TYPES = new HashSet<>( );

    static
    {
        TYPES.add( RepositoryType.MAVEN );
        TYPES.add( RepositoryType.NPM );
    }

    @Override
    public Set<RepositoryType> provides( )
    {
        return TYPES;
    }

    @Override
    public EditableManagedRepository createManagedInstance( String id, String name ) throws IOException {
        return BasicManagedRepository.newFilesystemInstance(id, name, Paths.get("target/repositories").resolve(id));
    }

    @Override
    public EditableRemoteRepository createRemoteInstance( String id, String name )
    {
        try {
            return BasicRemoteRepository.newFilesystemInstance( id, name , Paths.get("target/remotes"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public EditableRepositoryGroup createRepositoryGroup(String id, String name) {
        try
        {
            return BasicRepositoryGroup.newFilesystemInstance( id, name, Paths.get( "target/groups" ) );
        }
        catch ( IOException e )
        {
            throw new RuntimeException( );
        }

    }

    @Override
    public ManagedRepository createManagedInstance( ManagedRepositoryConfiguration configuration ) throws RepositoryException {
        BasicManagedRepository managedRepository = null;
        try {
            managedRepository = BasicManagedRepository.newFilesystemInstance(configuration.getId(), configuration.getName(), Paths.get("target/repositories").resolve(configuration.getId()));
        } catch (IOException e) {
            throw new RepositoryException(e);
        }
        updateManagedInstance( managedRepository, configuration );
        return managedRepository;
    }


    @Override
    public void updateManagedInstance( EditableManagedRepository managedRepository, ManagedRepositoryConfiguration configuration ) throws RepositoryException
    {
        try
        {
            managedRepository.setName( managedRepository.getPrimaryLocale(), configuration.getName( ) );
            managedRepository.setLocation( new URI( configuration.getLocation( )==null ?"" : configuration.getLocation() ) );
            managedRepository.setBaseUri( new URI( "" ) );
            managedRepository.setBlocksRedeployment( configuration.isBlockRedeployments( ) );
            managedRepository.setDescription( managedRepository.getPrimaryLocale(), configuration.getDescription( ) );
            managedRepository.setLayout( configuration.getLayout( ) );
            managedRepository.setScanned( configuration.isScanned( ) );
            managedRepository.setSchedulingDefinition( configuration.getRefreshCronExpression( ) );
            if (configuration.isReleases()) {
                managedRepository.addActiveReleaseScheme( ReleaseScheme.RELEASE );
            }
            if (configuration.isSnapshots()) {
                managedRepository.addActiveReleaseScheme( ReleaseScheme.SNAPSHOT );
            }
            ArtifactCleanupFeature acf = managedRepository.getFeature( ArtifactCleanupFeature.class );
            acf.setRetentionPeriod( Period.ofDays( configuration.getRetentionPeriod( ) ) );
            acf.setDeleteReleasedSnapshots( configuration.isDeleteReleasedSnapshots( ) );
            acf.setRetentionCount( configuration.getRetentionCount( ) );
            IndexCreationFeature icf = managedRepository.getFeature( IndexCreationFeature.class );
            icf.setIndexPath( new URI( configuration.getIndexDir( ) ) );
            icf.setSkipPackedIndexCreation( configuration.isSkipPackedIndexCreation( ) );
            StagingRepositoryFeature srf = managedRepository.getFeature( StagingRepositoryFeature.class );
            srf.setStageRepoNeeded( configuration.isStageRepoNeeded( ) );
        }
        catch ( Exception e )
        {
            throw new RepositoryException( "Error", e );
        }

    }


    @Override
    public ManagedRepository createStagingInstance( ManagedRepositoryConfiguration configuration ) throws RepositoryException {
        String id = configuration.getId( ) + StagingRepositoryFeature.STAGING_REPO_POSTFIX;
        BasicManagedRepository managedRepository = null;
        try {
            managedRepository = BasicManagedRepository.newFilesystemInstance(id, configuration.getName(), Paths.get("target/repositories").resolve(id));
        } catch (IOException e) {
            throw new RepositoryException(e);
        }
        updateManagedInstance( managedRepository, configuration );
        managedRepository.getFeature( StagingRepositoryFeature.class ).setStageRepoNeeded(false);
        return managedRepository;
    }

    @Override
    public RemoteRepository createRemoteInstance( RemoteRepositoryConfiguration configuration ) throws RepositoryException
    {
        BasicRemoteRepository remoteRepository = null;
        try {
            remoteRepository = BasicRemoteRepository.newFilesystemInstance( configuration.getId( ), configuration.getName( ), Paths.get("target/remotes") );
        } catch (IOException e) {
            throw new RepositoryException(e);
        }
        updateRemoteInstance( remoteRepository, configuration );
        return remoteRepository;
    }

    @SuppressWarnings( "unchecked" )
    @Override
    public void updateRemoteInstance( EditableRemoteRepository remoteRepository, RemoteRepositoryConfiguration configuration ) throws RepositoryException
    {
        try
        {
            remoteRepository.setName( remoteRepository.getPrimaryLocale(), configuration.getName( ) );
            remoteRepository.setBaseUri( new URI( "" ) );
            remoteRepository.setDescription( remoteRepository.getPrimaryLocale(), configuration.getDescription( ) );
            remoteRepository.setLayout( configuration.getLayout( ) );
            remoteRepository.setSchedulingDefinition( configuration.getRefreshCronExpression( ) );
            remoteRepository.setCheckPath( configuration.getCheckPath( ) );
            remoteRepository.setExtraHeaders( configuration.getExtraHeaders( ) );
            remoteRepository.setExtraParameters( configuration.getExtraParameters( ) );
            remoteRepository.setTimeout( Duration.ofSeconds( configuration.getTimeout( ) ) );
            char[] pwd = configuration.getPassword()==null ? "".toCharArray() : configuration.getPassword().toCharArray();
            remoteRepository.setCredentials( new PasswordCredentials( configuration.getUsername( ), pwd ) );
            remoteRepository.setLocation( new URI( configuration.getUrl( )==null ? "" : configuration.getUrl() ) );
            RemoteIndexFeature rif = remoteRepository.getFeature( RemoteIndexFeature.class );
            rif.setDownloadRemoteIndexOnStartup( configuration.isDownloadRemoteIndexOnStartup( ) );
            rif.setDownloadRemoteIndex( configuration.isDownloadRemoteIndex( ) );
            rif.setIndexUri( new URI( configuration.getIndexDir( ) ) );
            rif.setDownloadTimeout( Duration.ofSeconds( configuration.getRemoteDownloadTimeout( ) ) );
            rif.setProxyId( configuration.getRemoteDownloadNetworkProxyId( ) );
        }
        catch ( Exception e )
        {
            throw new RepositoryException( "Error", e );
        }

    }

    @Override
    public RepositoryGroup createRepositoryGroup(RepositoryGroupConfiguration configuration) throws RepositoryException {
        EditableRepositoryGroup group = createRepositoryGroup( configuration.getId( ), configuration.getName( ) );
        updateGroupInstance( group, configuration );
        return group;
    }

    private void updateGroupInstance( EditableRepositoryGroup group, RepositoryGroupConfiguration configuration )
    {
        group.setMergedIndexTTL( configuration.getMergedIndexTtl() );
        group.setSchedulingDefinition( configuration.getCronExpression() );
    }

    @Override
    public void updateRepositoryGroupInstance(EditableRepositoryGroup repositoryGroup, RepositoryGroupConfiguration configuration) throws RepositoryException {

    }

    @Override
    public ManagedRepositoryConfiguration getManagedConfiguration( ManagedRepository managedRepository ) throws RepositoryException
    {
        ManagedRepositoryConfiguration configuration = new ManagedRepositoryConfiguration( );
        configuration.setId( managedRepository.getId( ) );
        configuration.setName(managedRepository.getName());
        configuration.setLocation( managedRepository.getLocation( ) == null ? "" : managedRepository.getLocation().toString( ) );
        configuration.setBlockRedeployments( managedRepository.blocksRedeployments( ) );
        configuration.setDescription( managedRepository.getDescription( ) );
        configuration.setLayout( managedRepository.getLayout( ) );
        configuration.setScanned( managedRepository.isScanned( ) );
        configuration.setRefreshCronExpression( managedRepository.getSchedulingDefinition( ) );
        configuration.setReleases( managedRepository.getActiveReleaseSchemes().contains(ReleaseScheme.RELEASE) );
        configuration.setSnapshots( managedRepository.getActiveReleaseSchemes().contains(ReleaseScheme.SNAPSHOT) );
        ArtifactCleanupFeature acf = managedRepository.getFeature( ArtifactCleanupFeature.class );
        configuration.setRetentionPeriod( acf.getRetentionPeriod( ).getDays( ) );
        configuration.setDeleteReleasedSnapshots( acf.isDeleteReleasedSnapshots( ) );
        configuration.setRetentionCount( acf.getRetentionCount( ) );
        IndexCreationFeature icf = managedRepository.getFeature( IndexCreationFeature.class );
        configuration.setSkipPackedIndexCreation( icf.isSkipPackedIndexCreation( ) );
        configuration.setIndexDir( icf.getIndexPath( ) == null ? "" : icf.getIndexPath().toString( ) );
        StagingRepositoryFeature srf = managedRepository.getFeature( StagingRepositoryFeature.class );
        configuration.setStageRepoNeeded( srf.isStageRepoNeeded( ) );
        return configuration;
    }

    @Override
    public RepositoryGroupConfiguration getRepositoryGroupConfiguration(RepositoryGroup repositoryGroup) throws RepositoryException {
        RepositoryGroupConfiguration cfg = new RepositoryGroupConfiguration();
        cfg.setId(repositoryGroup.getId());
        cfg.setName(repositoryGroup.getName());
        if (repositoryGroup.supportsFeature( IndexCreationFeature.class ))
        {
            IndexCreationFeature indexCreationFeature = repositoryGroup.getFeature( IndexCreationFeature.class );

            cfg.setMergedIndexPath( indexCreationFeature.getIndexPath().toString() );
        }
        cfg.setMergedIndexTtl(repositoryGroup.getMergedIndexTTL());
        cfg.setRepositories(repositoryGroup.getRepositories().stream().map( Repository::getId ).collect( Collectors.toList()));
        cfg.setCronExpression(repositoryGroup.getSchedulingDefinition());
        return cfg;
    }

    @Override
    public void addRepositoryEventHandler( EventHandler<? super RepositoryEvent> eventHandler )
    {
        // do nothing
    }


    @Override
    public RemoteRepositoryConfiguration getRemoteConfiguration( RemoteRepository remoteRepository ) throws RepositoryException
    {
        RemoteRepositoryConfiguration configuration = new RemoteRepositoryConfiguration( );
        configuration.setId( remoteRepository.getId( ) );
        configuration.setName( remoteRepository.getName( ) );
        configuration.setDescription( remoteRepository.getDescription( ) );
        configuration.setLayout( remoteRepository.getLayout( ) );
        configuration.setRefreshCronExpression( remoteRepository.getSchedulingDefinition( ) );
        configuration.setCheckPath( remoteRepository.getCheckPath( ) );
        configuration.setExtraHeaders( remoteRepository.getExtraHeaders( ) );
        configuration.setExtraParameters( remoteRepository.getExtraParameters( ) );
        configuration.setTimeout( (int) remoteRepository.getTimeout( ).getSeconds( ) );
        RepositoryCredentials creds = remoteRepository.getLoginCredentials( );
        if (creds!=null)
        {
            PasswordCredentials pwdCreds = (PasswordCredentials) creds;
            configuration.setUsername( pwdCreds.getUsername( ) );
            configuration.setPassword( new String( pwdCreds.getPassword( ) ) );
        }
        configuration.setUrl( remoteRepository.getLocation( ) == null ? "" : remoteRepository.getLocation().toString( ) );
        RemoteIndexFeature rif = remoteRepository.getFeature( RemoteIndexFeature.class );
        configuration.setDownloadRemoteIndex( rif.isDownloadRemoteIndex( ) );
        configuration.setDownloadRemoteIndexOnStartup( rif.isDownloadRemoteIndexOnStartup( ) );
        configuration.setIndexDir( rif.getIndexUri( )==null ? "" : rif.getIndexUri().toString( ) );
        configuration.setRemoteDownloadNetworkProxyId( rif.getProxyId( ) );
        return configuration;
    }

    @Override
    public void handle(Event event) {

    }
}
