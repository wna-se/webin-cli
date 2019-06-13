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
package uk.ac.ebi.ena.webin.cli.rawreads;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.ena.readtools.webin.cli.rawreads.RawReadsFile;
import uk.ac.ebi.ena.readtools.webin.cli.rawreads.RawReadsFile.ChecksumMethod;
import uk.ac.ebi.ena.webin.cli.AbstractWebinCli;
import uk.ac.ebi.ena.webin.cli.WebinCli;
import uk.ac.ebi.ena.webin.cli.WebinCliContext;
import uk.ac.ebi.ena.webin.cli.WebinCliException;
import uk.ac.ebi.ena.webin.cli.WebinCliMessage;
import uk.ac.ebi.ena.webin.cli.WebinCliValidator.WorkDirLayout;
import uk.ac.ebi.ena.webin.cli.manifest.processor.SampleProcessor;
import uk.ac.ebi.ena.webin.cli.manifest.processor.StudyProcessor;
import uk.ac.ebi.ena.webin.cli.rawreads.RawReadsManifest.Field;
import uk.ac.ebi.ena.webin.cli.submit.SubmissionBundle;
import uk.ac.ebi.ena.webin.cli.submit.SubmissionBundle.SubmissionXMLFile;
import uk.ac.ebi.ena.webin.cli.submit.SubmissionBundle.SubmissionXMLFileType;
import uk.ac.ebi.ena.webin.cli.utils.FileUtils;

public class 
RawReadsWebinCli extends AbstractWebinCli<RawReadsManifest>
{   
    private static final String RUN_XML = "run.xml";
    private static final String EXPERIMENT_XML = "experiment.xml";

    private String studyId;
    private String sampleId;
    public RawReadsValidator validator;
    
    public static class 
    RawReadsValidatorData
    {
        String  study_id;
        String  sample_id;
        String  name;
        String  description;
        String  instrument_model;
        String  library_strategy;
        String  library_source;
        String  library_selection;
        String  library_name;
        String  platform;
        Integer insert_size;
        Integer pairing_horizon;
        
        List<RawReadsFile> files = Collections.emptyList();
        
        Boolean is_paired;
    }
    
    
    private SampleProcessor sampleProcessor;
    private StudyProcessor  studyProcessor;
    
    
    //TODO value should be estimated via validation
    private boolean is_paired;

    private static final Logger log = LoggerFactory.getLogger(RawReadsWebinCli.class);
    RawReadsValidatorData mdata;
    

    @Override public WebinCliContext 
    getContext() 
    {
        return WebinCliContext.reads;
    }

    
    @Override protected RawReadsManifest 
    createManifestReader() 
    {
        SampleProcessor sampleProcessor = isMetadataServiceActive( MetadataService.SAMPLE ) ? new SampleProcessor( getParameters(), sample -> this.sampleId = sample.getBiosampleId() ) : null;
        StudyProcessor  studyProcessor  = isMetadataServiceActive( MetadataService.STUDY  ) ? new StudyProcessor(  getParameters(),  study -> this.studyId  = study.getProjectId() )    : null;
        validator = new RawReadsValidator( sampleProcessor, studyProcessor );
        // Create manifest parser which will also set the sample and study fields.
        return new RawReadsManifest( sampleProcessor, studyProcessor );
    }

    
    @Override protected void 
    readManifest( Path inputDir, File manifestFile )
    {
        getManifestReader().readManifest( inputDir, manifestFile );
        mdata = new RawReadsValidatorData();
        
        mdata.instrument_model  = getManifestReader().getInstrument();
        mdata.library_strategy  = getManifestReader().getLibraryStrategy();
        mdata.library_source    = getManifestReader().getLibrarySource();
        mdata.library_selection = getManifestReader().getLibrarySelection();
        mdata.library_name      = getManifestReader().getLibraryName();

        mdata.platform          = getManifestReader().getPlatform();
        mdata.insert_size       = getManifestReader().getInsertSize();
        mdata.sample_id         = getManifestReader().getSampleId();
        mdata.study_id          = getManifestReader().getStudyId();
        mdata.description       = getManifestReader().getDescription();
        mdata.name              = getManifestReader().getName();
        mdata.files             = getManifestReader().getRawReadFiles();
        mdata.pairing_horizon   = getManifestReader().getPairingHorizon();
        
        setDescription( mdata.description );
        setSampleId( mdata.sample_id );
        setStudyId( mdata.study_id );
        setName( mdata.name );
        
        validator.init( getManifestReader().getResult(), new WorkDirLayout() 
                                                         { 
                                                             { 
                                                                 input    = getParameters().getInputDir().toPath();
                                                                 validate = getParameters().getOutputDir().toPath().resolve( getContext().toString() ).resolve( getName() ).resolve( "validate" );
                                                                 process  = getParameters().getOutputDir().toPath().resolve( getContext().toString() ).resolve( getName() ).resolve( "process" );
                                                                 submit   = getParameters().getOutputDir().toPath().resolve( getContext().toString() ).resolve( getName() ).resolve( "submit" );
                                                             } } );
    }

    
    @Override public void
    validate() throws WebinCliException
    {
        if( !FileUtils.emptyDirectory( getValidationDir() ) )
            throw WebinCliException.systemError( WebinCliMessage.Cli.EMPTY_DIRECTORY_ERROR.format( getValidationDir() ) );

        if( !FileUtils.emptyDirectory( getProcessDir() ) )
            throw WebinCliException.systemError( WebinCliMessage.Cli.EMPTY_DIRECTORY_ERROR.format( getProcessDir() ) );

        if( !FileUtils.emptyDirectory( getSubmitDir() ) )
            throw WebinCliException.systemError( WebinCliMessage.Cli.EMPTY_DIRECTORY_ERROR.format( getSubmitDir() ) );

        validator.validate();
        
        is_paired = Boolean.valueOf( getManifestReader().getResult().getValue( Field.__PAIRED ) );
        
//        is_paired = mdata.is_paired;
        
        if( !validator.getValidationResult().isValid() )
            throw WebinCliException.validationError("");
    }


    @Override public void
    prepareSubmissionBundle() throws WebinCliException
    {
        try
        {
            List<RawReadsFile> files = mdata.files;
            
            List<File> uploadFileList = files.stream().map( e -> new File( e.getFilename() ) ).collect( Collectors.toList() );
            Path uploadDir = getUploadRoot().resolve( Paths.get( String.valueOf( getContext() ), WebinCli.getSafeOutputDir( getName() ) ) );
            files.forEach( e -> e.setChecksumMethod( ChecksumMethod.MD5 ) );
            files.forEach( e -> {
                try
                {
                    e.setChecksum( FileUtils.calculateDigest( String.valueOf( e.getChecksumMethod() ), new File( e.getFilename() ) ) );
                } catch( NoSuchAlgorithmException | IOException e1 )
                {
                    throw new RuntimeException( e1 );
                }
            } );
            List<Element> eList = files.stream()
                                       .sequential()
                                       .map( e -> e.toElement( "FILE", uploadDir ) )
                                       .collect( Collectors.toList() );
    
            //do something
            String experiment_ref = getAlias();
            
            String e_xml = createExperimentXml( experiment_ref, getParameters().getCenterName(), is_paired, mdata.description );
            String r_xml = createRunXml( eList, experiment_ref, getParameters().getCenterName() );
            
            Path runXmlFile = getSubmitDir().toPath().resolve( RUN_XML );
            Path experimentXmlFile = getSubmitDir().toPath().resolve( EXPERIMENT_XML );
            
            Files.write( runXmlFile, r_xml.getBytes( StandardCharsets.UTF_8 ), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC );
            Files.write( experimentXmlFile, e_xml.getBytes( StandardCharsets.UTF_8 ), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC );
            
            setSubmissionBundle( new SubmissionBundle( getSubmitDir(), 
                                                       uploadDir.toString(), 
                                                       uploadFileList,
                                                       Arrays.asList( new SubmissionXMLFile( SubmissionXMLFileType.EXPERIMENT, experimentXmlFile.toFile(), FileUtils.calculateDigest( "MD5", experimentXmlFile.toFile() ) ), 
                                                                      new SubmissionXMLFile( SubmissionXMLFileType.RUN, runXmlFile.toFile(), FileUtils.calculateDigest( "MD5", runXmlFile.toFile() ) ) ),
                                                       getParameters().getCenterName(),
                                                       FileUtils.calculateDigest( "MD5", getParameters().getManifestFile() ) ) );
        } catch( NoSuchAlgorithmException | IOException ex )
        {
            throw WebinCliException.systemError( ex );
        }
    }

/*
    <RUN_SET>
    <RUN alias="" center_name="" run_center="blah">
        <EXPERIMENT_REF refname="" />
        <DATA_BLOCK>
            <FILES>
                <FILE filename="test_forward.fastq.gz" filetype="fastq" checksum="5aeca824118be49da0982bef9b57e689" checksum_method="MD5" quality_scoring_system="phred" ascii_offset="!" quality_encoding="ascii">
                    <READ_LABEL>F</READ_LABEL>
                </FILE>
                <FILE filename="test_reverse.fastq.gz" filetype="fastq" checksum="216e1803c0f22825caa58aa3622a0be5" checksum_method="MD5" quality_scoring_system="phred" ascii_offset="!" quality_encoding="ascii">
                    <READ_LABEL>R</READ_LABEL>
                </FILE>
            </FILES>
        </DATA_BLOCK>
    </RUN>
    </RUN_SET>
*/

    private String
    createExperimentXml( String experiment_ref, String centerName, boolean is_paired, String design_description )
    {
        String instrument_model = mdata.instrument_model;
        
        design_description = StringUtils.isBlank( design_description ) ? "unspecified" : design_description;
        
        String library_strategy  = mdata.library_strategy;
        String library_source    = mdata.library_source;
        String library_selection = mdata.library_selection;
        String library_name      = mdata.library_name;

        String platform  = mdata.platform;
        Integer insert_size = mdata.insert_size;
                
        try 
        {
            String full_name = WebinCliContext.reads.getXmlTitle( getName() );
            Element experimentSetE = new Element( "EXPERIMENT_SET" );
            Element experimentE = new Element( "EXPERIMENT" );
            experimentSetE.addContent( experimentE );
            
            Document doc = new Document( experimentSetE );
            experimentE.setAttribute( "alias", experiment_ref );
            
            if( null != centerName && !centerName.isEmpty() )
                experimentE.setAttribute( "center_name", centerName );
            
            experimentE.addContent( new Element( "TITLE" ).setText( full_name ) );
            
            Element studyRefE = new Element( "STUDY_REF" );
            experimentE.addContent( studyRefE );
            studyRefE.setAttribute( "accession", studyId );
  
            Element designE = new Element( "DESIGN" );
            experimentE.addContent( designE );
            
            Element designDescriptionE = new Element( "DESIGN_DESCRIPTION" );
            designDescriptionE.setText( design_description );
            designE.addContent( designDescriptionE );
            
            Element sampleDescriptorE = new Element( "SAMPLE_DESCRIPTOR" );
            sampleDescriptorE.setAttribute( "accession", sampleId );

            designE.addContent( sampleDescriptorE );

            Element libraryDescriptorE = new Element( "LIBRARY_DESCRIPTOR" );
            designE.addContent( libraryDescriptorE );
            
            if( null != library_name )
            {
                Element libraryNameE = new Element( "LIBRARY_NAME" );
                libraryNameE.setText( library_name );
                libraryDescriptorE.addContent( libraryNameE );
            }   
            
            Element libraryStrategyE = new Element( "LIBRARY_STRATEGY" );
            libraryStrategyE.setText( library_strategy );
            libraryDescriptorE.addContent( libraryStrategyE );
            
            Element librarySourceE = new Element( "LIBRARY_SOURCE" );
            librarySourceE.setText( library_source );
            libraryDescriptorE.addContent( librarySourceE );
            
            Element librarySelectionE = new Element( "LIBRARY_SELECTION" );
            librarySelectionE.setText( library_selection );
            libraryDescriptorE.addContent( librarySelectionE );

            Element libraryLayoutE = new Element( "LIBRARY_LAYOUT" );
            if( !is_paired )
            {
                libraryLayoutE.addContent( new Element( "SINGLE" ) );
            } else
            {
                Element pairedE = new Element( "PAIRED" );
                libraryLayoutE.addContent( pairedE );
                
                if( null != insert_size )
                    pairedE.setAttribute( "NOMINAL_LENGTH", String.valueOf( insert_size ) );
            }

            libraryDescriptorE.addContent( libraryLayoutE );
            
            Element platformE = new Element( "PLATFORM" );
            experimentE.addContent( platformE );
            
            Element platformRefE = new Element( platform );
            platformE.addContent( platformRefE );
            Element instrumentModelE = new Element( "INSTRUMENT_MODEL" );
            instrumentModelE.setText( instrument_model );
            platformRefE.addContent( instrumentModelE );
            
            XMLOutputter xmlOutput = new XMLOutputter();
            xmlOutput.setFormat( Format.getPrettyFormat() );
            StringWriter stringWriter = new StringWriter();
            xmlOutput.output( doc, stringWriter );
            return stringWriter.toString();
            
        } catch( IOException ex )
        {
            throw WebinCliException.systemError( ex );
        }
    }
    
    
    String
    createRunXml( List<Element> fileList, String experiment_ref, String centerName  ) 
    {
        try 
        {
            String full_name = WebinCliContext.reads.getXmlTitle( getName() );
            Element runSetE = new Element( "RUN_SET" );
            Element runE = new Element( "RUN" );
            runSetE.addContent( runE );
            
            Document doc = new Document( runSetE );
            runE.setAttribute( "alias", getAlias() );
            
            if( null != centerName && !centerName.isEmpty() )
                runE.setAttribute( "center_name", centerName );
            
            runE.addContent( new Element( "TITLE" ).setText( full_name ) );
            Element experimentRefE = new Element( "EXPERIMENT_REF" );
            runE.addContent( experimentRefE );
            experimentRefE.setAttribute( "refname", experiment_ref );
            
            Element dataBlockE = new Element( "DATA_BLOCK" );
            runE.addContent( dataBlockE );
            Element filesE = new Element( "FILES" );
            dataBlockE.addContent( filesE );
            
            for( Element e: fileList )
                filesE.addContent( e );
            
            XMLOutputter xmlOutput = new XMLOutputter();
            xmlOutput.setFormat( Format.getPrettyFormat() );
            StringWriter stringWriter = new StringWriter();
            xmlOutput.output( doc, stringWriter );
            return stringWriter.toString();
            
        } catch( IOException ex )
        {
            throw WebinCliException.systemError( ex );
        }
    }

    
    public void
    setStudyId( String studyId )
    {
        this.studyId = studyId;
    }
    

    public void
    setSampleId( String sampleId )
    {
        this.sampleId = sampleId;
    }


}
