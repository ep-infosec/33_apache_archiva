package org.apache.archiva.rest.v2.svc;
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

import org.apache.archiva.admin.model.AuditInformation;
import org.apache.archiva.redback.rest.services.RedbackAuthenticationThreadLocal;
import org.apache.archiva.redback.rest.services.RedbackRequestInformation;
import org.apache.archiva.redback.users.User;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;

/**
 * @author Martin Schreier <martin_s@apache.org>
 */
public class AbstractService
{
    @Context
    private HttpServletRequest httpServletRequest;

    protected AuditInformation getAuditInformation( )
    {
        RedbackRequestInformation redbackRequestInformation = RedbackAuthenticationThreadLocal.get( );
        User user;
        String remoteAddr;
        if (redbackRequestInformation==null) {
            user = null;
            remoteAddr = httpServletRequest.getRemoteAddr( );
        } else
        {
            user = redbackRequestInformation.getUser( );
            remoteAddr = redbackRequestInformation.getRemoteAddr( );
        }
        return new AuditInformation( user, remoteAddr );
    }
}
