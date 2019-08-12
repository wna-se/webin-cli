/*
 * Copyright 2018-2019 EMBL - European Bioinformatics Institute
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package uk.ac.ebi.ena.webin.cli.manifest.processor.metadata;

import uk.ac.ebi.embl.api.validation.ValidationResult;
import uk.ac.ebi.ena.webin.cli.WebinCliException;
import uk.ac.ebi.ena.webin.cli.WebinCliMessage;
import uk.ac.ebi.ena.webin.cli.WebinCliParameters;
import uk.ac.ebi.ena.webin.cli.manifest.ManifestFieldProcessor;
import uk.ac.ebi.ena.webin.cli.manifest.ManifestFieldValue;
import uk.ac.ebi.ena.webin.cli.service.SampleXmlService;
import uk.ac.ebi.ena.webin.cli.validator.reference.Sample;

public class
SampleXmlProcessor implements ManifestFieldProcessor
{
    private final WebinCliParameters parameters;
    private ManifestFieldProcessor.Callback<Sample> callback;

    public SampleXmlProcessor(WebinCliParameters parameters, ManifestFieldProcessor.Callback<Sample> callback )
    {
        this.parameters = parameters;
        this.callback = callback;
    }

    public SampleXmlProcessor(WebinCliParameters parameters)
    {
        this.parameters = parameters;
    }

    public void setCallback(Callback<Sample> callback) {
        this.callback = callback;
    }

    @Override public ValidationResult
    process( ManifestFieldValue fieldValue )
    {
        String value = fieldValue.getValue();

        try
        {
            SampleXmlService sampleXmlService = new SampleXmlService.Builder()
                                                                                .setCredentials( parameters.getUsername(), parameters.getPassword() )
                                                                                .setTest( parameters.isTestMode() )
                                                                                .build();
            Sample source = sampleXmlService.getSample( value );
            callback.notify( source );
            return new ValidationResult();
            
        } catch( WebinCliException e )
        {
            return new ValidationResult().append( WebinCliMessage.error(WebinCliMessage.Manifest.SAMPLE_LOOKUP_ERROR, value, e.getMessage() ) );
        }
    }
}