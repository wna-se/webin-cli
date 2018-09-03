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

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.jdom2.Element;

import uk.ac.ebi.embl.api.entry.AgpRow;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.entry.Text;
import uk.ac.ebi.embl.api.entry.XRef;
import uk.ac.ebi.embl.api.entry.feature.FeatureFactory;
import uk.ac.ebi.embl.api.entry.feature.SourceFeature;
import uk.ac.ebi.embl.api.entry.genomeassembly.AssemblyInfoEntry;
import uk.ac.ebi.embl.api.entry.genomeassembly.ChromosomeEntry;
import uk.ac.ebi.embl.api.entry.genomeassembly.UnlocalisedEntry;
import uk.ac.ebi.embl.api.entry.location.Location;
import uk.ac.ebi.embl.api.entry.location.LocationFactory;
import uk.ac.ebi.embl.api.entry.location.Order;
import uk.ac.ebi.embl.api.entry.qualifier.Qualifier;
import uk.ac.ebi.embl.api.entry.qualifier.QualifierFactory;
import uk.ac.ebi.embl.api.validation.FileType;
import uk.ac.ebi.embl.api.validation.SequenceEntryUtils;
import uk.ac.ebi.embl.api.validation.Severity;
import uk.ac.ebi.embl.api.validation.ValidationEngineException;
import uk.ac.ebi.embl.api.validation.ValidationMessage;
import uk.ac.ebi.embl.api.validation.ValidationMessageManager;
import uk.ac.ebi.embl.api.validation.ValidationPlanResult;
import uk.ac.ebi.embl.api.validation.ValidationResult;
import uk.ac.ebi.embl.api.validation.ValidationScope;
import uk.ac.ebi.embl.api.validation.helper.taxon.TaxonHelper;
import uk.ac.ebi.embl.api.validation.helper.taxon.TaxonHelperImpl;
import uk.ac.ebi.embl.api.validation.plan.EmblEntryValidationPlan;
import uk.ac.ebi.embl.api.validation.plan.EmblEntryValidationPlanProperty;
import uk.ac.ebi.embl.api.validation.plan.GenomeAssemblyValidationPlan;
import uk.ac.ebi.embl.api.validation.plan.ValidationPlan;
import uk.ac.ebi.embl.flatfile.reader.FlatFileReader;
import uk.ac.ebi.embl.flatfile.reader.genomeassembly.ChromosomeListFileReader;
import uk.ac.ebi.embl.flatfile.reader.genomeassembly.UnlocalisedListFileReader;
import uk.ac.ebi.ena.submit.ContextE;
import uk.ac.ebi.ena.submit.SubmissionBundle;
import uk.ac.ebi.ena.utils.FileUtils;

public class 
GenomeAssemblyWebinCli extends SequenceWebinCli<GenomeManifest>
{
	private List<String> chromosomeEntryNames = new ArrayList<>();
	private List<ChromosomeEntry> chromosomeEntries = null;
	private HashMap<String,Long> fastaEntryNames = new HashMap<>();
	private HashMap<String,Long> flatfileEntryNames = new HashMap<>();
	private HashSet<String> agpEntrynames = new HashSet<>();
    private HashMap<String,AgpRow> contigRangeMap= new HashMap<>();
	private HashMap<String, List<Qualifier>> chromosomeQualifierMap = new HashMap<>();
	private String sequencelessChromosomesCheck= "ChromosomeListSequenelessCheck";
	private String molType = "genomic DNA";
    private boolean valid;
    private int i;

    @Override
    public ContextE getContext() {
        return ContextE.genome;
    }

    @Override
    protected GenomeManifest createManifestReader() {
        return new GenomeManifest();
    }

    @Override
    public void readManifest(Path inputDir, File manifestFile) {
        getManifestReader().readManifest(inputDir, manifestFile);

        // Set study, sample and file fields

        if (isFetchStudy() && getManifestReader().getStudyId() != null)
            setStudy( fetchStudy( getManifestReader().getStudyId(), getTestMode() ) );
        if (isFetchSample() && getManifestReader().getSampleId() != null)
          setSample( fetchSample( getManifestReader().getSampleId(), getTestMode() ) );

        if (getManifestReader().getAgpFiles() != null)
            this.agpFiles.addAll(getManifestReader().getAgpFiles());
        if (getManifestReader().getFastaFiles() != null)
            this.fastaFiles.addAll(getManifestReader().getFastaFiles());
        if (getManifestReader().getFlatFiles() != null)
            this.flatFiles.addAll(getManifestReader().getFlatFiles());

        this.chromosomeListFile = getManifestReader().getChromosomeListFile();
        this.unlocalisedListFile = getManifestReader().getUnlocalisedListFile();

        // Set assembly info

        AssemblyInfoEntry assemblyInfo = new AssemblyInfoEntry();
        assemblyInfo.setName(getManifestReader().getName());
        if (getStudy() != null)
            assemblyInfo.setStudyId(getStudy().getProjectId());
        if (getSample() != null)
        assemblyInfo.setSampleId(getSample().getBiosampleId());
        assemblyInfo.setPlatform(getManifestReader().getPlatform());
        assemblyInfo.setProgram(getManifestReader().getProgram());
        assemblyInfo.setMoleculeType(getManifestReader().getMoleculeType());
        assemblyInfo.setAssemblyType(getManifestReader().getAssemblyType());
        assemblyInfo.setCoverage(getManifestReader().getCoverage());
        assemblyInfo.setMinGapLength(getManifestReader().getMinGapLength());
        assemblyInfo.setTpa(getManifestReader().getTpa());
        this.setAssemblyInfo(assemblyInfo);
    }

    @Override public boolean 
    validateInternal() throws ValidationEngineException 
    {
		boolean valid = true;
		i=1;
		try 
		{
            EmblEntryValidationPlanProperty property = getValidationProperties();
            
			TaxonHelper taxonHelper = new TaxonHelperImpl();

			valid = valid && validateChromosomeList( property, chromosomeListFile );
			valid = valid && validateUnlocalisedList( property );
			getChromosomeEntryNames( taxonHelper.isChildOf( getSample().getOrganism(), "Virus" ) );
		    valid = valid && readAGPfiles();
			valid = valid && validateFastaFiles( property, fastaFiles );
			valid = valid && validateFlatFiles( property );
			
			HashMap<String,Long> entryNames= new HashMap<String,Long>();
			entryNames.putAll( fastaEntryNames );
			entryNames.putAll( flatfileEntryNames );
    		property.isFixCds.set(true);
			property.contigEntryNames.set( entryNames );
			
			
			valid = valid && validateAgpFiles( property );
		    valid = valid && validateSequenceLessChromosomes( chromosomeListFile );

			if( !getTestMode() )
				deleteFixedFiles( valid );
			
			this.valid = valid; 
			    
			return valid;
		} catch (IOException e) 
		{
			throw new ValidationEngineException(e.getMessage());
		}
	}


    @Override public SubmissionBundle
    getSubmissionBundle()
    {
        if( valid )
            prepareSubmissionBundle();
        return super.getSubmissionBundle();
    }
    
    
    EmblEntryValidationPlanProperty 
    getValidationProperties() 
    {
        EmblEntryValidationPlanProperty property = new EmblEntryValidationPlanProperty();
        
        property.isFixMode.set( true );
        property.isRemote.set( true );
        property.locus_tag_prefixes.set( getStudy().getLocusTagsList() );
        return property;
    }
	   
	
	boolean 
    validateChromosomeList( EmblEntryValidationPlanProperty property, File chromosomeListFile ) throws IOException, ValidationEngineException 
    {
        if( chromosomeListFile == null )
            return true;
        
        ValidationResult parseResult = new ValidationResult();

        File f = getReportFile( FileFormat.CHROMOSOME_LIST, chromosomeListFile.getName() );
        
        chromosomeEntries = getChromosomeEntries( chromosomeListFile, parseResult );
        FileUtils.writeReport( f, parseResult, chromosomeListFile.getName() );
        
        if( null == chromosomeEntries || chromosomeEntries.isEmpty() )
        {
            FileUtils.writeReport( f, Severity.ERROR, "File " + chromosomeListFile.getPath() + " has no valid chromosome entries" );
            return false;
        }

        if( !parseResult.isValid() )
        {
            FileUtils.writeReport( f, parseResult, chromosomeListFile.getName() );
            return false;
        }
        
        boolean valid = true;
        if( chromosomeEntries != null )
        {
            property.fileType.set( FileType.CHROMOSOMELIST );
            
            for( ChromosomeEntry chromosomeEntry : chromosomeEntries )
            {
                ValidationPlanResult vpr = validateEntry( chromosomeEntry, property );
                valid &= vpr.isValid();
                FileUtils.writeReport( f, vpr.getMessages(), chromosomeListFile.getName() );
            }
        }
        return valid;
    }


	private boolean 
    validateUnlocalisedList( EmblEntryValidationPlanProperty property ) throws IOException, ValidationEngineException
    {
        if( unlocalisedListFile == null )
            return true;
        
        ValidationResult parseResult = new ValidationResult();
        List<UnlocalisedEntry> unlocalisedEntries = getUnlocalisedEntries( unlocalisedListFile, parseResult );
        File f = getReportFile( FileFormat.UNLOCALISED_LIST, unlocalisedListFile.getName() );
        boolean valid = parseResult.isValid();
        FileUtils.writeReport( f, parseResult, unlocalisedListFile.getName() );
        
        if( parseResult.isValid() )
        {
            if( unlocalisedEntries != null ) 
            {
                property.fileType.set( FileType.UNLOCALISEDLIST );
                for( UnlocalisedEntry unlocalisedEntry : unlocalisedEntries ) 
                {
                    ValidationPlanResult vpr = validateEntry( unlocalisedEntry, property );
                    valid &= vpr.isValid();
                    FileUtils.writeReport( f, vpr.getMessages(), unlocalisedListFile.getName() );
                }
            }
        }           
        
        return valid;
    }
	

	boolean 
    validateFastaFiles( EmblEntryValidationPlanProperty property, List<File> fastaFiles ) throws IOException, ValidationEngineException 
    {
        boolean valid = true;
        boolean valid_entries = false;
        
        for( File file : fastaFiles ) 
        {
            property.fileType.set( FileType.FASTA );
            File f = getReportFile( FileFormat.FASTA, file.getName() ); 
            FlatFileReader<?> reader = getFileReader( FileFormat.FASTA, file );
            ValidationResult parseResult = reader.read();
            valid &= parseResult.isValid();
            FileUtils.writeReport( f, parseResult, file.getName() );
            if( parseResult.isValid() )
            {
                while( reader.isEntry() )
                {
                    SourceFeature source = ( new FeatureFactory() ).createSourceFeature();
                    source.setScientificName( getSample().getOrganism() );
                    source.addQualifier( Qualifier.MOL_TYPE_QUALIFIER_NAME, molType );
                    Entry entry = (Entry) reader.getEntry();
                   	List<String> contigKeys=contigRangeMap.entrySet().stream().filter(e -> e.getKey().contains(entry.getSubmitterAccession().toUpperCase())).map(e -> e.getKey()).collect(Collectors.toList());
                    	for(String contigKey:contigKeys)
                    	{
                    		contigRangeMap.get(contigKey).setSequence(entry.getSequence().getSequenceByte(contigRangeMap.get(contigKey).getComponent_beg(),contigRangeMap.get(contigKey).getComponent_end()));
                    		
                    	}
                        if( getSample().getBiosampleId() != null )
                        entry.addXRef( new XRef( "BioSample", getSample().getBiosampleId() ) );
                    
                    if( getStudy().getProjectId() != null )
                        entry.addProjectAccession( new Text( getStudy().getProjectId() ) );
                    
                    if( entry.getSubmitterAccession() != null && chromosomeEntryNames.contains( entry.getSubmitterAccession().toUpperCase() ) ) 
                    {
                        List<Qualifier> chromosomeQualifiers = chromosomeQualifierMap.get( entry.getSubmitterAccession().toUpperCase() );
                        source.addQualifiers( chromosomeQualifiers );
                        property.validationScope.set( ValidationScope.ASSEMBLY_CHROMOSOME );
                        entry.setDataClass( Entry.STD_DATACLASS );
                    } else 
                    {
                        property.validationScope.set( ValidationScope.ASSEMBLY_CONTIG );
                        entry.setDataClass( Entry.WGS_DATACLASS );
                    }

                    Order<Location> featureLocation = new Order<Location>();
                    featureLocation.addLocation( new LocationFactory().createLocalRange(1l, entry.getSequence().getLength() ) );
                    source.setLocations( featureLocation);
                    entry.addFeature( source);
                    entry.getSequence().setMoleculeType( molType );

					ValidationPlanResult vpr = getValidationPlan( entry, property ).execute( entry );
					valid &= vpr.isValid();
		            FileUtils.writeReport( f, vpr.getMessages(), file.getName() );
		            
					fastaEntryNames.put( entry.getSubmitterAccession().toUpperCase(), entry.getSequence().getLength() );
                    valid_entries = true;
                    parseResult = reader.read();
                }

                if( !valid_entries )
                {
                    valid &= false;
                    FileUtils.writeReport( f, Severity.ERROR, "File " + file.getName() + " does not contain valid FASTA entries" );
                }   
            }
        }
        return valid;
    }
	
	
	private boolean 
	readAGPfiles() throws IOException
	{
		boolean valid = true;
		
	    for( File file : agpFiles ) 
        {
           FlatFileReader<?> reader = getFileReader( FileFormat.AGP, file );
            
            ValidationResult vr = reader.read();
            int i=1;
            
            FileUtils.writeReport( getReportFile(  FileFormat.AGP, file.getName() ), vr );
            
            while( ( valid &= vr.isValid() ) && reader.isEntry() )
            {
                agpEntrynames.add( ( (Entry) reader.getEntry() ).getSubmitterAccession().toUpperCase() );
                for(AgpRow agpRow: ((Entry)reader.getEntry()).getSequence().getSortedAGPRows())
                {
                	i++;
                  	if(!agpRow.isGap())
                	{
                	   contigRangeMap.put(agpRow.getComponent_id().toUpperCase()+"_"+i,agpRow);
                	}
                }
                vr = reader.read();
            }
            
        }
	    return valid;
	}
      

    private boolean 
    validateFlatFiles( EmblEntryValidationPlanProperty property ) throws ValidationEngineException, IOException 
    {
        boolean valid = true;
        for( File file : flatFiles ) 
        {
            property.fileType.set( FileType.EMBL );
            File f = getReportFile( FileFormat.FLATFILE, file.getName() );
            
            FlatFileReader<?> reader = getFileReader( FileFormat.FLATFILE, file );
            ValidationResult parseResult = reader.read();
            valid &= parseResult.isValid();
            FileUtils.writeReport( f, parseResult, file.getName() );
            
            if( parseResult.isValid() )
            {
                while( reader.isEntry() ) 
                {
                    Entry entry = (Entry) reader.getEntry();
                    if(entry.getSubmitterAccession()!=null)
                    flatfileEntryNames.put(entry.getSubmitterAccession().toUpperCase(),entry.getSequence().getLength());
                    entry.removeFeature( entry.getPrimarySourceFeature() );
                    SourceFeature source = ( new FeatureFactory() ).createSourceFeature();
                    source.addQualifier( Qualifier.MOL_TYPE_QUALIFIER_NAME, molType );
                    source.setScientificName( getSample().getOrganism() );
                    
                    if( getSample().getBiosampleId() != null )
                        entry.addXRef( new XRef( "BioSample", getSample().getBiosampleId() ) );
                    
                    if( getStudy().getProjectId() != null )
                        entry.addProjectAccession( new Text( getStudy().getProjectId() ) );
                    
                    if( entry.getSubmitterAccession() != null && chromosomeEntryNames.contains( entry.getSubmitterAccession().toUpperCase() ) ) 
                    {
                        List<Qualifier> chromosomeQualifiers = chromosomeQualifierMap.get( entry.getSubmitterAccession().toUpperCase() );
                        source.addQualifiers( chromosomeQualifiers );
                        property.validationScope.set( ValidationScope.ASSEMBLY_CHROMOSOME );
                        if( entry.getSubmitterAccession() != null && agpEntrynames.contains( entry.getSubmitterAccession().toUpperCase() ) )
                        {
                            entry.setDataClass( Entry.CON_DATACLASS );
                        } else
                        {
                            entry.setDataClass( Entry.STD_DATACLASS );
                        }
                    } else if( entry.getSubmitterAccession() != null && agpEntrynames.contains( entry.getSubmitterAccession().toUpperCase() ) ) 
                    {
                        entry.setDataClass( Entry.CON_DATACLASS );
                        property.validationScope.set( ValidationScope.ASSEMBLY_SCAFFOLD );
                    } else 
                    {
                        property.validationScope.set( ValidationScope.ASSEMBLY_CONTIG );
                        entry.setDataClass( Entry.WGS_DATACLASS );
                    }
                    Order<Location> featureLocation = new Order<Location>();
                    featureLocation.addLocation( new LocationFactory().createLocalRange( 1l, entry.getSequence().getLength() ) );
                    source.setLocations( featureLocation );
                    entry.addFeature( source );
                    entry.getSequence().setMoleculeType( molType );
                	List<String> contigKeys=contigRangeMap.entrySet().stream().filter(e -> e.getKey().contains(entry.getSubmitterAccession().toUpperCase())).map(e -> e.getKey()).collect(Collectors.toList());
                	for(String contigKey:contigKeys)
                	{
                		contigRangeMap.get(contigKey).setSequence(entry.getSequence().getSequenceByte(contigRangeMap.get(contigKey).getComponent_beg(),contigRangeMap.get(contigKey).getComponent_end()));
                		
                	}
                    ValidationPlanResult validationPlanResult = getValidationPlan( entry, property ).execute( entry );
                    valid &= validationPlanResult.isValid();
                    FileUtils.writeReport( f, validationPlanResult.getMessages(), file.getName() );

                    parseResult = reader.read();
                    if( !parseResult.isValid() )
                    {
                        valid = false;
                        FileUtils.writeReport( f, parseResult, file.getName() );
                    }
                }
            }
        }
        return valid;
    }


    private boolean 
    validateAgpFiles( EmblEntryValidationPlanProperty property ) throws IOException, ValidationEngineException 
    {
        boolean valid = true;

        for( File file : agpFiles ) 
        {
            property.fileType.set( FileType.AGP );
            File f = getReportFile( FileFormat.AGP, file.getName() );
            
            FlatFileReader<?> reader = getFileReader( FileFormat.AGP, file );
            
            ValidationResult parseResult = reader.read();
            valid &= parseResult.isValid();
            if( !valid ) //TODO: discuss file format errors, AgpFileReader.isEntry
            {
                FileUtils.writeReport( f, parseResult, file.getName() );
            } else
            {
                while( reader.isEntry() ) 
                {
                    SourceFeature source = ( new FeatureFactory() ).createSourceFeature();
                    source.setScientificName( getSample().getOrganism() );
                    source.addQualifier( Qualifier.MOL_TYPE_QUALIFIER_NAME, molType );
                    Entry entry = (Entry) reader.getEntry();
                    constructAGPSequence(entry);
                    if( getSample().getBiosampleId() != null )
                        entry.addXRef( new XRef( "BioSample", getSample().getBiosampleId() ) );
                    
                    if( getStudy().getProjectId() != null )
                        entry.addProjectAccession( new Text( getStudy().getProjectId() ) );
                    
                    entry.setDataClass( Entry.CON_DATACLASS );
                    if( entry.getSubmitterAccession() != null && chromosomeEntryNames.contains( entry.getSubmitterAccession().toUpperCase() ) ) 
                    {
                        List<Qualifier> chromosomeQualifiers = chromosomeQualifierMap.get( entry.getSubmitterAccession().toUpperCase() );
                        source.addQualifiers( chromosomeQualifiers );
                        property.validationScope.set( ValidationScope.ASSEMBLY_CHROMOSOME );
                    } else
                    {
                        property.validationScope.set( ValidationScope.ASSEMBLY_SCAFFOLD );
                    }
                    
                    Order<Location> featureLocation = new Order<Location>();
                    featureLocation.addLocation( new LocationFactory().createLocalRange( 1l, entry.getSequence().getLength() ) );
                    source.setLocations( featureLocation );
                    entry.addFeature( source );
                    entry.getSequence().setMoleculeType( molType );
                    ValidationPlanResult vpr = getValidationPlan( entry, property ).execute( entry );
                    valid &= vpr.isValid();
                    FileUtils.writeReport( f, vpr.getMessages(), file.getName() );
                    parseResult = reader.read();
                }
            }
        }
        return valid;
    }

    
    private boolean 
    validateSequenceLessChromosomes( File chromosomeListFile )
    {
    	List<String> sequencelessChromosomes = new ArrayList<String>();
    	for( String chromosome : chromosomeEntryNames )
    	{
    		if( agpEntrynames.contains( chromosome ) )//IWGSC_CSS_6DL_scaff_3330716
    			continue;
    
    		if( fastaEntryNames.containsKey( chromosome ) )//IWGSC_CSS_6DL_SCAFF_3330716
    			continue;
            
    		if( flatfileEntryNames.containsKey( chromosome ) )
            	continue;
            else
            	sequencelessChromosomes.add( chromosome );
      	}
    	
    	if( !sequencelessChromosomes.isEmpty() )
    	{    			
    	    File f = getReportFile( FileFormat.CHROMOSOME_LIST, chromosomeListFile.getName() );
    	    ValidationResult result = new ValidationResult();
            result.append( new ValidationResult().append( new ValidationMessage<>( Severity.ERROR, sequencelessChromosomesCheck, sequencelessChromosomes.stream().collect( Collectors.joining( "," ) ) ) ) );
            FileUtils.writeReport( f, result.getMessages(), chromosomeListFile.getName() );
            return false;
    	}
    	return true;
    }
    
	public ValidationPlan getValidationPlan(Object entry, EmblEntryValidationPlanProperty property) 
	{
		ValidationPlan validationPlan = null;
		if (entry instanceof AssemblyInfoEntry || entry instanceof ChromosomeEntry || entry instanceof UnlocalisedEntry)
			validationPlan = new GenomeAssemblyValidationPlan(property);
		else
			validationPlan = new EmblEntryValidationPlan(property);
		validationPlan.addMessageBundle(ValidationMessageManager.STANDARD_VALIDATION_BUNDLE);
		validationPlan.addMessageBundle(ValidationMessageManager.STANDARD_FIXER_BUNDLE);
		validationPlan.addMessageBundle(ValidationMessageManager.GENOMEASSEMBLY_VALIDATION_BUNDLE);
		validationPlan.addMessageBundle(ValidationMessageManager.GENOMEASSEMBLY_FIXER_BUNDLE);
		return validationPlan;
	}

	private List<ChromosomeEntry> getChromosomeEntries(File chromosomeFile, ValidationResult parseResult)
			throws IOException 
	{
		if (chromosomeFile == null)
			return null;
		ChromosomeListFileReader reader = getFileReader(FileFormat.CHROMOSOME_LIST, chromosomeFile);
		parseResult.append(reader.read());
		if (reader.isEntry())
			return reader.getentries();
		return null;
	}

	private List<UnlocalisedEntry> getUnlocalisedEntries(File unlocalisedFile, ValidationResult parseResult)
			throws IOException 
	{
		if (unlocalisedFile == null)
			return null;
		UnlocalisedListFileReader reader = getFileReader(FileFormat.UNLOCALISED_LIST, unlocalisedFile);
		parseResult.append(reader.read());
		if (reader.isEntry())
			return reader.getentries();
		return null;
	}

	private ValidationPlanResult validateEntry(Object entry, EmblEntryValidationPlanProperty property)
			throws ValidationEngineException 
	{
		ValidationPlan validationPlan = getValidationPlan(entry, property);
		return validationPlan.execute(entry);
	}

	private List<String> getChromosomeEntryNames(boolean isVirus) 
	{
		if (chromosomeEntries == null)
			return chromosomeEntryNames;

		for (ChromosomeEntry chromosomeEntry : chromosomeEntries) 
		{
			chromosomeEntryNames.add(chromosomeEntry.getObjectName().toUpperCase());
			chromosomeQualifierMap.put(chromosomeEntry.getObjectName().toUpperCase(),
					getChromosomeQualifier(chromosomeEntry, isVirus));
		}

		return chromosomeEntryNames;
	}


	private void 
	deleteFixedFiles( boolean valid ) throws IOException
	{
        List<File> files = new ArrayList<>();
        files.addAll( fastaFiles );
        files.addAll( flatFiles );
        files.addAll( agpFiles );
        
	    for( File f : files )
	    {
            if( !valid ) 
            {
                File fixedFile = new File( f.getPath() + ".fixed" );
                if( fixedFile.exists() )
                    fixedFile.delete();
            }
	    }
	}


    Element 
    makeAnalysisType( AssemblyInfoEntry entry )
    {
        Element typeE = new Element( ContextE.genome.getType() );
        
        typeE.addContent( createTextElement( "NAME", entry.getName() ) );
        if( null != entry.getAssemblyType() && !entry.getAssemblyType().isEmpty() )
            typeE.addContent( createTextElement( "TYPE", entry.getAssemblyType()));
        typeE.addContent( createTextElement( "PARTIAL", String.valueOf( Boolean.FALSE ) ) ); //as per SraAnalysisParser.setAssemblyInfo
        typeE.addContent( createTextElement( "COVERAGE", entry.getCoverage() ) );
        typeE.addContent( createTextElement( "PROGRAM",  entry.getProgram() ) );
        typeE.addContent( createTextElement( "PLATFORM", entry.getPlatform() ) );
        
        if( null != entry.getMinGapLength() )
            typeE.addContent( createTextElement( "MIN_GAP_LENGTH", String.valueOf( entry.getMinGapLength() ) ) );
        
        if( null != entry.getMoleculeType() && !entry.getMoleculeType().isEmpty() )
            typeE.addContent( createTextElement( "MOL_TYPE", entry.getMoleculeType() ) );
        
        if ( entry.isTpa()) 
            typeE.addContent( createTextElement( "TPA", String.valueOf( entry.isTpa() ) ) );

        return typeE;
    }
    
    public static List<Qualifier> getChromosomeQualifier(ChromosomeEntry entry,boolean isVirus)
	{
		String chromosomeType = entry.getChromosomeType();
		String chromosomeLocation = entry.getChromosomeLocation();
		String chromosomeName = entry.getChromosomeName();
		List<Qualifier> chromosomeQualifiers = new ArrayList<Qualifier>();
		
		if (chromosomeLocation != null && !chromosomeLocation.isEmpty()&& !isVirus&&!chromosomeLocation.equalsIgnoreCase("Phage"))
		{
			String organelleValue =  SequenceEntryUtils.getOrganelleValue(chromosomeLocation);
			if (organelleValue != null)
			{									
				chromosomeQualifiers.add((new QualifierFactory()).createQualifier(Qualifier.ORGANELLE_QUALIFIER_NAME, SequenceEntryUtils.getOrganelleValue(chromosomeLocation)));
			}
		}	
		else if (chromosomeName != null && !chromosomeName.isEmpty())
		{
			if (Qualifier.PLASMID_QUALIFIER_NAME.equals(chromosomeType))
			{
				chromosomeQualifiers.add((new QualifierFactory()).createQualifier(Qualifier.PLASMID_QUALIFIER_NAME, chromosomeName));
			}
			else if (Qualifier.CHROMOSOME_QUALIFIER_NAME.equals(chromosomeType))
			{
				chromosomeQualifiers.add((new QualifierFactory()).createQualifier(Qualifier.CHROMOSOME_QUALIFIER_NAME, chromosomeName));
			}
			else if("segmented".equals(chromosomeType)||"multipartite".equals(chromosomeType))
			{
				chromosomeQualifiers.add((new QualifierFactory()).createQualifier(Qualifier.SEGMENT_QUALIFIER_NAME, chromosomeName));

			}
			else if("monopartite".equals(chromosomeType))
			{
				chromosomeQualifiers.add((new QualifierFactory()).createQualifier(Qualifier.NOTE_QUALIFIER_NAME, chromosomeType));
			}
		}
		return chromosomeQualifiers;
	}
    
    public void constructAGPSequence(Entry entry)
    {
 		ByteBuffer sequenceBuffer=ByteBuffer.wrap(new byte[new Long(entry.getSequence().getLength()).intValue()]);
 
         for(AgpRow agpRow: entry.getSequence().getSortedAGPRows())
         {
         	i++;
           	if(!agpRow.isGap())
         	{
           		if(contigRangeMap.get(agpRow.getComponent_id().toUpperCase()+"_"+i)==null||contigRangeMap.get(agpRow.getComponent_id().toUpperCase()+"_"+i).getSequence()==null)
           		{//error
           			
           		}
           		else
           		{
           			sequenceBuffer.put(contigRangeMap.get(agpRow.getComponent_id().toUpperCase()+"_"+i).getSequence());
           		}
         	}
           	else
           		sequenceBuffer.put(StringUtils.repeat("N".toLowerCase(), agpRow.getGap_length().intValue()).getBytes());           	
         }
         entry.getSequence().setSequence(sequenceBuffer);
         String v = new String(sequenceBuffer.array());
         System.out.println(v);

    }
}
