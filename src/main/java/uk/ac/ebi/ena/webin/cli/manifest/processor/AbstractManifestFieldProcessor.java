package uk.ac.ebi.ena.webin.cli.manifest.processor;

import uk.ac.ebi.embl.api.validation.ValidationResult;
import uk.ac.ebi.ena.webin.cli.manifest.ManifestFieldProcessor;
import uk.ac.ebi.ena.webin.cli.manifest.ManifestFieldValue;

abstract public class
AbstractManifestFieldProcessor<T> 
{
    private final ManifestFieldProcessor.Callback<T> callback;
    private ValidationResult result;
    
    abstract public ValidationResult validate( ManifestFieldValue fieldValue );
    abstract public T getCallbackPayload();
    
    
    public
    AbstractManifestFieldProcessor( ManifestFieldProcessor.Callback<T> callback )
    {
        this.callback = callback;
    }
    
    
    public ValidationResult
    getValidationResult()
    {
        return result;
    }
    
    
    final public ValidationResult
    process( ManifestFieldValue fieldValue )
    {
        result = validate( fieldValue );
        if( result.isValid() && null != callback )
            callback.notify( getCallbackPayload() );
        
        return result;
    }
}