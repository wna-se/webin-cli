package uk.ac.ebi.ena.webin.cli.rawreads;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import htsjdk.samtools.SAMFormatException;
import htsjdk.samtools.cram.CRAMException;
import uk.ac.ebi.embl.api.validation.DefaultOrigin;
import uk.ac.ebi.embl.api.validation.Origin;
import uk.ac.ebi.embl.api.validation.Severity;
import uk.ac.ebi.embl.api.validation.ValidationMessage;
import uk.ac.ebi.embl.api.validation.ValidationResult;
import uk.ac.ebi.ena.readtools.webin.cli.rawreads.BamScanner;
import uk.ac.ebi.ena.readtools.webin.cli.rawreads.FastqScanner;
import uk.ac.ebi.ena.readtools.webin.cli.rawreads.RawReadsFile;
import uk.ac.ebi.ena.readtools.webin.cli.rawreads.RawReadsFile.AsciiOffset;
import uk.ac.ebi.ena.readtools.webin.cli.rawreads.RawReadsFile.Filetype;
import uk.ac.ebi.ena.readtools.webin.cli.rawreads.RawReadsFile.QualityScoringSystem;
import uk.ac.ebi.ena.readtools.webin.cli.rawreads.ScannerMessage;
import uk.ac.ebi.ena.readtools.webin.cli.rawreads.ScannerMessage.ScannerErrorMessage;
import uk.ac.ebi.ena.webin.cli.WebinCliConfig;
import uk.ac.ebi.ena.webin.cli.WebinCliException;
import uk.ac.ebi.ena.webin.cli.WebinCliMessage;
import uk.ac.ebi.ena.webin.cli.WebinCliValidator;
import uk.ac.ebi.ena.webin.cli.manifest.ManifestCVList;
import uk.ac.ebi.ena.webin.cli.manifest.ManifestFieldDefinition;
import uk.ac.ebi.ena.webin.cli.manifest.ManifestFieldProcessor;
import uk.ac.ebi.ena.webin.cli.manifest.ManifestFieldType;
import uk.ac.ebi.ena.webin.cli.manifest.ManifestFieldValue;
import uk.ac.ebi.ena.webin.cli.manifest.ManifestFileCount;
import uk.ac.ebi.ena.webin.cli.manifest.ManifestFileGroup;
import uk.ac.ebi.ena.webin.cli.manifest.ManifestFileSuffix;
import uk.ac.ebi.ena.webin.cli.manifest.ManifestReader.ManifestReaderState;
import uk.ac.ebi.ena.webin.cli.manifest.ManifestReaderResult;
import uk.ac.ebi.ena.webin.cli.manifest.processor.ASCIIFileNameProcessor;
import uk.ac.ebi.ena.webin.cli.manifest.processor.CVFieldProcessor;
import uk.ac.ebi.ena.webin.cli.manifest.processor.FileSuffixProcessor;
import uk.ac.ebi.ena.webin.cli.manifest.processor.PositiveIntegerProcessor;
import uk.ac.ebi.ena.webin.cli.manifest.processor.SampleProcessor;
import uk.ac.ebi.ena.webin.cli.manifest.processor.StudyProcessor;
import uk.ac.ebi.ena.webin.cli.reporter.ValidationMessageReporter;


public class 
RawReadsValidator implements WebinCliValidator
{
    private static final Logger log = LoggerFactory.getLogger( RawReadsValidator.class );


    public interface 
    Field
    {
        String NAME = "NAME";
        String STUDY = "STUDY";
        String SAMPLE = "SAMPLE";
        String PLATFORM = "PLATFORM";
        String INSTRUMENT = "INSTRUMENT";
        String DESCRIPTION = "DESCRIPTION";
        String LIBRARY_SOURCE = "LIBRARY_SOURCE";
        String LIBRARY_SELECTION = "LIBRARY_SELECTION";
        String LIBRARY_STRATEGY = "LIBRARY_STRATEGY";
        String LIBRARY_CONSTRUCTION_PROTOCOL = "LIBRARY_CONSTRUCTION_PROTOCOL";
        String LIBRARY_NAME = "LIBRARY_NAME";
        String INSERT_SIZE = "INSERT_SIZE";
        String QUALITY_SCORE = "QUALITY_SCORE";
        String __HORIZON = "__HORIZON";
        String FASTQ = "FASTQ";
        String BAM = "BAM";
        String CRAM = "CRAM";

        String __PAIRED = "__PAIRED";
        
    }

    
    public interface
    Description
    {
        String NAME = "Unique sequencing experiment name";
        String STUDY = "Study accession or name";
        String SAMPLE = "Sample accession or name";
        String PLATFORM = "Sequencing platform";
        String INSTRUMENT = "Sequencing instrument";
        String DESCRIPTION = "Experiment description";
        String LIBRARY_SOURCE = "Source material";
        String LIBRARY_SELECTION = "Method used to select or enrich the source material";
        String LIBRARY_STRATEGY = "Sequencing technique";
        String LIBRARY_CONSTRUCTION_PROTOCOL = "Protocol used to construct the sequencing library";
        String LIBRARY_NAME = "Library name";
        String INSERT_SIZE = "Insert size for paired reads";
        String QUALITY_SCORE = "";
        String __HORIZON = "";
        String FASTQ = "Fastq file";
        String BAM = "BAM file";
        String CRAM = "CRAM file";
    }

    
    private final static String INSTRUMENT_UNSPECIFIED = "unspecified";
    private final static String QUALITY_SCORE_PHRED_33 = "PHRED_33";
    private final static String QUALITY_SCORE_PHRED_64 = "PHRED_64";
    private final static String QUALITY_SCORE_LOGODDS  = "LOGODDS";

    public final static ManifestCVList CV_INSTRUMENT = new ManifestCVList( new File( "uk/ac/ebi/ena/webin/cli/rawreads/instrument.properties" ) );
    public final static ManifestCVList CV_PLATFORM = new ManifestCVList( new File( "uk/ac/ebi/ena/webin/cli/rawreads/platform.properties" ) );
    public final static ManifestCVList CV_SELECTION = new ManifestCVList( new File( "uk/ac/ebi/ena/webin/cli/rawreads/selection.properties" ) );
    public final static ManifestCVList CV_SOURCE = new ManifestCVList( new File( "uk/ac/ebi/ena/webin/cli/rawreads/source.properties" ) );
    public final static ManifestCVList CV_STRATEGY = new ManifestCVList( new File( "uk/ac/ebi/ena/webin/cli/rawreads/strategy.properties" ) );
    public final static ManifestCVList CV_QUALITY_SCORE = new ManifestCVList( QUALITY_SCORE_PHRED_33, QUALITY_SCORE_PHRED_64, QUALITY_SCORE_LOGODDS );
    
    
    public static class 
    ValidatorData
    {
        public int pairing_horizon;
        public List<RawReadsFile> files;
        public boolean is_paired;
    }
    
    private ValidatorData mdata;
    
    private Path validation_path;
    private Path processing_path;
    private SampleProcessor sampleProcessor;
    private StudyProcessor  studyProcessor;
    private ManifestReaderResult reader_result;
    
    public 
    RawReadsValidator( SampleProcessor sampleProcessor, StudyProcessor studyProcessor )
    {
        this.sampleProcessor = sampleProcessor;
        this. studyProcessor = studyProcessor;
    }
    
    
    // Directory creation.
    static File
    getReportFile( File dir, String filename, String suffix )
    {
        if( dir == null || !dir.isDirectory() )
            throw WebinCliException.systemError( WebinCliMessage.Cli.INVALID_REPORT_DIR_ERROR.format(filename ));

        return new File( dir, Paths.get( filename ).getFileName().toString() + suffix );
    }

    
    protected File
    getReportFile( String filename )
    {
        return getReportFile( getValidationPath().toFile(), filename, WebinCliConfig.REPORT_FILE_SUFFIX );
    }
    
    
    @Override public ValidationResult 
    getValidationResult()
    {
        return new ValidationResult();    
    }

    
    @Override public ManifestDefinition 
    getManifestDefinition()
    {
        return new ManifestDefinition( getManifestFieldDefinition( sampleProcessor, studyProcessor ), 
                                       getManifestFileCount() );
    }
    
    
    private List<ManifestFieldDefinition> 
    getManifestFieldDefinition( SampleProcessor sampleProcessor, StudyProcessor studyProcessor )
    {
        return // Fields.
        new ManifestFieldDefinition.Builder()
            .meta().required().name( Field.NAME        ).desc( Description.NAME ).and()
            .meta().required().name( Field.STUDY       ).desc( Description.STUDY ).processor( studyProcessor ).and()
            .meta().required().name( Field.SAMPLE      ).desc( Description.SAMPLE ).processor( sampleProcessor ).and()
            .meta().optional().name( Field.DESCRIPTION ).desc( Description.DESCRIPTION ).and()
            .meta().optional().name( Field.INSTRUMENT  ).desc( Description.INSTRUMENT ).processor( new CVFieldProcessor( CV_INSTRUMENT ) ).and()
            .meta().optional().name( Field.PLATFORM    ).desc( Description.PLATFORM ).processor( new CVFieldProcessor( CV_PLATFORM ) ).and()
            .meta().required().name( Field.LIBRARY_SOURCE                ).desc( Description.LIBRARY_SOURCE ).processor( new CVFieldProcessor( CV_SOURCE ) ).and()
            .meta().required().name( Field.LIBRARY_SELECTION             ).desc( Description.LIBRARY_SELECTION ).processor( new CVFieldProcessor( CV_SELECTION ) ).and()
            .meta().required().name( Field.LIBRARY_STRATEGY              ).desc( Description.LIBRARY_STRATEGY ).processor( new CVFieldProcessor( CV_STRATEGY ) ).and()
            .meta().optional().name( Field.LIBRARY_CONSTRUCTION_PROTOCOL ).desc( Description.LIBRARY_CONSTRUCTION_PROTOCOL ).and()
            .meta().optional().name( Field.LIBRARY_NAME                  ).desc( Description.LIBRARY_NAME ).and()
            .meta().optional().name( Field.INSERT_SIZE ).desc( Description.INSERT_SIZE ).processor( new PositiveIntegerProcessor( null ) ).and()
            
            .file().optional( 2 ).name( Field.FASTQ ).desc( Description.FASTQ ).processor( getFastqProcessors() ).and()
            .file().optional( 1 ).name( Field.BAM   ).desc( Description.BAM   ).processor( getBamProcessors()   ).and()
            .file().optional( 1 ).name( Field.CRAM  ).desc( Description.CRAM  ).processor( getCramProcessors()  ).and()
               
            .meta().optional().name( Field.QUALITY_SCORE ).desc( Description.QUALITY_SCORE ).processor( new CVFieldProcessor( CV_QUALITY_SCORE ) ).and()
            .meta().optional().name( Field.__HORIZON     ).desc( Description.__HORIZON ).defaultValue( String.valueOf( 500_000_000 ) ).processor( new PositiveIntegerProcessor( null ) ).and()

            .data().name( Field.__PAIRED ).defaultValue( String.valueOf( Boolean.FALSE ) ).desc( "paired from validator" )
            .build();
    }
    
    
    private ArrayList<ManifestFileGroup> 
    getManifestFileCount()
    {
        return // File groups.
        new ManifestFileCount.Builder()
                             .group()
                             .required( Field.FASTQ, 2 )
                             .and().group()
                             .required( Field.CRAM )
                             .and().group()
                             .required( Field.BAM )
                             .build();
    }

    
    private ManifestFieldProcessor[] 
    getFastqProcessors() 
    {
        return new ManifestFieldProcessor[] 
               {
                   new ASCIIFileNameProcessor(),
                   new FileSuffixProcessor( ManifestFileSuffix.GZIP_OR_BZIP_FILE_SUFFIX ) 
               };
    }

    
    private static ManifestFieldProcessor[] 
    getBamProcessors() 
    {
        return new ManifestFieldProcessor[] {
                new ASCIIFileNameProcessor(),
                new FileSuffixProcessor( ManifestFileSuffix.BAM_FILE_SUFFIX ) };
    }

    
    private static ManifestFieldProcessor[] 
    getCramProcessors() 
    {
        return new ManifestFieldProcessor[] {
                new ASCIIFileNameProcessor(),
                new FileSuffixProcessor( ManifestFileSuffix.CRAM_FILE_SUFFIX ) };
    }
  
    
    
    
    @Override public void 
    init( ManifestReaderResult reader_result, WorkDirLayout layout )
    {
        RawReadsManifest rrm = new RawReadsManifest();
        rrm.setState( new ManifestReaderState( layout.input, null ) );
        rrm.processManifest( reader_result );
        rrm.getName();
        mdata = new ValidatorData();
        mdata.pairing_horizon = rrm.getPairingHorizon();
        mdata.files           = rrm.getRawReadFiles();
        
        this.reader_result = reader_result;
        setProcessingPath( layout.process );
        setValidationPath( layout.validate );
        processInstrumentAndPlatform( reader_result.getField( Field.PLATFORM ), reader_result.getField( Field.INSTRUMENT ) );
        
        processFiles( reader_result, layout.input );
    }

    
//    @Override public void
//    processManifest( ManifestReaderResult result )
//    {
//        name        = result.getValue( Field.NAME );
//        study_id    = result.getValue( Field.STUDY );
//        sample_id   = result.getValue( Field.SAMPLE );
//        description = result.getValue( Field.DESCRIPTION );
//        instrument  = result.getValue( Field.INSTRUMENT );
//        platform    = result.getValue( Field.PLATFORM );
//
//        insert_size = result.getField( Field.INSERT_SIZE );
//        library_source = result.getValue( Field.LIBRARY_SOURCE );
//        library_selection = result.getValue( Field.LIBRARY_SELECTION );
//        library_strategy  = result.getValue( Field.LIBRARY_STRATEGY );
//
//        library_construction_protocol = result.getValue( Field.LIBRARY_CONSTRUCTION_PROTOCOL );
//        library_name = result.getValue( Field.LIBRARY_NAME );
//
//        if( result.getValue( Field.QUALITY_SCORE ) != null )
//        {
//            switch( result.getValue( Field.QUALITY_SCORE ) )
//            {
//            case QUALITY_SCORE_PHRED_33:
//                asciiOffset = AsciiOffset.FROM33;
//                qualityScoringSystem = QualityScoringSystem.phred;
//                break;
//            case QUALITY_SCORE_PHRED_64:
//                asciiOffset = AsciiOffset.FROM64;
//                qualityScoringSystem = QualityScoringSystem.phred;
//                break;
//            case QUALITY_SCORE_LOGODDS:
//                asciiOffset = null;
//                qualityScoringSystem = QualityScoringSystem.log_odds;
//                break;
//            }
//        }
//
//        if( result.getCount( Field.__HORIZON ) > 0 )
//            pairing_horizon = result.getField( Field.__HORIZON );
//
//        processInstrumentAndPlatform();
//        processFiles( result );
//    }
//    
    
    //TODO move to Processor
    private List<RawReadsFile>
    processFiles( ManifestReaderResult manifest_result,
                  Path                 inputDir )
                  
    {
        QualityScoringSystem qualityScoringSystem = null;
        AsciiOffset          asciiOffset = null;
        if( manifest_result.getValue( Field.QUALITY_SCORE ) != null )
        {
            switch( manifest_result.getValue( Field.QUALITY_SCORE ) )
            {
            case QUALITY_SCORE_PHRED_33:
                asciiOffset = AsciiOffset.FROM33;
                qualityScoringSystem = QualityScoringSystem.phred;
                break;
            case QUALITY_SCORE_PHRED_64:
                asciiOffset = AsciiOffset.FROM64;
                qualityScoringSystem = QualityScoringSystem.phred;
                break;
            case QUALITY_SCORE_LOGODDS:
                asciiOffset = null;
                qualityScoringSystem = QualityScoringSystem.log_odds;
                break;
            }
        }

        List<RawReadsFile> files = manifest_result.getFields()
                                                  .stream()
                                                  .filter( field -> field.getDefinition().getType() == ManifestFieldType.MANIFEST_FILE )
                                                  .map( field -> createReadFile( inputDir, field ) )
                                                  .collect( Collectors.toList() );

        // Set FASTQ quality scoring system and ascii offset.
        for( RawReadsFile f : files )
        {
            if( f.getFiletype().equals( Filetype.fastq ) )
            {
                if( qualityScoringSystem != null )
                    f.setQualityScoringSystem( qualityScoringSystem );
                if( asciiOffset != null )
                    f.setAsciiOffset( asciiOffset );
            }
        }
        
        return files;
    }
    
    
    static RawReadsFile
    createReadFile( Path inputDir, ManifestFieldValue field )
    {
        assert( field.getDefinition().getType() == ManifestFieldType.MANIFEST_FILE );

        RawReadsFile f = new RawReadsFile();
        f.setInputDir( inputDir );
        f.setFiletype( Filetype.valueOf( field.getName().toLowerCase() ) );

        String fileName = field.getValue();
        if( !Paths.get( fileName ).isAbsolute() )
            f.setFilename( inputDir.resolve( Paths.get( fileName ) ).toString() );
        else
            f.setFilename( fileName );

        return f;
    }
    
    
    protected final void
    error( WebinCliMessage message, Object... arguments )
    {
        Origin origin = new DefaultOrigin( getClass().getSimpleName() );
        error( message, origin, arguments );
    }

    
    private void
    error( WebinCliMessage message, Origin origin, Object... arguments )
    {
        throw WebinCliException.userError( WebinCliMessage.error( message, origin, arguments ).getMessage() );
    }

    
    //TODO move to Processor
    private void
    processInstrumentAndPlatform( ManifestFieldValue platform_value, ManifestFieldValue instrument_value )
    {
        String platform   = platform_value.getValue();
        String instrument = instrument_value.getValue();
        
        if( null == platform && ( null == instrument || instrument.equals( INSTRUMENT_UNSPECIFIED ) ) )
        {
            error( WebinCliMessage.Manifest.MISSING_PLATFORM_AND_INSTRUMENT_ERROR,
                   String.join( ", ", CV_PLATFORM.keyList() ),
                   String.join( ", ", CV_INSTRUMENT.keyList() ) );
        }

        if( instrument != null )
        {
            // Set platform.
            String platforms = CV_INSTRUMENT.getValue( instrument );
            if( StringUtils.isBlank( platforms ) )
            {
                error( WebinCliMessage.Manifest.MISSING_PLATFORM_FOR_INSTRUMENT_ERROR, instrument );
            }

            String[] platformList = platforms.split( "[;,]" );

            if( 1 == platformList.length )
            {
                platform = CV_PLATFORM.getKey( platformList[ 0 ] );
                
            } else if( !Stream.of( platformList ).collect( Collectors.toSet() ).contains( platform ) )
            {
                error( WebinCliMessage.Manifest.INVALID_PLATFORM_FOR_INSTRUMENT_ERROR,
                       StringUtils.isBlank( platform ) ? "is not defined" : platform + " is not supported",
                       instrument,
                       CV_INSTRUMENT.getValue( instrument ) );
            }
        } else
        {
            instrument = INSTRUMENT_UNSPECIFIED;
        }

        platform_value.setValue( platform );
        instrument_value.setValue( platform );
    }


    private boolean
    readBamFile( List<RawReadsFile> files, AtomicBoolean paired ) throws WebinCliException
    {
        BamScanner scanner = new BamScanner() 
        {
            @Override protected void 
            logProcessedReadNumber( long count )
            {
                RawReadsValidator.this.logProcessedReadNumber( count );
            }
        };
        
        
        boolean valid = true;
        for( RawReadsFile rf : files )
        {
            try( ValidationMessageReporter reporter = new ValidationMessageReporter( getReportFile( rf.getFilename() ) ) ) 
            {
                try 
                {
                    String msg = String.format( "Processing file %s\n", rf.getFilename() );
                    log.info( msg );
                    
                    List<ScannerMessage> list = Filetype.cram == rf.getFiletype() ? scanner.readCramFile( rf, paired ) : scanner.readBamFile( rf, paired );
                    List<ValidationMessage<Origin>> mv_list = list.stream().sequential().map( sm ->fMsg( sm ) ).collect( Collectors.toList() );
                    mv_list.stream().forEachOrdered( m -> reporter.write( m ) );
                    valid = new ValidationResult().append( mv_list ).isValid();
                } catch( SAMFormatException | CRAMException e )
                {
                    reporter.write( Severity.ERROR, e.getMessage() );
                    valid = false;

                } catch( IOException ex )
                {
                    throw WebinCliException.systemError( ex );
                }
            }
        }
        return valid;
    }
    
    
    private ValidationMessage<Origin>
    fMsg( ScannerMessage sm )
    {   
        Severity severity = sm instanceof ScannerErrorMessage ? Severity.ERROR : Severity.INFO;
        
        ValidationMessage<Origin> result = new ValidationMessage<>( severity, ValidationMessage.NO_KEY );
        result.setMessage( sm.getMessage() );
        if( null != sm.getOrigin() )
            result.append( new DefaultOrigin(  sm.getOrigin() ) );
        
        return result;
    }


    private boolean
    readFastqFile( List<RawReadsFile> files, AtomicBoolean paired )
    {
        try
        {
            if( files.size() > 2 )
            {
                String msg = "Unable to validate unusual amount of files: " + files;
                reportToFileList( files, msg );
                throw WebinCliException.validationError( msg );
            }
            
            
            FastqScanner fs = new FastqScanner( mdata.pairing_horizon )
            {
                @Override protected void 
                logProcessedReadNumber( long count )
                {
                    RawReadsValidator.this.logProcessedReadNumber( count );
                }
                
                
                @Override protected void 
                logFlushMsg( String msg )
                {
                    RawReadsValidator.this.logFlushMsg( msg );
                    
                }
            };            
            
            List<ScannerMessage> sm_list = fs.checkFiles( files.toArray( new RawReadsFile[ files.size() ] ) );
            ValidationResult vr = new ValidationResult();
            vr.append( sm_list.stream().map( e -> fMsg( e ) ).collect( Collectors.toList() ) );
            paired.set( fs.getPaired() );
            files.forEach(rf -> {
                try (ValidationMessageReporter reporter = new ValidationMessageReporter( getReportFile( rf.getFilename() ))) {
                    reporter.write(vr);
                }
            });
            return vr.isValid();

        } catch( Throwable ex )
        {
            throw WebinCliException.systemError( ex, "Unable to validate file(s): " + files );
        }
    }


    private void 
    reportToFileList( List<RawReadsFile> files, String msg )
    {
        for( RawReadsFile rf : files )
        {
            try( ValidationMessageReporter reporter = new ValidationMessageReporter( getReportFile( rf.getFilename() ) ) ) 
            {
                reporter.write( Severity.ERROR, msg );
            }
        }
    }

    
    @Override public void
    validate() throws WebinCliException
    {
        boolean valid = true;
        AtomicBoolean paired = new AtomicBoolean();
        
        List<RawReadsFile> files = mdata.files;

        for( RawReadsFile rf : files )
        {
            if( Filetype.fastq.equals( rf.getFiletype() ) )
            {
                valid = readFastqFile( files, paired );
            } else if( Filetype.bam.equals( rf.getFiletype() ) )
            {
                valid = readBamFile( files, paired );
            } else if( Filetype.cram.equals( rf.getFiletype() ) )
            {
                valid = readBamFile( files, paired );
            } else 
            {
                throw WebinCliException.systemError( WebinCliMessage.Cli.UNSUPPORTED_FILETYPE_ERROR.format( rf.getFiletype().name() ) );
            }
            break;
        }

        
        this.reader_result.getField( Field.__PAIRED ).setValue( String.valueOf( paired.get() ) );
        //mdata.is_paired = paired.get();
        
        if( !valid )
            throw WebinCliException.validationError("");
    }

    
    private void 
    logProcessedReadNumber( long count )
    {
        String msg = String.format( "\rProcessed %16d read(s)", count );
        logFlushMsg( msg );
    }

    
    private void
    logFlushMsg( String msg )
    {
        System.out.print( msg );
        System.out.flush();
    }
    
    
    public void 
    setValidationPath( Path path )
    {
        this.validation_path = path;
    }

    
    public Path 
    getValidationPath()
    {
        return validation_path;
    }


    public void 
    setProcessingPath( Path path )
    {
        this.processing_path = path;
    }


    public Path 
    getProcessingPath()
    {
        return processing_path;
    }
}
