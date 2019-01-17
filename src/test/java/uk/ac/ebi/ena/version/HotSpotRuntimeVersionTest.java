package uk.ac.ebi.ena.version;

import org.junit.Assert;
import org.junit.Test;

import uk.ac.ebi.ena.version.HotSpotRuntimeVersion.VersionInfo;

public class 
HotSpotRuntimeVersionTest 
{
    @Test public void
    testVersionInfo()
    {
        //System.getProperties().list( System.out );
        
        Assert.assertTrue( new VersionInfo( 1, 8, 0, 155 ).compareTo( new VersionInfo( 1, 8, 0, 155 ) ) == 0 );
        Assert.assertTrue( new VersionInfo( 1, 8, 0, 155 ).compareTo( new VersionInfo( 1, 8, 0, 154 ) ) > 0 );
        Assert.assertTrue( new VersionInfo( 1, 8, 0, 154 ).compareTo( new VersionInfo( 1, 8, 0, 155 ) ) < 0 );

        Assert.assertTrue( new VersionInfo( 1, 8, 0, 0 ).compareTo( new VersionInfo( 1, 8, 0, 1 ) ) < 0 );
        
        Assert.assertTrue( new VersionInfo(  1, 8, 0, 0 ).compareTo( new VersionInfo( 11, 8, 0, 0 ) ) < 0 );
        Assert.assertTrue( new VersionInfo( 11, 8, 0, 0 ).compareTo( new VersionInfo(  1, 8, 0, 0 ) ) > 0 );
    }
    
    
    @Test public void
    test()
    {
        HotSpotRuntimeVersion jrv = new HotSpotRuntimeVersion();

        //1.8.0_172-b11
        Assert.assertEquals( Integer.valueOf( 1 ), jrv.getVersion( "1.8.0_172-b11" ).major );
        Assert.assertEquals( Integer.valueOf( 8 ), jrv.getVersion( "1.8.0_172-b11" ).minor );
        Assert.assertEquals( Integer.valueOf( 0 ), jrv.getVersion( "1.8.0_172-b11" ).security );
        Assert.assertEquals( Integer.valueOf( "172" ), jrv.getVersion( "1.8.0_172-b11" ).build );

        //11.0.1+13-LTS
        Assert.assertEquals( Integer.valueOf( 11 ), jrv.getVersion( "11.0.1+13-LTS" ).major );
        Assert.assertEquals( Integer.valueOf( 0 ), jrv.getVersion( "11.0.1+13-LTS" ).minor );
        Assert.assertEquals( Integer.valueOf( 1 ), jrv.getVersion( "11.0.1+13-LTS" ).security );
        Assert.assertEquals( Integer.valueOf( "13" ), jrv.getVersion( "11.0.1+13-LTS" ).build );
       
        Assert.assertEquals( null, jrv.getVersion( "something wrong" ) );
        Assert.assertEquals( null, jrv.getVersion( "" ) );
       
        if( jrv.isHotSpot() )
            Assert.assertTrue( jrv.isComplient() );
    }
}