package uk.ac.ebi.ena.webin.cli.validation;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import uk.ac.ebi.ena.webin.cli.manifest.ManifestFieldValue;

public class
ValidationBundle
{
    private Path inputDirPath;
    private Path processDirPath;
    private Path reportDirPath;
    
    
    public Path
    getInputDir()
    {
        return this.inputDirPath;
    }
    
    
    public Path
    getProcessDir()
    {
        return this.processDirPath;
    }

    
    public Path
    getReportDir()
    {
        return this.reportDirPath;
    }

    
    public List<ManifestFieldValue> 
    getManifestFileds() 
    {
        List<ManifestFieldValue> result = new ArrayList<>();
        
        return result;
    }
}
