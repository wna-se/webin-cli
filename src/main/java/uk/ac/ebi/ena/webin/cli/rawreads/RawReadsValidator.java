package uk.ac.ebi.ena.webin.cli.rawreads;

import java.nio.file.Path;
import java.util.List;

import uk.ac.ebi.embl.api.validation.ValidationResult;
import uk.ac.ebi.ena.webin.cli.WebinCliParameters;
import uk.ac.ebi.ena.webin.cli.manifest.ManifestFieldDefinition;
import uk.ac.ebi.ena.webin.cli.manifest.ManifestFileGroup;
import uk.ac.ebi.ena.webin.cli.manifest.ManifestReaderResult;
import uk.ac.ebi.ena.webin.cli.manifest.processor.SampleProcessor;
import uk.ac.ebi.ena.webin.cli.manifest.processor.StudyProcessor;


interface 
WebinCliValidator
{
    public static class
    WorkDirLayout
    {
        Path process;
        Path validate;
        Path submit;
    }
    
    
    public static class 
    ManifestDefinition
    {
        final List<ManifestFieldDefinition> fields;
        final List<ManifestFileGroup> fileGroups;
        
        ManifestDefinition( List<ManifestFieldDefinition> fields, 
                            List<ManifestFileGroup>       fileGroups )
        {
            this.fields = fields;
            this.fileGroups = fileGroups;
        }
    }


    public ManifestDefinition getManifestDefinition();
    public void init( ManifestReaderResult param_list, WorkDirLayout layout );
    public ValidationResult getValidationResult();
    public void validate();
}


public class 
RawReadsValidator extends RawReadsWebinCli implements WebinCliValidator
{
    private SampleProcessor sampleProcessor;
    private StudyProcessor  studyProcessor;
    private WebinCliParameters parameters;
    
    
    public
    RawReadsValidator( WebinCliParameters parameters )
    {
        this.parameters = parameters;
        this.sampleProcessor = new SampleProcessor( parameters, null );
        this.studyProcessor  = new StudyProcessor( parameters, null );
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
        rrm.processManifest( reader_result );
        rrm.getName();
        
        setStudyId( getManifestReader().getStudyId() );
        setSampleId( getManifestReader().getSampleId() );
        setDescription( getManifestReader().getDescription() );

    
    }

    
    
    
}
