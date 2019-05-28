package uk.ac.ebi.ena.readtools.webin.cli.rawreads;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import htsjdk.samtools.DefaultSAMRecordFactory;
import htsjdk.samtools.SAMFormatException;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamFiles;
import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.Log.LogLevel;
import uk.ac.ebi.embl.api.validation.DefaultOrigin;
import uk.ac.ebi.embl.api.validation.Origin;
import uk.ac.ebi.embl.api.validation.Severity;
import uk.ac.ebi.embl.api.validation.ValidationMessage;
import uk.ac.ebi.embl.api.validation.ValidationResult;
import uk.ac.ebi.ena.readtools.cram.ref.ENAReferenceSource;
import uk.ac.ebi.ena.readtools.webin.cli.rawreads.RawReadsFile.Filetype;
import uk.ac.ebi.ena.readtools.webin.cli.rawreads.refs.CramReferenceInfo;
import uk.ac.ebi.ena.webin.cli.WebinCliConfig;
import uk.ac.ebi.ena.webin.cli.WebinCliException;
import uk.ac.ebi.ena.webin.cli.WebinCliMessage;
import uk.ac.ebi.ena.webin.cli.manifest.ManifestReader.ManifestReaderState;
import uk.ac.ebi.ena.webin.cli.manifest.ManifestReaderResult;
import uk.ac.ebi.ena.webin.cli.manifest.processor.SampleProcessor;
import uk.ac.ebi.ena.webin.cli.manifest.processor.StudyProcessor;
import uk.ac.ebi.ena.webin.cli.rawreads.RawReadsManifest;
import uk.ac.ebi.ena.webin.cli.rawreads.RawReadsManifest.Field;
import uk.ac.ebi.ena.webin.cli.reporter.ValidationMessageReporter;


public class 
RawReadsValidator implements WebinCliValidator
{
    private static final Logger log = LoggerFactory.getLogger( RawReadsValidator.class );
    private static final String BAM_STAR = "*";
    
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
        boolean valid = true;
        for( RawReadsFile rf : files )
        {
            long read_no = 0;
            long reads_cnt = 0;

            try( ValidationMessageReporter reporter = new ValidationMessageReporter( getReportFile( rf.getFilename() ) ) ) 
            {
                try 
                {
                    String msg = String.format( "Processing file %s\n", rf.getFilename() );
                    log.info( msg );
                    
                    ENAReferenceSource reference_source = new ENAReferenceSource();
                    reference_source.setLoggerWrapper( new ENAReferenceSource.LoggerWrapper() 
                    {
                        @Override public void
                        error( Object... messageParts )
                        {
                            reporter.write( Severity.ERROR, null == messageParts ? "null" : String.valueOf( Arrays.asList( messageParts ) ) );
                        }

                        @Override public void
                        warn( Object... messageParts )
                        {
                            reporter.write( Severity.WARNING, null == messageParts ? "null" : String.valueOf( Arrays.asList( messageParts ) ) );
                        }

                        @Override public void
                        info( Object... messageParts )
                        {
                            reporter.write( Severity.INFO, null == messageParts ? "null" : String.valueOf( Arrays.asList( messageParts ) ) );
                        }
                    } );

                    reporter.write( Severity.INFO, "REF_PATH  " + reference_source.getRefPathList() );
                    reporter.write( Severity.INFO, "REF_CACHE " + reference_source.getRefCacheList() );


                    File file = new File( rf.getFilename() );
                    Log.setGlobalLogLevel( LogLevel.ERROR );
                    SamReaderFactory.setDefaultValidationStringency( ValidationStringency.SILENT );
                    SamReaderFactory factory = SamReaderFactory.make();
                    factory.enable( SamReaderFactory.Option.DONT_MEMORY_MAP_INDEX );
                    factory.validationStringency( ValidationStringency.SILENT );
                    factory.referenceSource( reference_source );
                    factory.samRecordFactory( DefaultSAMRecordFactory.getInstance() );
                    SamInputResource ir = SamInputResource.of( file );
                    File indexMaybe = SamFiles.findIndex( file );
                    reporter.write( Severity.INFO, "proposed index: " + indexMaybe );

                    if (null != indexMaybe)
                        ir.index(indexMaybe);

                    SamReader reader = factory.open(ir);

                    for( SAMRecord record : reader )
                    {
                        read_no++;
                        //do not load supplementary reads
                        if( record.isSecondaryOrSupplementary() )
                            continue;

                        if( record.getDuplicateReadFlag() )
                            continue;

                        if( record.getReadString().equals( BAM_STAR ) && record.getBaseQualityString().equals( BAM_STAR ) )
                            continue;

                        if( record.getReadBases().length != record.getBaseQualities().length )
                        {
                            ValidationMessage<Origin> validationMessage = ValidationMessageReporter.createValidationMessage( Severity.ERROR, 
                                                                                                                             "Mismatch between length of read bases and qualities",
                                                                                                                             new DefaultOrigin( String.format( "%s:%d", rf.getFilename(), read_no ) ) );

                            reporter.write( validationMessage );
                            valid = false;
                        }

                        paired.compareAndSet( false, record.getReadPairedFlag() );
                        reads_cnt++;
                        if( 0 == reads_cnt % 1000 )
                            logProcessedReadNumber( reads_cnt );
                    }

                    logProcessedReadNumber( reads_cnt );

                    reader.close();

                    reporter.write( Severity.INFO, "Valid reads count: " + reads_cnt );
                    reporter.write( Severity.INFO, "LibraryLayout: " + ( paired.get() ? "PAIRED" : "SINGLE" ) );

                    if( 0 == reads_cnt )
                    {
                        reporter.write( Severity.ERROR, "File contains no valid reads" );
                        valid = false;
                    }

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
            
            
            FastqScanner fs = new FastqScanner( mdata.pairing_horizon );            
            ValidationResult vr = fs.checkFiles( files.toArray( new RawReadsFile[ files.size() ] ) );
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

    
    private boolean
    readCramFile( List<RawReadsFile> files, AtomicBoolean paired )
    {
        boolean valid = true;
        CramReferenceInfo cri = new CramReferenceInfo();
        for( RawReadsFile rf : files )
        {
            try (ValidationMessageReporter reporter = new ValidationMessageReporter( getReportFile( rf.getFilename() )))
            {
                try {
                    Map<String, Boolean> ref_set = cri.confirmFileReferences( new File( rf.getFilename() ) );
                    if( !ref_set.isEmpty() && ref_set.containsValue( Boolean.FALSE ) )
                    {
                        reporter.write(Severity.ERROR, "Unable to find reference sequence(s) from the CRAM reference registry: " +
                                ref_set.entrySet()
                                        .stream()
                                        .filter(e -> !e.getValue())
                                        .map(e -> e.getKey())
                                        .collect(Collectors.toList()));
                        valid = false;
                    }

                } catch( IOException ioe )
                {
                    reporter.write( Severity.ERROR, ioe.getMessage() );
                    valid = false;
                }
            }
        }

        return valid && readBamFile( files, paired );
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
                valid = readCramFile( files, paired );
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
