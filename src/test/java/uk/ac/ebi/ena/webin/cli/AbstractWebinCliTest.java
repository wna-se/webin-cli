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
package uk.ac.ebi.ena.webin.cli;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import org.junit.Test;

import uk.ac.ebi.ena.webin.cli.assembly.TranscriptomeAssemblyWebinCli;
import uk.ac.ebi.ena.webin.cli.manifest.ManifestFieldDefinition;
import uk.ac.ebi.ena.webin.cli.manifest.ManifestReader;
import uk.ac.ebi.ena.webin.cli.rawreads.RawReadsWebinCli;

public class 
AbstractWebinCliTest 
{
    static class
    TestManifest extends ManifestReader
    {
        public TestManifest( List<ManifestFieldDefinition> fields ) { super( fields ); }
        @Override public String getName() { return null; }
        @Override public String getDescription() { return null; }
        @Override protected void processManifest() { }
    }
    
    
    abstract static class 
    TestCli extends AbstractWebinCli<TestManifest>
    {
        protected TestCli( boolean test_mode ) { super( test_mode ); }
        @Override protected void validate() throws WebinCliException { }
        @Override protected void prepareSubmissionBundle() { }
        @Override protected TestManifest createManifestReader() { return null; }
        @Override protected void readManifest( Path inputDir, File manifestFile ) { }
    }
    
    
    @Test public void
    testGetAlias() 
    {
        TestCli genomeAssemblyWebinCli = new TestCli( true ) {
            @Override protected WebinCliContext getContext() { return WebinCliContext.genome; }
        };
        genomeAssemblyWebinCli.setName( "TEST_NAME" );
        assertEquals( "webin-genome-TEST_NAME", genomeAssemblyWebinCli.getAlias() );

        TestCli transcriptomeAssemblyWebinCli = new TestCli( true ) {
            @Override protected WebinCliContext getContext() { return WebinCliContext.transcriptome; }
        };
        transcriptomeAssemblyWebinCli.setName( "TEST_NAME" );
        assertEquals( "webin-transcriptome-TEST_NAME", transcriptomeAssemblyWebinCli.getAlias() );

        TestCli sequenceAssemblyWebinCli = new TestCli( true ) {
            @Override protected WebinCliContext getContext() { return WebinCliContext.sequence; }
        };
        sequenceAssemblyWebinCli.setName( "TEST_NAME" );
        assertEquals( "webin-sequence-TEST_NAME", sequenceAssemblyWebinCli.getAlias() );

        TestCli rawReadsWebinCli = new TestCli( true ) {
            @Override protected WebinCliContext getContext() { return WebinCliContext.reads; }
        };
        rawReadsWebinCli.setName( "TEST_NAME" );
        assertEquals( "webin-reads-TEST_NAME", rawReadsWebinCli.getAlias() );
    }
}
