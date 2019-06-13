package uk.ac.ebi.ena.webin.cli;

import java.nio.file.Path;
import java.util.List;

import uk.ac.ebi.embl.api.validation.ValidationResult;
import uk.ac.ebi.ena.webin.cli.manifest.ManifestFieldDefinition;
import uk.ac.ebi.ena.webin.cli.manifest.ManifestFileGroup;
import uk.ac.ebi.ena.webin.cli.manifest.ManifestReaderResult;

public interface 
WebinCliValidator
{
    public static class
    WorkDirLayout
    {
        public Path input;
        public Path process;
        public Path validate;
        public Path submit;
    }
    
    
    public static class 
    ManifestDefinition
    {
        final public List<ManifestFieldDefinition> fields;
        final public List<ManifestFileGroup> fileGroups;
        
        public
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