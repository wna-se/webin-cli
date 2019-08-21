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
package uk.ac.ebi.ena.webin.cli.assembly;

import uk.ac.ebi.embl.api.validation.submission.SubmissionValidator;
import uk.ac.ebi.ena.webin.cli.*;
import uk.ac.ebi.ena.webin.cli.manifest.ManifestReader;
import uk.ac.ebi.ena.webin.cli.validator.api.ValidationResponse;
import uk.ac.ebi.ena.webin.cli.validator.file.SubmissionFile;
import uk.ac.ebi.ena.webin.cli.validator.manifest.Manifest;
import uk.ac.ebi.ena.webin.cli.xml.XmlCreator;

import java.nio.file.Paths;
import java.util.List;

public abstract class
SequenceWebinCli<M extends Manifest> extends AbstractWebinCli<M>
{
    private static final String ERROR_FILE = "webin-cli.report";

    public SequenceWebinCli(WebinCliContext context, WebinCliParameters parameters, ManifestReader<M> manifestReader, XmlCreator<M> xmlCreator) {
        super(context, parameters, manifestReader, xmlCreator);
    }

    @Override protected void validateSubmissionForContext()
    {
        M manifest = getManifestReader().getManifest();

        if(!manifest.getFiles().get().isEmpty()) {
            for (SubmissionFile subFile : (List<SubmissionFile>) manifest.getFiles().get()) {
                subFile.setReportFile(Paths.get(getValidationDir().getPath()).resolve(subFile.getFile().getName() + ".report").toFile());
            }
        }
        manifest.setReportFile(Paths.get(getValidationDir().getPath()).resolve(ERROR_FILE).toFile());
        manifest.setProcessDir(getProcessDir());
        ValidationResponse response;
        try {
            response = new SubmissionValidator().validate(manifest);
        } catch (RuntimeException ex) {
            throw WebinCliException.systemError(ex);
        }
        if(response != null && response.getStatus() == ValidationResponse.status.VALIDATION_ERROR) {
            throw WebinCliException.validationError("");
        }
    }
}
