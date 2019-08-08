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
package uk.ac.ebi.ena.webin.cli;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;

import org.apache.commons.lang.StringUtils;

import uk.ac.ebi.ena.webin.cli.logger.ValidationMessageLogger;
import uk.ac.ebi.ena.webin.cli.manifest.ManifestReader;
import uk.ac.ebi.ena.webin.cli.reporter.ValidationMessageReporter;
import uk.ac.ebi.ena.webin.cli.submit.SubmissionBundle;
import uk.ac.ebi.ena.webin.cli.submit.SubmissionBundleHelper;
import uk.ac.ebi.ena.webin.cli.utils.FileUtils;

public abstract class 
AbstractWebinCli<M extends ManifestReader> implements WebinCliWrapper
{

    private String name; 
    private WebinCliParameters parameters = new WebinCliParameters();

    private boolean testMode;

    private M manifestReader;

    private File validationDir;
    private File processDir;
    private File submitDir;


    // MetadataService

    private final EnumSet<MetadataService> activeMetadataService = EnumSet.allOf(MetadataService.class);

    @Override
    public boolean isMetadataServiceActive(AbstractWebinCli.MetadataService metadataService) {
        return activeMetadataService.contains(metadataService);
    }

    @Override
    public void setMetadataServiceActive(AbstractWebinCli.MetadataService metadataService, boolean isActive) {
        if (isActive) {
            activeMetadataService.add(metadataService);
        }
        else {
            activeMetadataService.remove(metadataService);
        }
    }

    @Override
    public void setMetadataServiceActive(boolean isActive) {
        for (MetadataService metadataService: MetadataService.values()) {
            setMetadataServiceActive(metadataService, isActive);
        }
    }


    // TODO: remove
    private String description;

    // TODO: remove
    public String 
    getDescription()
    {
        return description;
    }

    // TODO: remove
    public void 
    setDescription( String description )
    {
        this.description = description;
    }

    public void setManifestReader(M manifestReader) {
        this.manifestReader = manifestReader;
    }

    protected abstract M createManifestReader();

    protected abstract void readManifest(Path inputDir, File manifestFile);

    @Override
    public final void
    readManifest( WebinCliParameters parameters )
    {
        this.parameters = parameters;
        this.manifestReader = createManifestReader();
        this.manifestReader.setValidateMandatory(parameters.isValidateManifestMandatory());
        this.manifestReader.setValidateFileExist(parameters.isValidateManifestFileExist());
        this.manifestReader.setValidateFileCount(parameters.isValidateManifestFileCount());

        this.validationDir = WebinCli.createOutputDir(parameters, ".");

        File manifestFile = getParameters().getManifestFile();
        File reportFile = getReportFile(manifestFile.getName() );

        reportFile.delete();

        try
        {
            readManifest( getParameters().getInputDir().toPath(), manifestFile );

            if (parameters.isCreateOutputDirs()) {
                if (!StringUtils.isBlank(manifestReader.getName())) {
                    setName();
                    this.validationDir = WebinCli.createOutputDir( parameters, String.valueOf( getContext() ), getName(), WebinCliConfig.VALIDATE_DIR );
                    this.processDir = WebinCli.createOutputDir( parameters, String.valueOf( getContext() ), getName(), WebinCliConfig.PROCESS_DIR );
                    this.submitDir = WebinCli.createOutputDir( parameters, String.valueOf( getContext() ), getName(), WebinCliConfig.SUBMIT_DIR );
                } else {
                    throw WebinCliException.systemError(WebinCliMessage.Cli.INIT_ERROR.format("Missing submission name."));
                }
            }
        } catch (WebinCliException ex) {
            throw ex;
        } catch (Exception ex) {
            throw WebinCliException.systemError(ex, WebinCliMessage.Cli.INIT_ERROR.format(ex.getMessage()));
        } finally {
            setName();
            if (manifestReader != null && !manifestReader.getValidationResult().isValid()) {
                ValidationMessageLogger.log(manifestReader.getValidationResult());
                try (ValidationMessageReporter reporter = new ValidationMessageReporter(reportFile)) {
                    reporter.write(manifestReader.getValidationResult());
                }
            }
        }

        if (manifestReader == null || !manifestReader.getValidationResult().isValid()) {
            throw WebinCliException.userError( WebinCliMessage.Manifest.INVALID_MANIFEST_FILE_ERROR.format(reportFile.getPath()) );
        }
    }

    private void setName() {
        if (manifestReader.getName() != null) {
            this.name = manifestReader.getName().trim().replaceAll("\\s+", "_");
        }
    }

    public M getManifestReader() {
        return manifestReader;
    }

    public File getValidationDir() {
        return validationDir;
    }

    public void setValidationDir(File validationDir) {
        this.validationDir = validationDir;
    }

    public File getProcessDir() {
        return processDir;
    }

    public void setProcessDir(File processDir) {
        this.processDir = processDir;
    }


    public File getSubmitDir() {
        return submitDir;
    }

    public void setSubmitDir(File submitDir) {
        this.submitDir = submitDir;
    }

    public String getAlias () {
        String alias = "webin-" + getContext().name() + "-" + getName();
        return alias;
    }

    private String
    getSubmissionBundleFileName()
    {
        return new File( getSubmitDir(), WebinCliConfig.SUBMISSION_BUNDLE_FILE_SUFFIX).getPath();
    }






    protected File
    getReportFile( String filename )
    {
        return WebinCli.getReportFile( getValidationDir(), filename, WebinCliConfig.REPORT_FILE_SUFFIX );
    }

    @Override
    public SubmissionBundle
    getSubmissionBundle()
    {
        SubmissionBundleHelper submissionBundleHelper = new SubmissionBundleHelper( getSubmissionBundleFileName() );
        try
        {
            return submissionBundleHelper.read( FileUtils.calculateDigest( "MD5", getParameters().getManifestFile() ) ) ;
        } catch( NoSuchAlgorithmException | IOException e )
        {
            return submissionBundleHelper.read();
        }
    }

    protected void
    setSubmissionBundle( SubmissionBundle submissionBundle )
    {
        new SubmissionBundleHelper( getSubmissionBundleFileName() ).write( submissionBundle );
    }


    public WebinCliParameters
    getParameters()
    {
        return this.parameters;
    }


    public String
    getName()
    {
        return name;
    }


    public void
    setName( String name )
    {
        this.name = name;
    }
    
    
    public boolean 
    getTestMode()
    {
        return this.testMode;
    }

    
    public void
    setTestMode( boolean test_mode )
    {
        this.testMode = test_mode;
    }

    
    public Path 
    getUploadRoot()
    {
    	return Paths.get( getTestMode() ? "webin-cli-test" : "webin-cli" );
    }
    
}
