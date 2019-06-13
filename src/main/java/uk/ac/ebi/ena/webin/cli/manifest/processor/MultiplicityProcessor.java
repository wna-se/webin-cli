package uk.ac.ebi.ena.webin.cli.manifest.processor;

import uk.ac.ebi.embl.api.validation.ValidationResult;
import uk.ac.ebi.ena.webin.cli.manifest.ManifestFieldValue;

public class
MultiplicityProcessor extends AbstractManifestFieldProcessor<Void>
{
    final int min;
    final int max;
    
    public
    MultiplicityProcessor( int min, int max )
    {
        super( null );
        this.min = min;
        this.max = max;
    }
    
    
//    @Override public void 
//    validate( ValueInstanceGroup group )
//    {
//        if( min > 0 && group.getValues().isEmpty() )
//        {
//            error( WebinCliMessage.Manifest.MISSING_MANDATORY_FIELD_ERROR, new DefaultOrigin( String.valueOf( group.getSource() ) ), min );
//        } else if( min < group.getValues().size() )
//        {
//        
//            
//        }
//        
//
//        if( group.getValues().size() > max )
//            group.getValues().forEach( v -> error( WebinCliMessage.Manifest.TOO_MANY_FIELDS_ERROR, 
//                                                   v.getOrigin(), 
//                                                   group.getAlias(), 
//                                                   String.valueOf( max ) ) );
//    }
//
    
    @Override public Void 
    getCallbackPayload()
    {
        return null;
    }


    @Override
    public ValidationResult validate( ManifestFieldValue fieldValue )
    {
        // TODO Auto-generated method stub
        return null;
    }
}
