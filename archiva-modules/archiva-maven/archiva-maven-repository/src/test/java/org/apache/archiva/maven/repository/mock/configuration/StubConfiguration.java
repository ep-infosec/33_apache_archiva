package org.apache.archiva.maven.repository.mock.configuration;

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

import org.apache.archiva.components.registry.Registry;
import org.apache.archiva.components.registry.RegistryException;
import org.apache.archiva.components.registry.RegistryListener;
import org.apache.archiva.configuration.provider.ArchivaConfiguration;
import org.apache.archiva.configuration.model.Configuration;
import org.apache.archiva.configuration.provider.ConfigurationListener;
import org.apache.archiva.configuration.provider.IndeterminateConfigurationException;
import org.apache.archiva.configuration.model.RepositoryScanningConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

@Service("archivaConfiguration#mocked")
public class StubConfiguration
    implements ArchivaConfiguration
{
    private Configuration configuration = new Configuration();

    StubConfiguration() {
        configuration.setRepositoryScanning( new RepositoryScanningConfiguration() );
    }

    @Override
    public Configuration getConfiguration()
    {
        return configuration;
    }

    @Override
    public void save( Configuration configuration )
        throws RegistryException, IndeterminateConfigurationException
    {
        this.configuration = configuration;
    }

    @Override
    public void save( Configuration configuration, String eventTag ) throws RegistryException, IndeterminateConfigurationException
    {
        this.configuration = configuration;
    }

    @Override
    public boolean isDefaulted()
    {
        return false;
    }

    @Override
    public void addListener( ConfigurationListener listener )
    {
        // throw new UnsupportedOperationException();
    }

    @Override
    public void removeListener( ConfigurationListener listener )
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addChangeListener( RegistryListener listener )
    {
        // throw new UnsupportedOperationException();
    }

    @Override
    public void removeChangeListener( RegistryListener listener )
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void reload()
    {
        // no op
    }

    @Override
    public Locale getDefaultLocale( )
    {
        return Locale.getDefault();
    }

    @Override
    public List<Locale.LanguageRange> getLanguagePriorities( )
    {
        return Locale.LanguageRange.parse( "en,fr,de" );
    }

    @Override
    public Path getAppServerBaseDir() {
        if (System.getProperties().containsKey("appserver.base")) {
            return Paths.get(System.getProperty("appserver.base"));
        } else {
            return Paths.get("");
        }
    }

    @Override
    public Path getRepositoryBaseDir() {
        return getDataDirectory().resolve("repositories");
    }

    @Override
    public Path getRemoteRepositoryBaseDir() {
        return getDataDirectory().resolve("remotes");
    }

    @Override
    public Path getRepositoryGroupBaseDir( )
    {
        return getDataDirectory().resolve("group");
    }

    @Override
    public Path getDataDirectory() {
        if (configuration!=null && configuration.getArchivaRuntimeConfiguration()!=null && StringUtils.isNotEmpty(configuration.getArchivaRuntimeConfiguration().getDataDirectory())) {
            Path dataDir = Paths.get(configuration.getArchivaRuntimeConfiguration().getDataDirectory());
            if (dataDir.isAbsolute()) {
                return dataDir;
            } else {
                return getAppServerBaseDir().resolve(dataDir);
            }
        } else {
            return getAppServerBaseDir().resolve("data");
        }

    }

    @Override
    public Registry getRegistry( )
    {
        return null;
    }
}
