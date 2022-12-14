package org.apache.archiva.maven.repository.mock;
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

import org.apache.archiva.components.taskqueue.TaskQueueException;
import org.apache.archiva.scheduler.repository.model.RepositoryArchivaTaskScheduler;
import org.apache.archiva.scheduler.repository.model.RepositoryTask;
import org.springframework.stereotype.Service;

/**
 * @author Olivier Lamy
 */
@Service ("archivaTaskScheduler#repositoryMock")
public class MockRepositoryArchivaTaskScheduler
    implements RepositoryArchivaTaskScheduler
{
    @Override
    public boolean isProcessingRepositoryTask( String repositoryId )
    {
        return false;
    }

    @Override
    public boolean isProcessingRepositoryTask( RepositoryTask task )
    {
        return false;
    }

    @Override
    public void queueTask( RepositoryTask task )
        throws TaskQueueException
    {
        // no op
    }

    @Override
    public boolean unQueueTask( RepositoryTask task )
        throws TaskQueueException
    {
        return false;
    }
}
