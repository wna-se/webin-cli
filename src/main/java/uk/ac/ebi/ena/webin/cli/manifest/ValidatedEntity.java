package uk.ac.ebi.ena.webin.cli.manifest;

import uk.ac.ebi.embl.api.validation.ValidationResult;

public interface 
ValidatedEntity 
{
    public ValidationResult getValidationResult();
    public boolean isValid();
}
