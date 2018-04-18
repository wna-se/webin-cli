package uk.ac.ebi.ena.submit;

import java.util.Arrays;

import org.apache.commons.lang3.StringUtils;

import uk.ac.ebi.ena.manifest.FileFormat;

public enum ContextE {
    sequence("Sequence assembly: _NAME_", "SEQUENCE_FLATFILE", new FileFormat[] {FileFormat.TSV, FileFormat.FLATFILE} ),
    transcriptome("Transcriptome assembly: _NAME_", "TRANSCRIPTOME_ASSEMBLY", new FileFormat[] {FileFormat.FASTA,FileFormat.FLATFILE,FileFormat.INFO} ),
    genome("Genome assembly: _NAME_", "SEQUENCE_ASSEMBLY",new FileFormat[] {FileFormat.FASTA, FileFormat.AGP, FileFormat.FLATFILE,FileFormat.CHROMOSOME_LIST,FileFormat.UNLOCALISED_LIST,FileFormat.INFO});

    private String analysisTitle;
    private String analysisType;
    private FileFormat[] fileFormats;
    
    private ContextE(String analysisTitle, String analysisType,FileFormat[] fileFormats) {
        this.analysisTitle = analysisTitle;
        this.analysisType = analysisType;
        this.fileFormats =fileFormats;
    }

    public String getAnalysisTitle(String name) {
        return analysisTitle.replace("_NAME_", name);
    }

    public String getAnalysisType() {
        return analysisType;
    }
    
    public FileFormat[] getFileFormats()
    {
    	return fileFormats;
    }
    
    public String getFileFormatString() {
    	StringBuilder fileFormats = new StringBuilder();
    	for(FileFormat fileFormat:Arrays.asList(this.fileFormats))
    		fileFormats.append(fileFormat.name()+",");
    	return StringUtils.stripEnd(fileFormats.toString(),",");
    }

    public static  ContextE getContext(String context) {
    	try {
    	  return ContextE.valueOf(context);
    	}catch(Exception e)	{
    		return null;
    	}
    }
}
