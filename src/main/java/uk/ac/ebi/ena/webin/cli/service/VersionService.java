/*
 * Copyright 2018-2021 EMBL - European Bioinformatics Institute
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package uk.ac.ebi.ena.webin.cli.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import uk.ac.ebi.ena.webin.cli.WebinCliMessage;
import uk.ac.ebi.ena.webin.cli.entity.Version;
import uk.ac.ebi.ena.webin.cli.utils.ExceptionUtils;
import uk.ac.ebi.ena.webin.cli.utils.RetryUtils;

public class 
VersionService extends WebinService
{
    private static final Logger log = LoggerFactory.getLogger(VersionService.class);

    public static class 
    Builder extends AbstractBuilder<VersionService> {
        public VersionService
        build()
        {
          return new VersionService( this );
        }
    }

    protected 
    VersionService( Builder builder )
    {
        super( builder );
    }

    public Version getVersion(String version )
    {
        return getVersion( version, getTest() );
    }

    private Version getVersion(String version, boolean test ) {
        RestTemplate restTemplate = new RestTemplate();

        return ExceptionUtils.executeWithRestExceptionHandling(

            () -> RetryUtils.executeWithRetry(
                retryContext -> restTemplate.getForObject(
                    getWebinRestUri("/cli/{version}", test), Version.class, version),
                retryContext -> log.warn("Retrying version retrieval from server."),
                HttpServerErrorException.class, ResourceAccessException.class),

            WebinCliMessage.SERVICE_AUTHENTICATION_ERROR.format("Version"),
            null,
            WebinCliMessage.VERSION_SERVICE_SYSTEM_ERROR.text());
    }
}
