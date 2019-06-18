package uk.ac.ebi.ena.webin.cli.manifest.processor;

import uk.ac.ebi.embl.api.validation.Origin;
import uk.ac.ebi.embl.api.validation.Severity;
import uk.ac.ebi.embl.api.validation.ValidationMessage;
import uk.ac.ebi.embl.api.validation.ValidationResult;
import uk.ac.ebi.ena.webin.cli.WebinCliMessage;
import uk.ac.ebi.ena.webin.cli.manifest.ManifestFieldProcessor;
import uk.ac.ebi.ena.webin.cli.manifest.ManifestFieldValue;

public class
PositiveIntegerProcessor extends AbstractManifestFieldProcessor<Integer> implements ManifestFieldProcessor 
{
    private Integer value;
    
    
    public 
    PositiveIntegerProcessor( Callback<Integer> callback )
    {
        super( callback );
    }

    
    @Override public ValidationResult 
    validate( ManifestFieldValue field )
    {
        ValidationResult result = new ValidationResult( field.getOrigin() );
        String fieldValue = field.getValue();
        try
        {
            value = Integer.valueOf( fieldValue );
            if( value <= 0 )
                result.append( new ValidationMessage<Origin>( Severity.ERROR, WebinCliMessage.Manifest.INVALID_POSITIVE_INTEGER_ERROR.key(), field.getName(), fieldValue ) );
        } catch( NumberFormatException nfe )
        {
            result.append( new ValidationMessage<Origin>( Severity.ERROR, WebinCliMessage.Manifest.INVALID_POSITIVE_INTEGER_ERROR.key(), field.getName(), fieldValue ) );
        
//        } catch( NullPointerException npe )
//        {
//            result.append( new ValidationMessage<Origin>( Severity.ERROR, WebinCliMessage.Manifest.MISSING_MANDATORY_FIELD_ERROR.key(), field.getName() ) );
        }
        return result;
    }

    
    @Override public Integer 
    getCallbackPayload()
    {
    
        return value;
    }
}
