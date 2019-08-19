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
    private final WebinCliContext context;
    private final WebinCliParameters parameters;
    private final M manifestReader;

    private File validationDir;
    private File processDir;
    private File submitDir;


    public AbstractWebinCli(WebinCliContext context, WebinCliParameters parameters, M manifestReader) {
        this.context = context;
        this.parameters = parameters;
        this.manifestReader = manifestReader;
    }

    public WebinCliContext getContext() {
        return context;
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

    protected abstract void readManifest(Path inputDir, File manifestFile);

    @Override
    public final void
    readManifest()
    {
        this.validationDir = WebinCli.createOutputDir(parameters, ".");

        File manifestFile = getParameters().getManifestFile();
        File reportFile = getReportFile(manifestFile.getName() );

        reportFile.delete();

        try
        {
            readManifest( getParameters().getInputDir().toPath(), manifestFile );
            if (!StringUtils.isBlank(manifestReader.getName())) {
                this.validationDir = WebinCli.createOutputDir( parameters, String.valueOf( this.context ), getSubmissionName(), WebinCliConfig.VALIDATE_DIR );
                this.processDir = WebinCli.createOutputDir( parameters, String.valueOf( this.context ), getSubmissionName(), WebinCliConfig.PROCESS_DIR );
                this.submitDir = WebinCli.createOutputDir( parameters, String.valueOf( this.context ), getSubmissionName(), WebinCliConfig.SUBMIT_DIR );
            } else {
                throw WebinCliException.systemError(WebinCliMessage.Cli.INIT_ERROR.format("Missing submission name."));
            }
        } catch (WebinCliException ex) {
            throw ex;
        } catch (Exception ex) {
            throw WebinCliException.systemError(ex, WebinCliMessage.Cli.INIT_ERROR.format(ex.getMessage()));
        } finally {
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
        String alias = "webin-" + context.name() + "-" + getSubmissionName();
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

    /** Returns the submission name. It is the name from the manifest with multiple whitespaces replaced
     * by a single underscore.
     * @return
     */
    public String
    getSubmissionName()
    {
        String name = manifestReader.getName();
        if (name != null) {
            return name.trim().replaceAll("\\s+", "_");
        }
        return name;
    }

    public Path
    getUploadRoot()
    {
    	return Paths.get( this.parameters.isTestMode() ? "webin-cli-test" : "webin-cli" );
    }
}
