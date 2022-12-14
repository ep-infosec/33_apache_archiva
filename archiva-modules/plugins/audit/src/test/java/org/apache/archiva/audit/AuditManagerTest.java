package org.apache.archiva.audit;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import junit.framework.TestCase;
import org.apache.archiva.configuration.model.ManagedRepositoryConfiguration;
import org.apache.archiva.metadata.model.facets.AuditEvent;
import org.apache.archiva.metadata.repository.MetadataRepository;
import org.apache.archiva.metadata.repository.RepositorySession;
import org.apache.archiva.metadata.repository.RepositorySessionFactory;
import org.apache.archiva.test.utils.ArchivaBlockJUnit4ClassRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(ArchivaBlockJUnit4ClassRunner.class)
public class AuditManagerTest
    extends TestCase
{
    private DefaultAuditManager auditManager;

    private MetadataRepository metadataRepository;

    private RepositorySessionFactory repositorySessionFactory;

    private RepositorySession session;

    private static final String AUDIT_EVENT_BASE = "2010/01/18/123456.";

    private static final String TEST_REPO_ID = "test-repo";

    private static final String TEST_REPO_ID_2 = "repo2";

    private static final String TEST_USER = "test_user";

    private static final String TEST_RESOURCE_BASE = "test/resource";

    private static final String TEST_IP_ADDRESS = "127.0.0.1";

    private static final SimpleDateFormat TIMESTAMP_FORMAT = createTimestampFormat();

    private static final DecimalFormat MILLIS_FORMAT = new DecimalFormat( "000" );

    private static SimpleDateFormat createTimestampFormat()
    {
        SimpleDateFormat fmt = new SimpleDateFormat( AuditEvent.TIMESTAMP_FORMAT );
        fmt.setTimeZone( TimeZone.getTimeZone( "UTC" ) );
        return fmt;
    }

    @Override
    @Before
    public void setUp()
        throws Exception
    {
        super.setUp();

        auditManager = new DefaultAuditManager();

        metadataRepository = mock( MetadataRepository.class );

        repositorySessionFactory = mock(RepositorySessionFactory.class);

        session = mock( RepositorySession.class );

        auditManager.setRepositorySessionFactory( repositorySessionFactory );

        ManagedRepositoryConfiguration repository = new ManagedRepositoryConfiguration();
        repository.setId( TEST_REPO_ID );
        repository.setLocation( "" );
    }

    @Test
    public void testGetMostRecentEvents()
        throws Exception
    {
        int numEvents = 11;
        List<AuditEvent> expectedEvents = new ArrayList<>( numEvents );
        for ( int i = 0; i < numEvents; i++ )
        {
            AuditEvent event = createEvent( AUDIT_EVENT_BASE + MILLIS_FORMAT.format( i ) );
            expectedEvents.add( event );
        }

        when( repositorySessionFactory.createSession() ).thenReturn( session );
            when(metadataRepository.getMetadataFacets(session, TEST_REPO_ID, AuditEvent.FACET_ID)).thenReturn(
                    getEventNames(expectedEvents));

            for (AuditEvent event : expectedEvents.subList(1, expectedEvents.size())) {
                when(
                        metadataRepository.getMetadataFacet(session, TEST_REPO_ID, AuditEvent.FACET_ID, event.getName())).thenReturn(
                        event);
            }

        List<AuditEvent> events =
            auditManager.getMostRecentAuditEvents( metadataRepository, Collections.singletonList( TEST_REPO_ID ) );
        assertNotNull( events );
        assertEquals( numEvents - 1, events.size() );
        int expectedTimestampCounter = numEvents - 1;
        for ( AuditEvent event : events )
        {
            String num = MILLIS_FORMAT.format( expectedTimestampCounter );
            assertTestEvent( event, AUDIT_EVENT_BASE + num, getDefaultTestResourceName( num ) );
            expectedTimestampCounter--;
        }

    }

    @Test
    public void testGetMostRecentEventsLessThan10()
        throws Exception
    {
        int numEvents = 5;
        List<AuditEvent> expectedEvents = new ArrayList<>( numEvents );
        for ( int i = 0; i < numEvents; i++ )
        {
            expectedEvents.add( createEvent( AUDIT_EVENT_BASE + MILLIS_FORMAT.format( i ) ) );
        }

        when( repositorySessionFactory.createSession() ).thenReturn( session );
            when(metadataRepository.getMetadataFacets(session, TEST_REPO_ID, AuditEvent.FACET_ID)).thenReturn(
                    getEventNames(expectedEvents));
            for (AuditEvent event : expectedEvents) {
                when(
                        metadataRepository.getMetadataFacet(session, TEST_REPO_ID, AuditEvent.FACET_ID, event.getName())).thenReturn(
                        event);
            }

        List<AuditEvent> events =
            auditManager.getMostRecentAuditEvents( metadataRepository, Collections.singletonList( TEST_REPO_ID ) );
        assertNotNull( events );
        assertEquals( numEvents, events.size() );
        int expectedTimestampCounter = numEvents - 1;
        for ( AuditEvent event : events )
        {
            String num = MILLIS_FORMAT.format( expectedTimestampCounter );
            assertTestEvent( event, AUDIT_EVENT_BASE + num, getDefaultTestResourceName( num ) );
            expectedTimestampCounter--;
        }

    }

    @Test
    public void testGetMostRecentEventsInterleavedRepositories()
        throws Exception
    {
        int numEvents = 11;
        Map<String, List<String>> eventNames = new LinkedHashMap<>( );
        List<AuditEvent> events = new ArrayList<>();
        eventNames.put( TEST_REPO_ID, new ArrayList<>( ) );
        eventNames.put( TEST_REPO_ID_2, new ArrayList<>( ) );
        for ( int i = 0; i < numEvents; i++ )
        {
            String repositoryId = i % 2 == 0 ? TEST_REPO_ID : TEST_REPO_ID_2;
            String num = MILLIS_FORMAT.format( i );
            AuditEvent event = createEvent( repositoryId, AUDIT_EVENT_BASE + num, getDefaultTestResourceName( num ) );
            events.add( event );
            eventNames.get( repositoryId ).add( event.getName() );
        }
        when( repositorySessionFactory.createSession() ).thenReturn( session );
            when(metadataRepository.getMetadataFacets(session, TEST_REPO_ID, AuditEvent.FACET_ID)).thenReturn(
                    eventNames.get(TEST_REPO_ID));
            when(metadataRepository.getMetadataFacets(session, TEST_REPO_ID_2, AuditEvent.FACET_ID)).thenReturn(
                    eventNames.get(TEST_REPO_ID_2));

            for (AuditEvent event : events.subList(1, events.size())) {
                when(metadataRepository.getMetadataFacet(session, event.getRepositoryId(),
                        AuditEvent.FACET_ID, event.getName())).thenReturn(event);
            }

        events =
            auditManager.getMostRecentAuditEvents( metadataRepository, Arrays.asList( TEST_REPO_ID, TEST_REPO_ID_2 ) );
        assertNotNull( events );
        assertEquals( numEvents - 1, events.size() );
        int expectedTimestampCounter = numEvents - 1;
        for ( AuditEvent event : events )
        {
            String num = MILLIS_FORMAT.format( expectedTimestampCounter );
            String expectedRepoId = expectedTimestampCounter % 2 == 0 ? TEST_REPO_ID : TEST_REPO_ID_2;
            assertTestEvent( event, expectedRepoId, AUDIT_EVENT_BASE + num, getDefaultTestResourceName( num ) );
            expectedTimestampCounter--;
        }

    }

    @Test
    public void testGetMostRecentEventsWhenEmpty()
        throws Exception

    {
        when( repositorySessionFactory.createSession() ).thenReturn( session );
            when(metadataRepository.getMetadataFacets(session, TEST_REPO_ID, AuditEvent.FACET_ID)).thenReturn(
                    Collections.emptyList());

        assertTrue( auditManager.getMostRecentAuditEvents( metadataRepository,
                                                           Collections.singletonList( TEST_REPO_ID ) ).isEmpty() );

    }

    @Test
    public void testAddAuditEvent()
        throws Exception

    {
        AuditEvent event = createEvent( new Date() );

        when( repositorySessionFactory.createSession() ).thenReturn( session );
            metadataRepository.addMetadataFacet(session, TEST_REPO_ID, event);


        auditManager.addAuditEvent( metadataRepository, event );

    }

    @Test
    public void testAddAuditEventNoRepositoryId()
        throws Exception
    {
        AuditEvent event = createEvent( new Date() );
        event.setRepositoryId( null );

        // should just be ignored

        auditManager.addAuditEvent( metadataRepository, event );
    }

    @Test
    public void testDeleteStats()
        throws Exception

    {

        when( repositorySessionFactory.createSession() ).thenReturn( session );

        metadataRepository.removeMetadataFacets(session, TEST_REPO_ID, AuditEvent.FACET_ID);

        auditManager.deleteAuditEvents( metadataRepository, TEST_REPO_ID );

    }

    @Test
    public void testGetEventsRangeInside()
        throws Exception

    {
        Date current = new Date();

        AuditEvent event1 = createEvent( new Date( current.getTime() - 12345 ) );
        Date expectedTimestamp = new Date( current.getTime() - 3000 );
        AuditEvent expectedEvent = createEvent( expectedTimestamp );
        AuditEvent event3 = createEvent( new Date( current.getTime() - 1000 ) );
        when( repositorySessionFactory.createSession() ).thenReturn( session );
            when(metadataRepository.getMetadataFacets(session, TEST_REPO_ID, AuditEvent.FACET_ID)).thenReturn(
                    Arrays.asList(event1.getName(), expectedEvent.getName(), event3.getName()));

            // only match the middle one
            when(metadataRepository.getMetadataFacet(session, TEST_REPO_ID,
                    AuditEvent.FACET_ID, expectedEvent.getName())).thenReturn(expectedEvent);

        List<AuditEvent> events =
            auditManager.getAuditEventsInRange( metadataRepository, Collections.singletonList( TEST_REPO_ID ),
                                                new Date( current.getTime() - 4000 ),
                                                new Date( current.getTime() - 2000 ) );

        assertEquals( 1, events.size() );
        assertTestEvent( events.get( 0 ), TIMESTAMP_FORMAT.format( expectedTimestamp ), expectedEvent.getResource() );

    }

    @Test
    public void testGetEventsRangeUpperOutside()
        throws Exception
    {
        Date current = new Date();

        AuditEvent event1 = createEvent( new Date( current.getTime() - 12345 ) );
        Date expectedTimestamp = new Date( current.getTime() - 3000 );
        AuditEvent expectedEvent2 = createEvent( expectedTimestamp );
        Date ts3 = new Date( current.getTime() - 1000 );
        AuditEvent expectedEvent3 = createEvent( ts3 );

        when( repositorySessionFactory.createSession() ).thenReturn( session );
            when(metadataRepository.getMetadataFacets(session, TEST_REPO_ID, AuditEvent.FACET_ID)).thenReturn(
                    Arrays.asList(event1.getName(), expectedEvent2.getName(), expectedEvent3.getName()));

            when(metadataRepository.getMetadataFacet(session, TEST_REPO_ID,
                    AuditEvent.FACET_ID, expectedEvent2.getName())).thenReturn(expectedEvent2);
            when(metadataRepository.getMetadataFacet(session, TEST_REPO_ID,
                    AuditEvent.FACET_ID, expectedEvent3.getName())).thenReturn(expectedEvent3);

        List<AuditEvent> events =
            auditManager.getAuditEventsInRange( metadataRepository, Collections.singletonList( TEST_REPO_ID ),
                                                new Date( current.getTime() - 4000 ), current );

        assertEquals( 2, events.size() );
        assertTestEvent( events.get( 0 ), TIMESTAMP_FORMAT.format( ts3 ), expectedEvent3.getResource() );
        assertTestEvent( events.get( 1 ), TIMESTAMP_FORMAT.format( expectedTimestamp ), expectedEvent2.getResource() );
    }

    @Test
    public void testGetEventsRangeLowerOutside()
        throws Exception
    {
        Date current = new Date();

        Date ts1 = new Date( current.getTime() - 12345 );
        AuditEvent expectedEvent1 = createEvent( ts1 );
        Date expectedTimestamp = new Date( current.getTime() - 3000 );
        AuditEvent expectedEvent2 = createEvent( expectedTimestamp );
        AuditEvent event3 = createEvent( new Date( current.getTime() - 1000 ) );

        when( repositorySessionFactory.createSession() ).thenReturn( session );

            when(metadataRepository.getMetadataFacets(session, TEST_REPO_ID, AuditEvent.FACET_ID)).thenReturn(
                    Arrays.asList(expectedEvent1.getName(), expectedEvent2.getName(), event3.getName()));

            when(metadataRepository.getMetadataFacet(session, TEST_REPO_ID,
                    AuditEvent.FACET_ID, expectedEvent1.getName())).thenReturn(expectedEvent1);

            when(metadataRepository.getMetadataFacet(session, TEST_REPO_ID,
                    AuditEvent.FACET_ID, expectedEvent2.getName())).thenReturn(expectedEvent2);

        List<AuditEvent> events =
            auditManager.getAuditEventsInRange( metadataRepository, Collections.singletonList( TEST_REPO_ID ),
                                                new Date( current.getTime() - 20000 ),
                                                new Date( current.getTime() - 2000 ) );

        assertEquals( 2, events.size() );
        assertTestEvent( events.get( 0 ), TIMESTAMP_FORMAT.format( expectedTimestamp ), expectedEvent2.getResource() );
        assertTestEvent( events.get( 1 ), TIMESTAMP_FORMAT.format( ts1 ), expectedEvent1.getResource() );
    }

    @Test
    public void testGetEventsRangeLowerAndUpperOutside()
        throws Exception
    {
        Date current = new Date();

        Date ts1 = new Date( current.getTime() - 12345 );
        AuditEvent expectedEvent1 = createEvent( ts1 );
        Date expectedTimestamp = new Date( current.getTime() - 3000 );
        AuditEvent expectedEvent2 = createEvent( expectedTimestamp );
        Date ts3 = new Date( current.getTime() - 1000 );
        AuditEvent expectedEvent3 = createEvent( ts3 );

        when( repositorySessionFactory.createSession() ).thenReturn( session );
            when(metadataRepository.getMetadataFacets(session, TEST_REPO_ID, AuditEvent.FACET_ID)).thenReturn(
                    Arrays.asList(expectedEvent1.getName(), expectedEvent2.getName(), expectedEvent3.getName()));

            when(metadataRepository.getMetadataFacet(session, TEST_REPO_ID,
                    AuditEvent.FACET_ID, expectedEvent1.getName())).thenReturn(expectedEvent1);

            when(metadataRepository.getMetadataFacet(session, TEST_REPO_ID,
                    AuditEvent.FACET_ID, expectedEvent2.getName())).thenReturn(expectedEvent2);

            when(metadataRepository.getMetadataFacet(session, TEST_REPO_ID,
                    AuditEvent.FACET_ID, expectedEvent3.getName())).thenReturn(expectedEvent3);

        List<AuditEvent> events =
            auditManager.getAuditEventsInRange( metadataRepository, Collections.singletonList( TEST_REPO_ID ),
                                                new Date( current.getTime() - 20000 ), current );

        assertEquals( 3, events.size() );
        assertTestEvent( events.get( 0 ), TIMESTAMP_FORMAT.format( ts3 ), expectedEvent3.getResource() );
        assertTestEvent( events.get( 1 ), TIMESTAMP_FORMAT.format( expectedTimestamp ), expectedEvent2.getResource() );
        assertTestEvent( events.get( 2 ), TIMESTAMP_FORMAT.format( ts1 ), expectedEvent1.getResource() );

    }

    @Test
    public void testGetEventsWithResource()
        throws Exception
    {
        Date current = new Date();

        Date ts1 = new Date( current.getTime() - 12345 );
        AuditEvent expectedEvent1 = createEvent( ts1 );
        Date expectedTimestamp = new Date( current.getTime() - 3000 );
        AuditEvent expectedEvent2 = createEvent( expectedTimestamp );
        expectedEvent2.setResource( "different-resource" );
        Date ts3 = new Date( current.getTime() - 1000 );
        AuditEvent expectedEvent3 = createEvent( ts3 );

        when( repositorySessionFactory.createSession() ).thenReturn( session );
            when(metadataRepository.getMetadataFacets(session, TEST_REPO_ID, AuditEvent.FACET_ID)).thenReturn(
                    Arrays.asList(expectedEvent1.getName(), expectedEvent2.getName(), expectedEvent3.getName()));


            when(metadataRepository.getMetadataFacet(session, TEST_REPO_ID,
                    AuditEvent.FACET_ID, expectedEvent1.getName())).thenReturn(expectedEvent1);

            when(metadataRepository.getMetadataFacet(session, TEST_REPO_ID,
                    AuditEvent.FACET_ID, expectedEvent2.getName())).thenReturn(expectedEvent2);

            when(metadataRepository.getMetadataFacet(session, TEST_REPO_ID,
                    AuditEvent.FACET_ID, expectedEvent3.getName())).thenReturn(expectedEvent3);

        List<AuditEvent> events =
            auditManager.getAuditEventsInRange( metadataRepository, Collections.singletonList( TEST_REPO_ID ),
                                                TEST_RESOURCE_BASE, new Date( current.getTime() - 20000 ), current );

        assertEquals( 2, events.size() );
        assertTestEvent( events.get( 0 ), TIMESTAMP_FORMAT.format( ts3 ), expectedEvent3.getResource() );
        assertTestEvent( events.get( 1 ), TIMESTAMP_FORMAT.format( ts1 ), expectedEvent1.getResource() );

    }

    @Test
    public void testGetEventsWithNonExistantResource()
        throws Exception
    {
        Date current = new Date();

        AuditEvent expectedEvent1 = createEvent( new Date( current.getTime() - 12345 ) );
        Date expectedTimestamp = new Date( current.getTime() - 3000 );
        AuditEvent expectedEvent2 = createEvent( expectedTimestamp );
        expectedEvent2.setResource( "different-resource" );
        AuditEvent expectedEvent3 = createEvent( new Date( current.getTime() - 1000 ) );

        when( repositorySessionFactory.createSession() ).thenReturn( session );
            when(metadataRepository.getMetadataFacets(session, TEST_REPO_ID, AuditEvent.FACET_ID)).thenReturn(
                    Arrays.asList(expectedEvent1.getName(), expectedEvent2.getName(), expectedEvent3.getName()));


            when(metadataRepository.getMetadataFacet(session, TEST_REPO_ID, AuditEvent.FACET_ID, expectedEvent1.getName())).thenReturn(expectedEvent1);

            when(metadataRepository.getMetadataFacet(session, TEST_REPO_ID, AuditEvent.FACET_ID, expectedEvent2.getName())).thenReturn(expectedEvent2);

            when(metadataRepository.getMetadataFacet(session, TEST_REPO_ID, AuditEvent.FACET_ID, expectedEvent3.getName())).thenReturn(expectedEvent3);


        List<AuditEvent> events =
            auditManager.getAuditEventsInRange( metadataRepository, Collections.singletonList( TEST_REPO_ID ), "foo",
                                                new Date( current.getTime() - 20000 ), current );

        assertEquals( 0, events.size() );

    }

    @Test
    public void testGetEventsRangeMultipleRepositories()
        throws Exception
    {
        Date current = new Date();

        Date ts1 = new Date( current.getTime() - 12345 );
        AuditEvent expectedEvent1 = createEvent( ts1 );
        Date expectedTimestamp = new Date( current.getTime() - 3000 );
        AuditEvent expectedEvent2 = createEvent( expectedTimestamp );
        expectedEvent2.setRepositoryId( TEST_REPO_ID_2 );
        Date ts3 = new Date( current.getTime() - 1000 );
        AuditEvent expectedEvent3 = createEvent( ts3 );

        when( repositorySessionFactory.createSession() ).thenReturn( session );
            when(metadataRepository.getMetadataFacets(session, TEST_REPO_ID, AuditEvent.FACET_ID))
                    .thenReturn(Arrays.asList(expectedEvent1.getName(), expectedEvent3.getName()));

            when(metadataRepository.getMetadataFacets(session, TEST_REPO_ID_2, AuditEvent.FACET_ID))
                    .thenReturn( Collections.singletonList( expectedEvent2.getName( ) ) );


            when(metadataRepository.getMetadataFacet(session, TEST_REPO_ID, AuditEvent.FACET_ID, expectedEvent1.getName()))
                    .thenReturn(expectedEvent1);

            when(metadataRepository.getMetadataFacet(session, TEST_REPO_ID_2, AuditEvent.FACET_ID, expectedEvent2.getName()))
                    .thenReturn(expectedEvent2);

            when(metadataRepository.getMetadataFacet(session, TEST_REPO_ID, AuditEvent.FACET_ID, expectedEvent3.getName()))
                    .thenReturn(expectedEvent3);

        List<AuditEvent> events =
            auditManager.getAuditEventsInRange( metadataRepository, Arrays.asList( TEST_REPO_ID, TEST_REPO_ID_2 ),
                                                new Date( current.getTime() - 20000 ), current );

        assertEquals( 3, events.size() );
        assertTestEvent( events.get( 0 ), TEST_REPO_ID, TIMESTAMP_FORMAT.format( ts3 ), expectedEvent3.getResource() );
        assertTestEvent( events.get( 1 ), TEST_REPO_ID_2, TIMESTAMP_FORMAT.format( expectedTimestamp ),
                         expectedEvent2.getResource() );
        assertTestEvent( events.get( 2 ), TEST_REPO_ID, TIMESTAMP_FORMAT.format( ts1 ), expectedEvent1.getResource() );

    }

    @Test
    public void testGetEventsRangeNotInside()
        throws Exception
    {
        Date current = new Date();

        String name1 = createEvent( new Date( current.getTime() - 12345 ) ).getName();
        Date expectedTimestamp = new Date( current.getTime() - 3000 );
        String name2 = createEvent( expectedTimestamp ).getName();
        String name3 = createEvent( new Date( current.getTime() - 1000 ) ).getName();

        when( repositorySessionFactory.createSession() ).thenReturn( session );
            when(metadataRepository.getMetadataFacets(session, TEST_REPO_ID, AuditEvent.FACET_ID))
                    .thenReturn(Arrays.asList(name1, name2, name3));

        List<AuditEvent> events =
            auditManager.getAuditEventsInRange( metadataRepository, Collections.singletonList( TEST_REPO_ID ),
                                                new Date( current.getTime() - 20000 ),
                                                new Date( current.getTime() - 16000 ) );

        assertEquals( 0, events.size() );

    }

    private static String getDefaultTestResourceName( String num )
    {
        return TEST_RESOURCE_BASE + "/" + num + ".xml";
    }

    private static AuditEvent createEvent( Date timestamp )
        throws ParseException
    {
        return createEvent( TIMESTAMP_FORMAT.format( timestamp ) );
    }

    private static AuditEvent createEvent( String ts )
        throws ParseException
    {
        return createEvent( TEST_REPO_ID, ts, getDefaultTestResourceName(
            ts.substring( AUDIT_EVENT_BASE.length(), AUDIT_EVENT_BASE.length() + 3 ) ) );
    }

    private static AuditEvent createEvent( String repositoryId, String timestamp, String resource )
        throws ParseException
    {
        AuditEvent event = new AuditEvent();
        event.setTimestamp( TIMESTAMP_FORMAT.parse( timestamp ) );
        event.setAction( AuditEvent.UPLOAD_FILE );
        event.setRemoteIP( TEST_IP_ADDRESS );
        event.setRepositoryId( repositoryId );
        event.setUserId( TEST_USER );
        event.setResource( resource );
        return event;
    }

    private static void assertTestEvent( AuditEvent event, String timestamp, String resource )
    {
        assertTestEvent( event, TEST_REPO_ID, timestamp, resource );
    }

    private static void assertTestEvent( AuditEvent event, String repositoryId, String timestamp, String resource )
    {
        assertEquals( timestamp, TIMESTAMP_FORMAT.format( event.getTimestamp() ) );
        assertEquals( AuditEvent.UPLOAD_FILE, event.getAction() );
        assertEquals( TEST_IP_ADDRESS, event.getRemoteIP() );
        assertEquals( repositoryId, event.getRepositoryId() );
        assertEquals( TEST_USER, event.getUserId() );
        assertEquals( resource, event.getResource() );
    }

    private List<String> getEventNames( List<AuditEvent> events )
    {
        List<String> names = new ArrayList<>( events.size() );
        for ( AuditEvent event : events )
        {
            names.add( event.getName() );
        }
        return names;
    }
}
