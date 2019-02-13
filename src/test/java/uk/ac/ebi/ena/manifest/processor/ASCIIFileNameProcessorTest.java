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

import org.junit.Assert;
import org.junit.Test;
import uk.ac.ebi.embl.api.validation.Severity;
import uk.ac.ebi.ena.manifest.*;

import static uk.ac.ebi.ena.manifest.processor.ProcessorTestUtils.createFieldValue;

public class 
ASCIIFileNameProcessorTest
{
    @Test public void 
    test() 
    {
        ASCIIFileNameProcessor processor = new ASCIIFileNameProcessor();

        ManifestFieldValue fieldValue = createFieldValue( ManifestFieldType.META, "FIELD1", "a.bam" );
        Assert.assertNull( processor.process( fieldValue ) );
        Assert.assertEquals( "a.bam", fieldValue.getValue() );

        fieldValue = createFieldValue( ManifestFieldType.META, "FIELD1", "/a/b/c.bam" );
        Assert.assertNull( processor.process( fieldValue ) );
        
        fieldValue = createFieldValue( ManifestFieldType.META, "FIELD1", "a:\\B\\c.bam" );
        Assert.assertNull( processor.process( fieldValue ) );
        
        fieldValue = createFieldValue( ManifestFieldType.META, "FIELD1", "a|b.cram" );
        Assert.assertEquals(processor.process(fieldValue).getSeverity(), Severity.ERROR);

        fieldValue = createFieldValue( ManifestFieldType.META, "FIELD1", "a&b.cram" );
        Assert.assertEquals(processor.process(fieldValue).getSeverity(), Severity.ERROR);
        Assert.assertEquals( "a&b.cram", fieldValue.getValue() );
    }
}
