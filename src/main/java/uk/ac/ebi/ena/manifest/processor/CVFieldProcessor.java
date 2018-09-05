/*
 * Copyright 2018 EMBL - European Bioinformatics Institute
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package uk.ac.ebi.ena.manifest.processor;

import uk.ac.ebi.embl.api.validation.Origin;
import uk.ac.ebi.embl.api.validation.ValidationMessage;
import uk.ac.ebi.ena.manifest.ManifestFieldProcessor;
import uk.ac.ebi.ena.manifest.ManifestFieldValue;
import uk.ac.ebi.ena.manifest.ManifestCVList;

public class
CVFieldProcessor implements ManifestFieldProcessor
{
    public final static CVFieldProcessor CV_BOOLEAN = new CVFieldProcessor(new ManifestCVList(
        "yes",
        "no",
        "true",
        "false",
        "Y",
        "N"));

    private ManifestCVList cvList;

    public CVFieldProcessor(String ... values )
    {
        this.cvList = new ManifestCVList(values);
    }

    public CVFieldProcessor(ManifestCVList cvList )
    {
        this.cvList = cvList;
    }

    @Override public ValidationMessage<Origin>
    process(ManifestFieldValue fieldValue )
    {
        String value = fieldValue.getValue();

        if( !cvList.contains( value ) )
            return ValidationMessage.error( "MANIFEST_INVALID_FIELD_VALUE", fieldValue.getName(), value, cvList.keyList() );

        String corrected = cvList.getKey( value );

        if( !value.equals( corrected ) )
        {
            fieldValue.setValue( corrected );
            return ValidationMessage.info( "MANIFEST_FIELD_VALUE_WAS_CORRECTED", fieldValue.getName(), value, corrected );
        }

        return null;
    }
}
