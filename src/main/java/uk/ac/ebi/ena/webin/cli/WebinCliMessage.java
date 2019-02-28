/*
 * Copyright 2019 EMBL - European Bioinformatics Institute
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package uk.ac.ebi.ena.webin.cli;

import uk.ac.ebi.embl.api.validation.Origin;
import uk.ac.ebi.embl.api.validation.Severity;
import uk.ac.ebi.embl.api.validation.ValidationMessage;

import java.lang.reflect.Field;
import java.text.MessageFormat;

public interface WebinCliMessage {

    // Messages.
    //

    enum Cli implements WebinCliMessage {
        VALIDATE_SUCCESS("The submission has been validated successfully."),
        UPLOAD_SUCCESS("Files have been uploaded to webin.ebi.ac.uk. "),
        SUBMIT_SUCCESS("The submission has been completed successfully. "),
        VALIDATE_USER_ERROR("Submission validation failed because of a user error. Please check the report directory for the errors: "),
        VALIDATE_SYSTEM_ERROR("Submission validation failed because of a system error. "),
        UPLOAD_ERROR("Failed to upload files to webin.ebi.ac.uk because of a {0}. "),
        SUBMIT_ERROR("The submission has failed because of a {0}. "),
        AUTHENTICATION_ERROR("Invalid submission account user name or password."),
        INVALID_CONTEXT_ERROR("Invalid context: {0}."),
        EMPTY_DIRECTORY_ERROR("Unable to empty directory {0}" );

        public final String text;
        Cli(String text) {
            this.text = text;
        }
    }

    enum Bundle implements WebinCliMessage {
        REVALIDATE_SUBMISSION("Submission requires re-validation."),
        VALIDATE_SUBMISSION("Submission has not been validated previously."),
        FILE_ERROR("Unable to create submission bundle file: {0}");

        public final String text;
        Bundle(String text) {
            this.text = text;
        }
    }

    enum Ftp implements WebinCliMessage {
       UPLOAD_DIR_ERROR("Missing upload directory. Failed to upload files to webin.ebi.ac.uk using FTP."),
       CONNECT_ERROR("Failed to connect to webin.ebi.ac.uk using FTP."),
       CREATE_DIR_ERROR("Failed to create directory \"{0}\" in webin.ebi.ac.uk using FTP."),
       CHANGE_DIR_ERROR("Failed to access directory \"{0}\" in webin.ebi.ac.uk using FTP."),
       UPLOAD_ERROR("Failed to upload \"{0}\" file to webin.ebi.ac.uk using FTP."),
       SERVER_ERROR("Failed to upload files to webin.ebi.ac.uk using FTP.");

        public final String text;
        Ftp(String text) {
            this.text = text;
        }
    }

    enum Aspera implements WebinCliMessage {
        UPLOAD_ERROR("Failed to upload files to webin.ebi.ac.uk using Aspera.");

        public final String text;
        Aspera(String text) {
            this.text = text;
        }
    }

    enum Service implements WebinCliMessage {
        SUBMISSION_SERVICE_SYSTEM_ERROR("A server error occurred when attempting to submit."),
        IGNORE_ERRORS_SERVICE_SYSTEM_ERROR("A server error occurred when retrieving ignore error information."),
        VERSION_SERVICE_SYSTEM_ERROR("A server error occurred when checking application version."),
        SAMPLE_SERVICE_VALIDATION_ERROR("Unknown sample {0} or the sample cannot be referenced by your submission account. Samples must be submitted before they can be referenced in the submission."),
        SAMPLE_SERVICE_SYSTEM_ERROR("A server error occurred when retrieving sample {0} information."),
        STUDY_SERVICE_VALIDATION_ERROR("Unknown study {0} or the study cannot be referenced by your submission account. Studies must be submitted before they can be referenced in the submission."),
        STUDY_SERVICE_SYSTEM_ERROR("A server error occurred when retrieving study {0} information.");

        public final String text;
        Service(String text) {
            this.text = text;
        }
    }

    enum Manifest implements WebinCliMessage {
        READING_MANIFEST_FILE_ERROR("Could not read manifest file: \"{0}\"."),
        READING_INFO_FILE_ERROR("Could not read info file: \"{0}\"."),
        INVALID_MANIFEST_FILE_ERROR("Invalid manifest file. Please see the error report file \"{0}\"." ),
        UNKNOWN_FIELD_ERROR("Unknown field: {0}."),
        INVALID_FIELD_VALUE_ERROR("Invalid {0} field value: \"{1}\". Valid values are: {2}."),
        INVALID_FILE_FIELD_ERROR("Invalid {0} file name. Could not read file: \"{1}\"."),
        INVALID_FILE_SUFFIX_ERROR("Invalid {0} file suffix: \"{1}\". Valid file suffixes are: {2}."),
        INVALID_FILE_NAME_ERROR("Invalid {0} file name: \"{1}\". File name should conform following regular expression: {2}."),
        INVALID_POSITIVE_INTEGER_ERROR("Invalid {0} field value: \"{1}\". Non-negative integer expected."),
        INVALID_POSITIVE_FLOAT_ERROR("Invalid {0} field value: \"{1}\". Non-negative float expected."),
        MISSING_MANDATORY_FIELD_ERROR("Missing mandatory field {0}."),
        TOO_MANY_FIELDS_ERROR("Field {0} should not appear more than {1} times."),
        NO_DATA_FILES_ERROR("No data files have been specified. Expected data files are: {0}."),
        INVALID_FILE_GROUP_ERROR("An invalid set of files has been specified{1}. Expected data files are: {0}."),
        INVALID_FILE_COMPRESSION_ERROR("Failed to uncompress file: \"{0}\". The file must be compressed with {1}."),
        FIELD_VALUE_CORRECTED("Field \"{0}\" value \"{1}\" was corrected to \"{2}\"."),
        STUDY_LOOKUP_ERROR("Failed to lookup study \"{0}\". {1}"),
        SAMPLE_LOOKUP_ERROR("Failed to lookup sample \"{0}\". {1}"),
        MISSING_PLATFORM_AND_INSTRUMENT_ERROR("Platform and/or instrument should be defined. Valid platforms: {0}. Valid instruments: {1}."),
        INVALID_PLATFORM_FOR_INSTRUMENT_ERROR("Platform {0} for instrument {1}. Valid platforms are: {2}."),
        MISSING_PLATFORM_FOR_INSTRUMENT_ERROR("Missing platform for instrument: {0}.");

        public final String text;
        Manifest(String text) {
            this.text = text;
        }
    }

    // Interface methods.
    //

    static ValidationMessage<Origin> error(WebinCliMessage message, Object ... arguments) {
        return WebinCliMessage.message(Severity.ERROR, message,null, arguments);
    }

    static ValidationMessage<Origin> error(WebinCliMessage message, Origin origin, Object ... arguments) {
        return WebinCliMessage.message(Severity.ERROR, message, origin, arguments);
    }

    static ValidationMessage<Origin> info(WebinCliMessage message, Object ... arguments) {
        return WebinCliMessage.message(Severity.INFO, message,null, arguments);
    }

    static ValidationMessage<Origin> info(WebinCliMessage message, Origin origin, Object ... arguments) {
        return WebinCliMessage.message(Severity.INFO, message, origin, arguments);
    }

    static ValidationMessage<Origin> message(Severity severity, WebinCliMessage message, Origin origin, Object ... arguments) {
        // Set key.
        ValidationMessage<Origin> validationMessage = new ValidationMessage<>(severity, message.key());
        // Override message.
        validationMessage.setMessage(message.format(arguments));
        // Set origin.
        if (origin != null) {
            validationMessage.append(origin);
        }
        return validationMessage;
    }

    default String key() {
        if (this instanceof Enum) {
            return ((Enum) this).name();
        }
        return null;
    }

    default String format(Object ... arguments) {
        String text;
        try {
            Field field = this.getClass().getField("text");
            text = (String) field.get(this);
        }
        catch (NoSuchFieldException | IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
        try {
            return MessageFormat.format(text, arguments);
        } catch (RuntimeException ex) {
            return text;
        }
    }
}