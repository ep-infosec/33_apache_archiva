package org.apache.archiva.maven.proxy;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.archiva.policies.CachedFailuresPolicy;
import org.apache.archiva.policies.ChecksumPolicy;
import org.apache.archiva.policies.PolicyOption;
import org.apache.archiva.policies.PropagateErrorsDownloadPolicy;
import org.apache.archiva.policies.PropagateErrorsOnUpdateDownloadPolicy;
import org.apache.archiva.policies.ProxyDownloadException;
import org.apache.archiva.policies.ReleasesPolicy;
import org.apache.archiva.policies.SnapshotsPolicy;
import org.apache.archiva.repository.content.BaseRepositoryContentLayout;
import org.apache.archiva.repository.content.LayoutException;
import org.apache.archiva.repository.storage.StorageAsset;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.authorization.AuthorizationException;
import org.junit.Test;
import org.mockito.stubbing.Stubber;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

/**
 * ErrorHandlingTest
 *
 *
 */
public class ErrorHandlingTest
    extends AbstractProxyTestCase
{
    private static final String PATH_IN_BOTH_REMOTES_NOT_LOCAL =
        "org/apache/maven/test/get-in-both-proxies/1.0/get-in-both-proxies-1.0.jar";

    private static final String PATH_IN_BOTH_REMOTES_AND_LOCAL =
        "org/apache/maven/test/get-on-multiple-repos/1.0/get-on-multiple-repos-1.0.pom";

    private static final String ID_MOCKED_PROXIED1 = "badproxied1";

    private static final String NAME_MOCKED_PROXIED1 = "Bad Proxied 1";

    private static final String ID_MOCKED_PROXIED2 = "badproxied2";

    private static final String NAME_MOCKED_PROXIED2 = "Bad Proxied 2";

    @Test
    public void testPropagateErrorImmediatelyWithErrorThenSuccess()
        throws Exception
    {
        String path = PATH_IN_BOTH_REMOTES_NOT_LOCAL;
        Path expectedFile = setupRepositoriesWithLocalFileNotPresent( path );

        createMockedProxyConnector( ID_MOCKED_PROXIED1, NAME_MOCKED_PROXIED1, PropagateErrorsDownloadPolicy.STOP );
        saveConnector( ID_DEFAULT_MANAGED, ID_PROXIED2, false );

        simulateGetError( path, expectedFile, Arrays.asList( createTransferException( ) ) );

        confirmSingleFailure( path, ID_MOCKED_PROXIED1 );
    }

    @Test
    public void testPropagateErrorImmediatelyWithNotFoundThenError()
        throws Exception
    {
        String path = PATH_IN_BOTH_REMOTES_NOT_LOCAL;
        Path expectedFile = setupRepositoriesWithLocalFileNotPresent( path );

        createMockedProxyConnector( ID_MOCKED_PROXIED1, NAME_MOCKED_PROXIED1, PropagateErrorsDownloadPolicy.STOP );
        createMockedProxyConnector( ID_MOCKED_PROXIED2, NAME_MOCKED_PROXIED2, PropagateErrorsDownloadPolicy.STOP );

        simulateGetError( path, expectedFile, Arrays.asList( createResourceNotFoundException( ), createTransferException( ) ) );

        confirmSingleFailure( path, ID_MOCKED_PROXIED2 );
    }

    @Test
    public void testPropagateErrorImmediatelyWithSuccessThenError()
        throws Exception
    {
        String path = PATH_IN_BOTH_REMOTES_NOT_LOCAL;
        Path expectedFile = setupRepositoriesWithLocalFileNotPresent( path );

        saveConnector( ID_DEFAULT_MANAGED, ID_PROXIED1, false );

        createMockedProxyConnector( ID_MOCKED_PROXIED2, NAME_MOCKED_PROXIED2, PropagateErrorsDownloadPolicy.STOP );

        confirmSuccess( path, expectedFile, REPOPATH_PROXIED1 );
    }

    @Test
    public void testPropagateErrorImmediatelyWithNotFoundThenSuccess()
        throws Exception
    {
        String path = PATH_IN_BOTH_REMOTES_NOT_LOCAL;
        Path expectedFile = setupRepositoriesWithLocalFileNotPresent( path );

        createMockedProxyConnector( ID_MOCKED_PROXIED1, NAME_MOCKED_PROXIED1, PropagateErrorsDownloadPolicy.STOP );

        saveConnector( ID_DEFAULT_MANAGED, ID_PROXIED2, false  );

        simulateGetError( path, expectedFile, Arrays.asList( createResourceNotFoundException( ) ) );

        confirmSuccess( path, expectedFile, REPOPATH_PROXIED2 );
    }

    @Test
    public void testPropagateErrorAtEndWithErrorThenSuccess()
        throws Exception
    {
        String path = PATH_IN_BOTH_REMOTES_NOT_LOCAL;
        Path expectedFile = setupRepositoriesWithLocalFileNotPresent( path );

        createMockedProxyConnector( ID_MOCKED_PROXIED1, NAME_MOCKED_PROXIED1, PropagateErrorsDownloadPolicy.STOP );

        saveConnector( ID_DEFAULT_MANAGED, ID_PROXIED2, false  );

        simulateGetError( path, expectedFile, Arrays.asList( createTransferException( ) ) );

        confirmSingleFailure( path, ID_MOCKED_PROXIED1 );
    }

    @Test
    public void testPropagateErrorAtEndWithSuccessThenError()
        throws Exception
    {
        String path = PATH_IN_BOTH_REMOTES_NOT_LOCAL;
        Path expectedFile = setupRepositoriesWithLocalFileNotPresent( path );

        saveConnector( ID_DEFAULT_MANAGED, ID_PROXIED1, false  );

        createMockedProxyConnector( ID_MOCKED_PROXIED2, NAME_MOCKED_PROXIED2, PropagateErrorsDownloadPolicy.QUEUE );

        confirmSuccess( path, expectedFile, REPOPATH_PROXIED1 );
    }

    @Test
    public void testPropagateErrorAtEndWithNotFoundThenError()
        throws Exception
    {
        String path = PATH_IN_BOTH_REMOTES_NOT_LOCAL;
        Path expectedFile = setupRepositoriesWithLocalFileNotPresent( path );

        createMockedProxyConnector( ID_MOCKED_PROXIED1, NAME_MOCKED_PROXIED1, PropagateErrorsDownloadPolicy.QUEUE );

        createMockedProxyConnector( ID_MOCKED_PROXIED2, NAME_MOCKED_PROXIED2, PropagateErrorsDownloadPolicy.QUEUE );

        simulateGetError( path, expectedFile, Arrays.asList( createResourceNotFoundException( ), createTransferException( ) ) );

        confirmSingleFailure( path, ID_MOCKED_PROXIED2 );
    }

    @Test
    public void testPropagateErrorAtEndWithErrorThenNotFound()
        throws Exception
    {
        String path = PATH_IN_BOTH_REMOTES_NOT_LOCAL;
        Path expectedFile = setupRepositoriesWithLocalFileNotPresent( path );

        createMockedProxyConnector( ID_MOCKED_PROXIED1, NAME_MOCKED_PROXIED1, PropagateErrorsDownloadPolicy.QUEUE );

        createMockedProxyConnector( ID_MOCKED_PROXIED2, NAME_MOCKED_PROXIED2, PropagateErrorsDownloadPolicy.QUEUE );

        simulateGetError( path, expectedFile, Arrays.asList( createTransferException( ), createResourceNotFoundException( ) ) );

        confirmSingleFailure( path, ID_MOCKED_PROXIED1 );
    }

    @Test
    public void testPropagateErrorAtEndWithErrorThenError()
        throws Exception
    {
        String path = PATH_IN_BOTH_REMOTES_NOT_LOCAL;
        Path expectedFile = setupRepositoriesWithLocalFileNotPresent( path );

        createMockedProxyConnector( ID_MOCKED_PROXIED1, NAME_MOCKED_PROXIED1, PropagateErrorsDownloadPolicy.QUEUE );

        createMockedProxyConnector( ID_MOCKED_PROXIED2, NAME_MOCKED_PROXIED2, PropagateErrorsDownloadPolicy.QUEUE );

        simulateGetError( path, expectedFile, Arrays.asList( createTransferException( ), createTransferException( ) ) );

        confirmFailures( path, new String[]{ID_MOCKED_PROXIED1, ID_MOCKED_PROXIED2} );
    }

    @Test
    public void testPropagateErrorAtEndWithNotFoundThenSuccess()
        throws Exception
    {
        String path = PATH_IN_BOTH_REMOTES_NOT_LOCAL;
        Path expectedFile = setupRepositoriesWithLocalFileNotPresent( path );

        createMockedProxyConnector( ID_MOCKED_PROXIED1, NAME_MOCKED_PROXIED1, PropagateErrorsDownloadPolicy.QUEUE );

        saveConnector( ID_DEFAULT_MANAGED, ID_PROXIED2, false  );

        simulateGetError( path, expectedFile, Arrays.asList( createResourceNotFoundException( ) ) );

        confirmSuccess( path, expectedFile, REPOPATH_PROXIED2 );
    }

    @Test
    public void testIgnoreErrorWithErrorThenSuccess()
        throws Exception
    {
        String path = PATH_IN_BOTH_REMOTES_NOT_LOCAL;
        Path expectedFile = setupRepositoriesWithLocalFileNotPresent( path );

        createMockedProxyConnector( ID_MOCKED_PROXIED1, NAME_MOCKED_PROXIED1, PropagateErrorsDownloadPolicy.IGNORE );

        saveConnector( ID_DEFAULT_MANAGED, ID_PROXIED2, false  );

        simulateGetError( path, expectedFile, Arrays.asList( createTransferException( ) ) );

        confirmSuccess( path, expectedFile, REPOPATH_PROXIED2 );
    }

    @Test
    public void testIgnoreErrorWithSuccessThenError()
        throws Exception
    {
        String path = PATH_IN_BOTH_REMOTES_NOT_LOCAL;
        Path expectedFile = setupRepositoriesWithLocalFileNotPresent( path );

        saveConnector( ID_DEFAULT_MANAGED, ID_PROXIED1, false  );

        createMockedProxyConnector( ID_MOCKED_PROXIED2, NAME_MOCKED_PROXIED2, PropagateErrorsDownloadPolicy.IGNORE );

        confirmSuccess( path, expectedFile, REPOPATH_PROXIED1 );
    }

    @Test
    public void testIgnoreErrorWithNotFoundThenError()
        throws Exception
    {
        String path = PATH_IN_BOTH_REMOTES_NOT_LOCAL;
        Path expectedFile = setupRepositoriesWithLocalFileNotPresent( path );

        createMockedProxyConnector( ID_MOCKED_PROXIED1, NAME_MOCKED_PROXIED1, PropagateErrorsDownloadPolicy.IGNORE );

        createMockedProxyConnector( ID_MOCKED_PROXIED2, NAME_MOCKED_PROXIED2, PropagateErrorsDownloadPolicy.IGNORE );

        simulateGetError( path, expectedFile, Arrays.asList( createResourceNotFoundException( ), createTransferException( ) ) );

        confirmNotDownloadedNoError( path );
    }

    @Test
    public void testIgnoreErrorWithErrorThenNotFound()
        throws Exception
    {
        String path = PATH_IN_BOTH_REMOTES_NOT_LOCAL;
        Path expectedFile = setupRepositoriesWithLocalFileNotPresent( path );

        createMockedProxyConnector( ID_MOCKED_PROXIED1, NAME_MOCKED_PROXIED1, PropagateErrorsDownloadPolicy.IGNORE );

        createMockedProxyConnector( ID_MOCKED_PROXIED2, NAME_MOCKED_PROXIED2, PropagateErrorsDownloadPolicy.IGNORE );

        simulateGetError( path, expectedFile, Arrays.asList( createTransferException( ), createResourceNotFoundException( ) ) );

        confirmNotDownloadedNoError( path );
    }

    @Test
    public void testIgnoreErrorWithErrorThenError()
        throws Exception
    {
        String path = PATH_IN_BOTH_REMOTES_NOT_LOCAL;
        Path expectedFile = setupRepositoriesWithLocalFileNotPresent( path );

        createMockedProxyConnector( ID_MOCKED_PROXIED1, NAME_MOCKED_PROXIED1, PropagateErrorsDownloadPolicy.IGNORE );

        createMockedProxyConnector( ID_MOCKED_PROXIED2, NAME_MOCKED_PROXIED2, PropagateErrorsDownloadPolicy.IGNORE );

        simulateGetError( path, expectedFile, Arrays.asList( createTransferException( ), createTransferException( ) ) );

        confirmNotDownloadedNoError( path );
    }

    @Test
    public void testPropagateOnUpdateAlwaysArtifactNotPresent()
        throws Exception
    {
        String path = PATH_IN_BOTH_REMOTES_NOT_LOCAL;
        Path expectedFile = setupRepositoriesWithLocalFileNotPresent( path );

        createMockedProxyConnector( ID_MOCKED_PROXIED1, NAME_MOCKED_PROXIED1, PropagateErrorsDownloadPolicy.STOP,
                                    PropagateErrorsOnUpdateDownloadPolicy.ALWAYS );
        createMockedProxyConnector( ID_MOCKED_PROXIED2, NAME_MOCKED_PROXIED2, PropagateErrorsDownloadPolicy.STOP,
                                    PropagateErrorsOnUpdateDownloadPolicy.ALWAYS );

        simulateGetError( path, expectedFile, Arrays.asList( createTransferException( ) ) );

        confirmSingleFailure( path, ID_MOCKED_PROXIED1 );
    }

    @Test
    public void testPropagateOnUpdateAlwaysArtifactPresent()
        throws Exception
    {
        String path = PATH_IN_BOTH_REMOTES_AND_LOCAL;
        Path expectedFile = setupRepositoriesWithLocalFilePresent( path );

        createMockedProxyConnector( ID_MOCKED_PROXIED1, NAME_MOCKED_PROXIED1, PropagateErrorsDownloadPolicy.STOP,
                                    PropagateErrorsOnUpdateDownloadPolicy.ALWAYS );
        createMockedProxyConnector( ID_MOCKED_PROXIED2, NAME_MOCKED_PROXIED2, PropagateErrorsDownloadPolicy.STOP,
                                    PropagateErrorsOnUpdateDownloadPolicy.ALWAYS );

        simulateGetIfNewerError( path, expectedFile, createTransferException() );

        confirmSingleFailure( path, ID_MOCKED_PROXIED1 );
    }

    @Test
    public void testPropagateOnUpdateAlwaysQueueArtifactNotPresent()
        throws Exception
    {
        String path = PATH_IN_BOTH_REMOTES_NOT_LOCAL;
        Path expectedFile = setupRepositoriesWithLocalFileNotPresent( path );

        createMockedProxyConnector( ID_MOCKED_PROXIED1, NAME_MOCKED_PROXIED1, PropagateErrorsDownloadPolicy.QUEUE,
                                    PropagateErrorsOnUpdateDownloadPolicy.ALWAYS );
        createMockedProxyConnector( ID_MOCKED_PROXIED2, NAME_MOCKED_PROXIED2, PropagateErrorsDownloadPolicy.QUEUE,
                                    PropagateErrorsOnUpdateDownloadPolicy.ALWAYS );

        simulateGetError( path, expectedFile, Arrays.asList( createTransferException( ), createTransferException( ) ) );

        confirmFailures( path, new String[] { ID_MOCKED_PROXIED1, ID_MOCKED_PROXIED2 } );
    }

    @Test
    public void testPropagateOnUpdateAlwaysQueueArtifactPresent()
        throws Exception
    {
        String path = PATH_IN_BOTH_REMOTES_AND_LOCAL;
        Path expectedFile = setupRepositoriesWithLocalFilePresent( path );

        createMockedProxyConnector( ID_MOCKED_PROXIED1, NAME_MOCKED_PROXIED1, PropagateErrorsDownloadPolicy.QUEUE,
                                    PropagateErrorsOnUpdateDownloadPolicy.ALWAYS );
        createMockedProxyConnector( ID_MOCKED_PROXIED2, NAME_MOCKED_PROXIED2, PropagateErrorsDownloadPolicy.QUEUE,
                                    PropagateErrorsOnUpdateDownloadPolicy.ALWAYS );

        simulateGetIfNewerError( path, expectedFile, createTransferException() );
        simulateGetIfNewerError( path, expectedFile, createTransferException() );

        confirmFailures( path, new String[] { ID_MOCKED_PROXIED1, ID_MOCKED_PROXIED2 } );
    }

    @Test
    public void testPropagateOnUpdateAlwaysIgnoreArtifactNotPresent()
        throws Exception
    {
        String path = PATH_IN_BOTH_REMOTES_NOT_LOCAL;
        Path expectedFile = setupRepositoriesWithLocalFileNotPresent( path );

        createMockedProxyConnector( ID_MOCKED_PROXIED1, NAME_MOCKED_PROXIED1, PropagateErrorsDownloadPolicy.IGNORE,
                                    PropagateErrorsOnUpdateDownloadPolicy.ALWAYS );
        createMockedProxyConnector( ID_MOCKED_PROXIED2, NAME_MOCKED_PROXIED2, PropagateErrorsDownloadPolicy.IGNORE,
                                    PropagateErrorsOnUpdateDownloadPolicy.ALWAYS );

        simulateGetError( path, expectedFile, Arrays.asList( createTransferException( ), createTransferException( ) ) );

        confirmNotDownloadedNoError( path );
    }

    @Test
    public void testPropagateOnUpdateAlwaysIgnoreArtifactPresent()
        throws Exception
    {
        String path = PATH_IN_BOTH_REMOTES_AND_LOCAL;
        Path  expectedFile = setupRepositoriesWithLocalFilePresent( path );

        createMockedProxyConnector( ID_MOCKED_PROXIED1, NAME_MOCKED_PROXIED1, PropagateErrorsDownloadPolicy.IGNORE,
                                    PropagateErrorsOnUpdateDownloadPolicy.ALWAYS );
        createMockedProxyConnector( ID_MOCKED_PROXIED2, NAME_MOCKED_PROXIED2, PropagateErrorsDownloadPolicy.IGNORE,
                                    PropagateErrorsOnUpdateDownloadPolicy.ALWAYS );

        simulateGetIfNewerError( path, expectedFile, createTransferException() );
        simulateGetIfNewerError( path, expectedFile, createTransferException() );

        confirmNotDownloadedNoError( path );
        assertTrue( Files.exists(expectedFile) );
    }

    @Test
    public void testPropagateOnUpdateNotPresentArtifactNotPresent()
        throws Exception
    {
        String path = PATH_IN_BOTH_REMOTES_NOT_LOCAL;
        Path expectedFile = setupRepositoriesWithLocalFileNotPresent( path );

        createMockedProxyConnector( ID_MOCKED_PROXIED1, NAME_MOCKED_PROXIED1, PropagateErrorsDownloadPolicy.STOP,
                                    PropagateErrorsOnUpdateDownloadPolicy.NOT_PRESENT );
        createMockedProxyConnector( ID_MOCKED_PROXIED2, NAME_MOCKED_PROXIED2, PropagateErrorsDownloadPolicy.STOP,
                                    PropagateErrorsOnUpdateDownloadPolicy.NOT_PRESENT );

        simulateGetError( path, expectedFile, Arrays.asList( createTransferException( ) ) );

        confirmSingleFailure( path, ID_MOCKED_PROXIED1 );
    }

    @Test
    public void testPropagateOnUpdateNotPresentArtifactPresent()
        throws Exception
    {
        String path = PATH_IN_BOTH_REMOTES_AND_LOCAL;
        Path expectedFile = setupRepositoriesWithLocalFilePresent( path );

        createMockedProxyConnector( ID_MOCKED_PROXIED1, NAME_MOCKED_PROXIED1, PropagateErrorsDownloadPolicy.STOP,
                                    PropagateErrorsOnUpdateDownloadPolicy.NOT_PRESENT );
        createMockedProxyConnector( ID_MOCKED_PROXIED2, NAME_MOCKED_PROXIED2, PropagateErrorsDownloadPolicy.STOP,
                                    PropagateErrorsOnUpdateDownloadPolicy.NOT_PRESENT );

        simulateGetIfNewerError( path, expectedFile, createTransferException() );

        confirmNotDownloadedNoError( path );
        assertTrue( Files.exists(expectedFile) );
    }

    @Test
    public void testPropagateOnUpdateNotPresentQueueArtifactNotPresent()
        throws Exception
    {
        String path = PATH_IN_BOTH_REMOTES_NOT_LOCAL;
        Path expectedFile = setupRepositoriesWithLocalFileNotPresent( path );

        createMockedProxyConnector( ID_MOCKED_PROXIED1, NAME_MOCKED_PROXIED1, PropagateErrorsDownloadPolicy.QUEUE,
                                    PropagateErrorsOnUpdateDownloadPolicy.NOT_PRESENT );
        createMockedProxyConnector( ID_MOCKED_PROXIED2, NAME_MOCKED_PROXIED2, PropagateErrorsDownloadPolicy.QUEUE,
                                    PropagateErrorsOnUpdateDownloadPolicy.NOT_PRESENT );

        simulateGetError( path, expectedFile, Arrays.asList( createTransferException( ), createTransferException( ) ) );

        confirmFailures( path, new String[] { ID_MOCKED_PROXIED1, ID_MOCKED_PROXIED2 } );
    }

    @Test
    public void testPropagateOnUpdateNotPresentQueueArtifactPresent()
        throws Exception
    {
        String path = PATH_IN_BOTH_REMOTES_AND_LOCAL;
        Path expectedFile = setupRepositoriesWithLocalFilePresent( path );

        createMockedProxyConnector( ID_MOCKED_PROXIED1, NAME_MOCKED_PROXIED1, PropagateErrorsDownloadPolicy.QUEUE,
                                    PropagateErrorsOnUpdateDownloadPolicy.NOT_PRESENT );
        createMockedProxyConnector( ID_MOCKED_PROXIED2, NAME_MOCKED_PROXIED2, PropagateErrorsDownloadPolicy.QUEUE,
                                    PropagateErrorsOnUpdateDownloadPolicy.NOT_PRESENT );

        simulateGetIfNewerError( path, expectedFile, createTransferException() );
        simulateGetIfNewerError( path, expectedFile, createTransferException() );

        confirmNotDownloadedNoError( path );
        assertTrue( Files.exists(expectedFile));
    }

    @Test
    public void testPropagateOnUpdateNotPresentIgnoreArtifactNotPresent()
        throws Exception
    {
        String path = PATH_IN_BOTH_REMOTES_NOT_LOCAL;
        Path expectedFile = setupRepositoriesWithLocalFileNotPresent( path );

        createMockedProxyConnector( ID_MOCKED_PROXIED1, NAME_MOCKED_PROXIED1, PropagateErrorsDownloadPolicy.IGNORE,
                                    PropagateErrorsOnUpdateDownloadPolicy.NOT_PRESENT );
        createMockedProxyConnector( ID_MOCKED_PROXIED2, NAME_MOCKED_PROXIED2, PropagateErrorsDownloadPolicy.IGNORE,
                                    PropagateErrorsOnUpdateDownloadPolicy.NOT_PRESENT );

        simulateGetError( path, expectedFile, Arrays.asList( createTransferException( ), createTransferException( ) ) );
        confirmNotDownloadedNoError( path );
    }

    @Test
    public void testPropagateOnUpdateNotPresentIgnoreArtifactPresent()
        throws Exception
    {
        String path = PATH_IN_BOTH_REMOTES_AND_LOCAL;
        Path expectedFile = setupRepositoriesWithLocalFilePresent( path );

        createMockedProxyConnector( ID_MOCKED_PROXIED1, NAME_MOCKED_PROXIED1, PropagateErrorsDownloadPolicy.IGNORE,
                                    PropagateErrorsOnUpdateDownloadPolicy.NOT_PRESENT );
        createMockedProxyConnector( ID_MOCKED_PROXIED2, NAME_MOCKED_PROXIED2, PropagateErrorsDownloadPolicy.IGNORE,
                                    PropagateErrorsOnUpdateDownloadPolicy.NOT_PRESENT );

        simulateGetIfNewerError( path, expectedFile, createTransferException() );
        simulateGetIfNewerError( path, expectedFile, createTransferException() );

        confirmNotDownloadedNoError( path );
        assertTrue( Files.exists(expectedFile));
    }

    // ------------------------------------------
    // HELPER METHODS
    // ------------------------------------------

    private void createMockedProxyConnector( String id, String name, PolicyOption errorPolicy )
    {
        saveRemoteRepositoryConfig( id, name, "http://bad.machine.com/repo/", "default" );
        saveConnector( ID_DEFAULT_MANAGED, id, ChecksumPolicy.FIX, ReleasesPolicy.ALWAYS, SnapshotsPolicy.ALWAYS,
                       CachedFailuresPolicy.NO, errorPolicy, false );
    }

    private void createMockedProxyConnector( String id, String name, PolicyOption errorPolicy, PolicyOption errorOnUpdatePolicy )
    {
        saveRemoteRepositoryConfig( id, name, "http://bad.machine.com/repo/", "default" );
        saveConnector( ID_DEFAULT_MANAGED, id, ChecksumPolicy.FIX, ReleasesPolicy.ALWAYS, SnapshotsPolicy.ALWAYS,
                       CachedFailuresPolicy.NO, errorPolicy, errorOnUpdatePolicy, false );
    }

    private Path setupRepositoriesWithLocalFileNotPresent( String path )
        throws Exception
    {
        setupTestableManagedRepository( path );

        Path file = managedDefaultDir.resolve( path );

        assertNotExistsInManagedDefaultRepo( file );

        return file;
    }

    private Path setupRepositoriesWithLocalFilePresent( String path )
        throws Exception
    {
        setupTestableManagedRepository( path );

        Path file = managedDefaultDir.resolve( path );

        assertTrue( Files.exists(file) );

        return file;
    }

    private void simulateGetError( String path, Path expectedFile, List<Exception> throwables )
        throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException
    {
        Stubber stubber = doThrow( throwables.get( 0 ) );
        if (throwables.size()>1) {
            for(int i=1; i<throwables.size(); i++)
            {
                stubber = stubber.doThrow( throwables.get( i ) );
            }
        }
        stubber.when( wagonMock ).get( eq( path ), any( ) );
    }

    private void simulateGetIfNewerError( String path, Path expectedFile, TransferFailedException exception )
        throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException, IOException
    {
        doThrow( exception ).when( wagonMock ).getIfNewer( eq( path ), any( ), eq( Files.getLastModifiedTime( expectedFile ).toMillis( ) ) );
    }

    private Path createExpectedTempFile( Path expectedFile )
    {
        return managedDefaultDir.resolve(expectedFile.getFileName().toString() + ".tmp" ).toAbsolutePath();
    }

    private void confirmSingleFailure( String path, String id )
        throws LayoutException
    {
        confirmFailures( path, new String[]{id} );
    }

    private void confirmFailures( String path, String[] ids )
        throws LayoutException
    {
        // Attempt the proxy fetch.
        StorageAsset downloadedFile = null;
        try
        {
            BaseRepositoryContentLayout layout = managedDefaultRepository.getLayout( BaseRepositoryContentLayout.class );
            downloadedFile = proxyHandler.fetchFromProxies( managedDefaultRepository.getRepository(),
                layout.getArtifact( path ) );
            fail( "Proxy should not have succeeded" );
        }
        catch ( ProxyDownloadException e )
        {
            assertEquals( ids.length, e.getFailures().size() );
            for ( String id : ids )
            {
                assertTrue( e.getFailures().keySet().contains( id ) );
            }
        }
        assertNotDownloaded( downloadedFile );
    }

    private void confirmSuccess( String path, Path expectedFile, String basedir )
        throws Exception
    {
        StorageAsset downloadedFile = performDownload( path );

        Path proxied1File = Paths.get( basedir, path );
        assertFileEquals( expectedFile, downloadedFile.getFilePath(), proxied1File );
    }

    private void confirmNotDownloadedNoError( String path )
        throws Exception
    {
        StorageAsset downloadedFile = performDownload( path );

        assertNotDownloaded( downloadedFile );
    }

    private StorageAsset performDownload( String path )
        throws ProxyDownloadException, LayoutException
    {
        // Attempt the proxy fetch.
        BaseRepositoryContentLayout layout = managedDefaultRepository.getLayout( BaseRepositoryContentLayout.class );

        StorageAsset downloadedFile = proxyHandler.fetchFromProxies( managedDefaultRepository.getRepository(),
            layout.getArtifact( path ) );
        return downloadedFile;
    }

    private static TransferFailedException createTransferException()
    {
        return new TransferFailedException( "test download exception" );
    }

    private static ResourceDoesNotExistException createResourceNotFoundException()
    {
        return new ResourceDoesNotExistException( "test download not found" );
    }
}
