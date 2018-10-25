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

package uk.ac.ebi.ena.assembly;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

import uk.ac.ebi.embl.api.entry.genomeassembly.AssemblyInfoEntry;
import uk.ac.ebi.embl.api.validation.submission.Context;
import uk.ac.ebi.embl.api.validation.submission.SubmissionFile;
import uk.ac.ebi.embl.api.validation.submission.SubmissionFiles;
import uk.ac.ebi.embl.api.validation.submission.SubmissionOptions;
import uk.ac.ebi.embl.api.validation.submission.SubmissionFile.FileType;
import uk.ac.ebi.ena.WebinCliTestUtils;
import uk.ac.ebi.ena.assembly.SequenceAssemblyWebinCli;
import uk.ac.ebi.ena.study.Study;

public class SequenceAssemblyValidationTest {
    private final static String SEQUENCE_BASE_DIR = "src/test/resources/uk/ac/ebi/ena/template/tsvfile";
    private final static String[] allTemplatesA = {"ERT000002-rRNA.tsv.gz",
                                                    "ERT000003-EST-1.tsv.gz",
                                                    "ERT000006-SCM.tsv.gz",
                                                    "ERT000009-ITS.tsv.gz",
                                                    "ERT000020-COI.tsv.gz",
                                                    "ERT000024-GSS-1.tsv.gz",
                                                    "ERT000028-SVC.tsv.gz",
                                                    "ERT000029-SCGD.tsv.gz",
                                                    "ERT000030-MHC1.tsv.gz",
                                                    "ERT000031-viroid.tsv.gz",
                                                    "ERT000032-matK.tsv.gz",
                                                    "ERT000034-Dloop.tsv.gz",
                                                    "ERT000035-IGS.tsv.gz",
                                                    "ERT000036-MHC2.tsv.gz",
                                                    "ERT000037-intron.tsv.gz",
                                                    "ERT000038-hyloMarker.tsv.gz",
                                                    "ERT000039-Sat.tsv.gz",
                                                    "ERT000042-ncRNA.tsv.gz",
                                                    "ERT000047-betasat.tsv.gz",
                                                    "ERT000050-ISR.tsv.gz",
                                                    "ERT000051-poly.tsv.gz",
                                                    "ERT000052-ssRNA.tsv.gz",
                                                    "ERT000053-ETS.tsv.gz",
                                                    "ERT000054-prom.tsv.gz",
                                                    "ERT000055-STS.tsv.gz",
                                                    "ERT000056-mobele.tsv.gz",
                                                    "ERT000057-alphasat.tsv.gz",
                                                    "ERT000058-MLmarker.tsv.gz",
                                                    "ERT000060-vUTR.tsv.gz"};

    //TODO Default Locale handling is incorrect
    @Before public void 
    before()
    {
        Locale.setDefault( Locale.UK );
    }
    
    
    @Test
    public void mandatoryFieldsPresent()  {
        try {
            String testTsvFile = SEQUENCE_BASE_DIR + File.separator + "Sequence-mandatory-field-missing.tsv.gz";
            SequenceAssemblyWebinCli sequenceAssemblyWebinCli = new SequenceAssemblyWebinCli();
          //  sequenceAssemblyWebinCli.setValidationDir(new File(SEQUENCE_BASE_DIR));
            StringBuilder resultsSb = sequenceAssemblyWebinCli.validateTestTsv(testTsvFile);
            String expectedResults = new String(Files.readAllBytes(Paths.get(SEQUENCE_BASE_DIR  + File.separator + "Sequence-mandatory-field-missing-expected-results.txt")));
            assertEquals(resultsSb.toString().replaceAll("\\s+", ""), expectedResults.replaceAll("\\s+", ""));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testAllTemplate() {
        try {
            String testTsvFile;;
            StringBuilder resultsSb = new StringBuilder();
            for (String file: allTemplatesA) {
                testTsvFile = SEQUENCE_BASE_DIR + File.separator + file;
                SequenceAssemblyWebinCli sequenceAssemblyWebinCli = new SequenceAssemblyWebinCli();
                sequenceAssemblyWebinCli.setValidationDir(new File(SEQUENCE_BASE_DIR));
                resultsSb.append(sequenceAssemblyWebinCli.validateTestTsv(testTsvFile));
            }
            assertEquals("", resultsSb.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void invalidAlphanumericEntrynumber()  {
        try {
            String testTsvFile = SEQUENCE_BASE_DIR + File.separator + "Sequence-invalid-alphanumeric-entrynumber-.tsv.gz";
            SequenceAssemblyWebinCli sequenceAssemblyWebinCli = new SequenceAssemblyWebinCli();
            sequenceAssemblyWebinCli.setValidationDir(new File(SEQUENCE_BASE_DIR));
            StringBuilder resultsSb = sequenceAssemblyWebinCli.validateTestTsv(testTsvFile);
            String expectedResults = new String(Files.readAllBytes(Paths.get(SEQUENCE_BASE_DIR  + File.separator + "Sequence-invalidAlphanumericEntrynumber-expected-results.txt")));
            assertEquals(resultsSb.toString().trim(), expectedResults.trim());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void requiredHeadersMissing() {
        try {
            String testTsvFile = SEQUENCE_BASE_DIR + File.separator + "Sequence-ERT000039-missingheaders.tsv.gz";
            SequenceAssemblyWebinCli sequenceAssemblyWebinCli = new SequenceAssemblyWebinCli();
            sequenceAssemblyWebinCli.setValidationDir(new File(SEQUENCE_BASE_DIR));
            StringBuilder resultsSb = sequenceAssemblyWebinCli.validateTestTsv(testTsvFile);
            String expectedResults = new String(Files.readAllBytes(Paths.get(SEQUENCE_BASE_DIR  + File.separator + "Sequence-ERT000039-missingheaders-expected-results.txt")));
            assertEquals(resultsSb.toString().replaceAll("\\s+", ""), expectedResults.replaceAll("\\s+", ""));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void invalidMarker() {
        try {
            String testTsvFile = SEQUENCE_BASE_DIR + File.separator + "Sequence-invalid-marker.tsv.gz";
            SequenceAssemblyWebinCli sequenceAssemblyWebinCli = new SequenceAssemblyWebinCli();
            sequenceAssemblyWebinCli.setValidationDir(new File(SEQUENCE_BASE_DIR));
            StringBuilder resultsSb = sequenceAssemblyWebinCli.validateTestTsv(testTsvFile);
            String expectedResults = new String(Files.readAllBytes(Paths.get(SEQUENCE_BASE_DIR  + File.separator + "Sequence-invalidMarker-expected-results.txt")));
            assertEquals(resultsSb.toString().trim(), expectedResults.trim());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void invalidSediment() {
        try {
            String testTsvFile = SEQUENCE_BASE_DIR + File.separator + "Sequence-invalid-sediment.tsv.gz";
            SequenceAssemblyWebinCli sequenceAssemblyWebinCli = new SequenceAssemblyWebinCli();
            sequenceAssemblyWebinCli.setValidationDir(new File(SEQUENCE_BASE_DIR));
            StringBuilder resultsSb = sequenceAssemblyWebinCli.validateTestTsv(testTsvFile);
            String expectedResults = new String(Files.readAllBytes(Paths.get(SEQUENCE_BASE_DIR  + File.separator + "Sequence-invalidSediment-expected-results.txt")));
            assertEquals(resultsSb.toString().trim(), expectedResults.trim());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void invalidEntryNumberStart() {
        try {
            String testTsvFile = SEQUENCE_BASE_DIR + File.separator + "Sequence-invalid-entrynumber-start-.tsv.gz";
            SequenceAssemblyWebinCli sequenceAssemblyWebinCli = new SequenceAssemblyWebinCli();
            sequenceAssemblyWebinCli.setValidationDir(new File(SEQUENCE_BASE_DIR));
            StringBuilder resultsSb = sequenceAssemblyWebinCli.validateTestTsv(testTsvFile);
            String expectedResults = new String(Files.readAllBytes(Paths.get(SEQUENCE_BASE_DIR  + File.separator + "Sequence-invalidEntrynumberStart-expected-results.txt")));
            assertEquals(resultsSb.toString().trim(), expectedResults.trim());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void nonAsciiCharacters() {
        try {
            String testTsvFile = SEQUENCE_BASE_DIR + File.separator + "Sequence-non-ascii-characters.gz";
            SequenceAssemblyWebinCli sequenceAssemblyWebinCli = new SequenceAssemblyWebinCli();
            sequenceAssemblyWebinCli.setValidationDir(new File(SEQUENCE_BASE_DIR));
            StringBuilder resultsSb = sequenceAssemblyWebinCli.validateTestTsv(testTsvFile);
            String expectedResults = new String(Files.readAllBytes(Paths.get(SEQUENCE_BASE_DIR  + File.separator + "Sequence-nonAsciiCharacters-expected-results.txt")));
            assertEquals(resultsSb.toString().replaceAll("\\s+",""), expectedResults.replaceAll("\\s+", ""));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    
	private GenomeAssemblyWebinCli getValidator(File file,FileType fileType)
	
	{
		SubmissionOptions options = new SubmissionOptions();
		if(file!=null)
		{
		SubmissionFiles files = new SubmissionFiles();
        SubmissionFile SubmissionFile= new SubmissionFile(fileType,file);
        files.addFile(SubmissionFile);
		options.submissionFiles = Optional.of(files);
		}
        options.assemblyInfoEntry = Optional.of(new AssemblyInfoEntry());
        options.context =Optional.of(Context.genome);
		options.isFixMode =true;
		options.isRemote =true;
		GenomeAssemblyWebinCli validator = new GenomeAssemblyWebinCli();
    	validator.setTestMode( true );
        validator.setStudy( new Study() );
        options.reportDir=Optional.of(WebinCliTestUtils.createTempDir().getAbsolutePath());
        validator.setSubmitDir(WebinCliTestUtils.createTempDir());
        validator.setSubmissionOptions(options);
        return validator;
	}
}
