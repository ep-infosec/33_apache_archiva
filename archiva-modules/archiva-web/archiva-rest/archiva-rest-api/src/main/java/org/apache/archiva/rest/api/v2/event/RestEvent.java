package org.apache.archiva.rest.api.v2.event;
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


import org.apache.archiva.event.Event;
import org.apache.archiva.event.EventContextBuilder;
import org.apache.archiva.event.EventType;
import org.apache.archiva.event.context.RestContext;

/**
 * @author Martin Schreier <martin_s@apache.org>
 */
public class RestEvent extends Event<RestContext>
{

    public static EventType<RestEvent> ANY = new EventType<>( Event.ANY, "REST");


    public RestEvent( EventType<? extends Event> type, Object originator)
    {
        super( type, originator );
        EventContextBuilder builder = EventContextBuilder.withEvent( this );
    }

    @Override
    public RestContext getContext() {
        return getContext( RestContext.class );
    }
}
