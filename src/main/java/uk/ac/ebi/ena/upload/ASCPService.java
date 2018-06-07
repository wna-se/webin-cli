package uk.ac.ebi.ena.upload;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.util.List;

import uk.ac.ebi.ena.webin.cli.WebinCliException;

public class 
ASCPService implements UploadService
{
    private static final String EXECUTABLE = "ascp";
    private String userName;
    private String password;

    class 
    StreamConsumer extends Thread
    {
        private InputStream istream;
        private long read_cnt;
        
        StreamConsumer( InputStream istream )
        {
            this.istream = istream;
        }
        
        
        @Override public void
        run()
        {
            try
            {
                while( 0 != istream.read() ) 
                    ++read_cnt; 
                
            } catch( IOException e )
            {
                ;
            }
        }
        
        
        public long
        getReadCnt()
        {
            return read_cnt;
        }
    }
    
    
    @Override public boolean
    isAvaliable()
    {
        try
        {            
            Runtime rt = Runtime.getRuntime();
            Process proc = rt.exec( EXECUTABLE, 
                                    new String[] { String.format( "PATH=%s", System.getenv( "PATH" ) ) } );
            
            new StreamConsumer( proc.getInputStream() ).start();
            new StreamConsumer( proc.getErrorStream() ).start();
            
            proc.waitFor();
            
            int exitVal = proc.exitValue();
            
            if( 0 != exitVal )
                return false;
            
        } catch( Throwable t )
        {
            return false;
        }
        
        return true;
    }
    
    
    @Override public void
    connect( String userName, String password )
    {
        this.password = password;
        this.userName = userName;
        
    }

    
    private String
    createUploadList( List<File> uploadFilesList, Path inputDir )
    {
        StringBuilder sb = new StringBuilder();
        for( File f : uploadFilesList )
        {
            String from = f.isAbsolute() ? f.toString() : inputDir.resolve( f.toPath() ).normalize().toString();
            sb.append( String.format( "%s\n", from ) );
        }
        return sb.toString();
    }
    
       
    private String 
    getCommand( Path file_list, Path inputDir, String uploadDir )
    {
        return String.format( "%s --file-checksum=md5 -d --mode=send --overwrite=always --user=\"%s\" -QT -l300M -L- --src-base=%s --source-prefix=\"%s\" --file-list=\"%s\" --host=webin.ebi.ac.uk \"%s\"",
                              EXECUTABLE,
                              this.userName, 
                              inputDir.normalize().toString(),
                              inputDir.normalize().toString(),
                              file_list,
                              uploadDir );
    }
    
    
    @Override public void
    ftpDirectory( List<File> uploadFilesList,
                  String uploadDir,
                  Path inputDir )
    {
        try
        {            
            String file_list = createUploadList( uploadFilesList, inputDir );
            String command = getCommand( Files.write( Files.createTempFile( "FILE", "LIST", new FileAttribute<?>[] {} ), 
                                                      file_list.getBytes(), 
                                                      StandardOpenOption.CREATE ),
                                         inputDir.toAbsolutePath(),
                                         uploadDir );
            
            
System.out.println( "CMD: " + command );   

            Runtime rt = Runtime.getRuntime();
            Process proc = rt.exec( command, 
                                    new String[] { String.format( "ASPERA_SCP_PASS=%s", this.password ), 
                                                   String.format( "PATH=%s", System.getenv( "PATH" ) ) } );
            
            new StreamConsumer( proc.getInputStream() ).start();
            new StreamConsumer( proc.getErrorStream() ).start();
            
            proc.waitFor();
            
            int exitVal = proc.exitValue();
            
            if( 0 != exitVal )
                throw WebinCliException.createSystemError( "Unable to upload files using ASPERA" );
            
        } catch( Throwable t )
        {
            throw WebinCliException.createSystemError( "Unable to upload files using ASPERA " + t.getMessage() );
        }
    }

    
    @Override public void
    disconnect()
    {
        //do nothing
        this.password = null;
        this.userName = null;
    }
}