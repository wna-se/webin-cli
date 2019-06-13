package uk.ac.ebi.ena.webin.cli.rawreads;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

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
import uk.ac.ebi.ena.readtools.webin.cli.rawreads.RawReadsFile.Filetype;
import uk.ac.ebi.ena.readtools.webin.cli.rawreads.ScannerMessage;
import uk.ac.ebi.ena.readtools.webin.cli.rawreads.ScannerMessage.ScannerErrorMessage;
import uk.ac.ebi.ena.webin.cli.WebinCliConfig;
import uk.ac.ebi.ena.webin.cli.WebinCliException;
import uk.ac.ebi.ena.webin.cli.WebinCliMessage;
import uk.ac.ebi.ena.webin.cli.WebinCliValidator;
import uk.ac.ebi.ena.webin.cli.manifest.ManifestReader.ManifestReaderState;
import uk.ac.ebi.ena.webin.cli.manifest.ManifestReaderResult;
import uk.ac.ebi.ena.webin.cli.manifest.processor.SampleProcessor;
import uk.ac.ebi.ena.webin.cli.manifest.processor.StudyProcessor;
import uk.ac.ebi.ena.webin.cli.rawreads.RawReadsManifest.Field;
import uk.ac.ebi.ena.webin.cli.reporter.ValidationMessageReporter;


public class 
RawReadsValidator implements WebinCliValidator
{
    private static final Logger log = LoggerFactory.getLogger( RawReadsValidator.class );
    
    public static class 
    ValidatorData
    {
        public int pairing_horizon;
        public List<RawReadsFile> files;
        public boolean is_paired;
    }
    
    private ValidatorData mdata;
    
    
    static 
    {
        System.setProperty( "samjdk.use_cram_ref_download", Boolean.TRUE.toString() );
    }


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
        return new ManifestDefinition( RawReadsManifest.getManifestFieldDefinition( sampleProcessor, studyProcessor ), 
                                       RawReadsManifest.getManifestFileCount() );
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
