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

import static uk.ac.ebi.ena.webin.cli.manifest.processor.ProcessorTestUtils.createFieldValue;

import org.junit.Assert;
import org.junit.Test;

import uk.ac.ebi.ena.webin.cli.message.ValidationMessage.Severity;
import uk.ac.ebi.ena.webin.cli.WebinCliParameters;
import uk.ac.ebi.ena.webin.cli.message.ValidationResult;
import uk.ac.ebi.ena.webin.cli.WebinCliTestUtils;
import uk.ac.ebi.ena.webin.cli.manifest.ManifestFieldType;
import uk.ac.ebi.ena.webin.cli.manifest.ManifestFieldValue;

public class
AnalysisProcessorTest
{
    private final WebinCliParameters parameters = WebinCliTestUtils.getTestWebinCliParameters();

    @Test public void
    testCorrect()
    {
        final String analysis_id = "ERZ690501";
        AnalysisProcessor processor = new AnalysisProcessor( parameters,
                                                            ( e ) -> {
                                                                Assert.assertEquals( 1, e.size() );
                                                                Assert.assertEquals( analysis_id, e.get( 0 ).getAnalysisId() ); 
                                                            } );

        ManifestFieldValue fieldValue = createFieldValue( ManifestFieldType.META, "ANALYSIS_REF", analysis_id );
        Assert.assertTrue( processor.process( fieldValue ).isValid() );
        Assert.assertEquals( analysis_id, fieldValue.getValue() );
    }
    

    @Test public void
    testCorrectList()
    {
        AnalysisProcessor processor = new AnalysisProcessor( parameters, 
                                                             ( e ) -> {
                                                                 Assert.assertEquals( 3, e.size() );
                                                                 Assert.assertEquals( "ERZ690501", e.get( 0 ).getAnalysisId() );
                                                                 Assert.assertEquals( "ERZ690500", e.get( 1 ).getAnalysisId() );
                                                                 Assert.assertEquals( "ERZ690502", e.get( 2 ).getAnalysisId() );
                                                             } );

        ManifestFieldValue fieldValue = createFieldValue( ManifestFieldType.META, "ANALYSIS_REF", "ERZ690501, ERZ690500, ERZ690500, ERZ690502" );
        Assert.assertTrue( processor.process( fieldValue ).isValid() );
        Assert.assertEquals( "ERZ690501, ERZ690500, ERZ690502", fieldValue.getValue() );
    }

    
    
    @Test public void
    testIncorrect()
    {
        AnalysisProcessor processor = new AnalysisProcessor( parameters, Assert::assertNull );

        final String analysis_id = "SOME_ANALYSIS_ID";
        ManifestFieldValue fieldValue = createFieldValue( ManifestFieldType.META, "ANALYSIS_REF", analysis_id );
        ValidationResult result = processor.process( fieldValue );
        Assert.assertFalse( result.isValid() );
        Assert.assertEquals( 1, result.count( Severity.ERROR ) );
        Assert.assertTrue( result.getMessages( Severity.ERROR ).iterator().next().getMessage().contains( analysis_id ) );
        Assert.assertEquals( analysis_id, fieldValue.getValue() );
    }
    
    
    @Test public void
    testIncorrectList()
    {
        AnalysisProcessor processor = new AnalysisProcessor( parameters, Assert::assertNull );

        ManifestFieldValue fieldValue = createFieldValue( ManifestFieldType.META, "ANALYSIS_REF", "SOME_ANALYSIS_ID1, ERZ690500, SOME_ANALYSIS_ID2" );
        ValidationResult result = processor.process( fieldValue );
        result.getMessages( Severity.ERROR ).stream().forEach( System.out::println );
        Assert.assertFalse( result.isValid() );
        Assert.assertEquals( 2, result.count( Severity.ERROR ) );
        Assert.assertTrue(  result.getMessages( Severity.ERROR ).stream().anyMatch( e -> e.getMessage().contains( "SOME_ANALYSIS_ID1" ) ) );
        Assert.assertFalse( result.getMessages( Severity.ERROR ).stream().anyMatch( e -> e.getMessage().contains( "ERZ690500" ) ) );
        Assert.assertTrue(  result.getMessages( Severity.ERROR ).stream().anyMatch( e -> e.getMessage().contains( "SOME_ANALYSIS_ID2" ) ) );
        Assert.assertEquals( "SOME_ANALYSIS_ID1, ERZ690500, SOME_ANALYSIS_ID2", fieldValue.getValue() );
    }

}
