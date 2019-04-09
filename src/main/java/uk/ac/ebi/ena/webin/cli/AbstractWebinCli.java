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
import java.security.NoSuchAlgorithmException;

import org.apache.commons.lang.StringUtils;

import uk.ac.ebi.ena.webin.cli.logger.ValidationMessageLogger;
import uk.ac.ebi.ena.webin.cli.manifest.ManifestReader;
import uk.ac.ebi.ena.webin.cli.manifest.ManifestSource;
import uk.ac.ebi.ena.webin.cli.reporter.ValidationMessageReporter;
import uk.ac.ebi.ena.webin.cli.submit.SubmissionBundle;
import uk.ac.ebi.ena.webin.cli.submit.SubmissionBundleHelper;
import uk.ac.ebi.ena.webin.cli.utils.FileUtils;

public abstract class 
AbstractWebinCli<T extends ManifestReader>
{
    private static final String VALIDATE_DIR = "validate";
    private static final String PROCESS_DIR = "process";
    private static final String SUBMIT_DIR   = "submit";
    private static final String REPORT_FILE_SUFFIX = ".report";
    private static final String SUBMISSION_BUNDLE = ".data";

    private String name; 
    private WebinCliParameters parameters = new WebinCliParameters();
    private ManifestSource manifestSource;

    private boolean testMode;

    private T manifestReader;

    private File validationDir;
    private File processDir;
    private File submitDir;

    private boolean fetchSample = true;
    private boolean fetchStudy = true;
 	private boolean fetchSource = true;

    protected abstract T createManifestReader();

    private String description;

    public String 
    getDescription()
    {
        return description;
    }

    
    public void 
    setDescription( String description )
    {
        this.description = description;
    }


    public final void
    init( WebinCliParameters parameters ) {
        init(parameters, new ManifestSource(parameters.getManifestFile()));
    }

    public final void
    init( WebinCliParameters parameters, ManifestSource manifestSource )
    {
        this.parameters = parameters;
        this.manifestSource = manifestSource;
        this.manifestReader = createManifestReader();

        this.validationDir = WebinCli.createOutputDir(parameters, ".");

        Path inputDir = getParameters().getInputDir().toPath();
        File manifestFile = getParameters().getManifestFile();
        File reportFile = getReportFile(manifestFile.getName() );

        reportFile.delete();

        try
        {
            readManifest( inputDir, manifestSource);

            if (!StringUtils.isBlank(manifestReader.getName())) {
                setName();
                this.validationDir = WebinCli.createOutputDir( parameters, String.valueOf( getContext() ), getName(), VALIDATE_DIR );
                this.processDir = WebinCli.createOutputDir( parameters, String.valueOf( getContext() ), getName(), PROCESS_DIR );
                this.submitDir = WebinCli.createOutputDir( parameters, String.valueOf( getContext() ), getName(), SUBMIT_DIR );
            } else {
                throw WebinCliException.systemError(WebinCliMessage.Cli.INIT_ERROR.format("Missing submission name."));
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

    public abstract WebinCliContext getContext();

    public abstract void readManifest(Path inputDir, ManifestSource manifestSource);

    public T getManifestReader() {
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

    public abstract void validate() throws WebinCliException;
    public abstract void prepareSubmissionBundle();

    private String
    getSubmissionBundleFileName()
    {
        return new File( getSubmitDir(), SUBMISSION_BUNDLE ).getPath();
    }

    public boolean
    isFetchStudy()
    {
        return fetchStudy;
    }


    public boolean
    isFetchSample()
    {
        return fetchSample;
    }


    public void
    setFetchSample(boolean fetchSample)
    {
        this.fetchSample = fetchSample;
    }


    public void
    setFetchStudy(boolean fetchStudy)
    {
        this.fetchStudy = fetchStudy;
    }

    protected File
    getReportFile( String filename )
    {
        return WebinCli.getReportFile( getValidationDir(), filename, REPORT_FILE_SUFFIX );
    }

    public SubmissionBundle
    getSubmissionBundle()
    {
        SubmissionBundleHelper submissionBundleHelper = new SubmissionBundleHelper( getSubmissionBundleFileName() );
        return submissionBundleHelper.read( getManifestSource().getManifestMd5() );
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

    public ManifestSource getManifestSource() {
        return manifestSource;
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

    public boolean isFetchSource() 
    {
 		return fetchSource;
 	}

 	public void setFetchSource(boolean fetchSource) 
 	{
 		this.fetchSource = fetchSource;
 	}
}
