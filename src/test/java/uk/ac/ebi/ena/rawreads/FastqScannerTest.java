package uk.ac.ebi.ena.rawreads;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import org.junit.Assert;
import org.junit.Test;

import uk.ac.ebi.embl.api.validation.Origin;
import uk.ac.ebi.embl.api.validation.Severity;
import uk.ac.ebi.embl.api.validation.ValidationMessage;
import uk.ac.ebi.embl.api.validation.ValidationResult;
import uk.ac.ebi.ena.assembly.GenomeAssemblyWebinCliTest;
import uk.ac.ebi.ena.frankenstein.loader.common.feeder.DataFeederException;


public class 
FastqScannerTest 
{
    static final int expected_reads = 10_000;
    
    
    @Test public void 
    testSingle() throws SecurityException, NoSuchMethodException, DataFeederException, IOException, InterruptedException
    {
        URL  url1 = GenomeAssemblyWebinCliTest.class.getClassLoader().getResource( "uk/ac/ebi/ena/rawreads/EP0_GTTCCTT_S1.txt.gz" );
        FastqScanner fs = new FastqScanner( expected_reads );
        RawReadsFile rf = new RawReadsFile();
        
        rf.setFilename( new File( url1.getFile() ).getCanonicalPath() );
        
        ValidationResult vr = fs.checkFiles( rf );
        
        Assert.assertTrue( vr.getMessages( Severity.ERROR ).isEmpty() );
    }
    
    
    @Test public void 
    testSingleDuplications() throws SecurityException, NoSuchMethodException, DataFeederException, IOException, InterruptedException
    {
        URL  url1 = GenomeAssemblyWebinCliTest.class.getClassLoader().getResource( "uk/ac/ebi/ena/rawreads/EP0_GTTCCTT_S1.txt.dup.gz" );
        FastqScanner fs = new FastqScanner( expected_reads );
        RawReadsFile rf = new RawReadsFile();
        
        rf.setFilename( new File( url1.getFile() ).getCanonicalPath() );
        
        ValidationResult vr = fs.checkFiles( rf );
        
        Assert.assertEquals( vr.toString(), 2, vr.getMessages( Severity.ERROR ).size() );
    }


    @Test public void 
    testPaired() throws SecurityException, NoSuchMethodException, DataFeederException, IOException, InterruptedException
    {
        URL  url1 = GenomeAssemblyWebinCliTest.class.getClassLoader().getResource( "uk/ac/ebi/ena/rawreads/EP0_GTTCCTT_0.TXT.GZ" );
        FastqScanner fs = new FastqScanner( expected_reads );
        RawReadsFile rf = new RawReadsFile();
        
        rf.setFilename( new File( url1.getFile() ).getCanonicalPath() );
        
        ValidationResult vr = fs.checkFiles( rf );
        
        Assert.assertEquals( vr.toString(), 0, vr.getMessages( Severity.ERROR ).size() );
    }
    
    
    @Test public void 
    testPair() throws SecurityException, NoSuchMethodException, DataFeederException, IOException, InterruptedException
    {
        URL  url1 = GenomeAssemblyWebinCliTest.class.getClassLoader().getResource( "uk/ac/ebi/ena/rawreads/EP0_GTTCCTT_S1.txt.gz" );
        URL  url2 = GenomeAssemblyWebinCliTest.class.getClassLoader().getResource( "uk/ac/ebi/ena/rawreads/EP0_GTTCCTT_S2.txt.gz" );
        FastqScanner fs = new FastqScanner( expected_reads );
        RawReadsFile rf1 = new RawReadsFile();
        rf1.setFilename( new File( url1.getFile() ).getCanonicalPath() );
        
        RawReadsFile rf2 = new RawReadsFile();
        rf2.setFilename( new File( url2.getFile() ).getCanonicalPath() );

        ValidationResult vr = fs.checkFiles( rf1, rf2 );
        
        Assert.assertEquals( toString( vr ), 0, vr.getMessages( Severity.ERROR ).size() );
    }
    
    
    
    public Path
    saveRandomized( String content, Path folder, boolean gzip, String...suffix ) throws IOException
    {
        Path file = Files.createTempFile( "_content_", "_content_" );
        Files.write( file, content.getBytes( StandardCharsets.UTF_8 ), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC );
        Path path = Files.createTempFile( folder, "COPY", (String) ( file.getName( file.getNameCount() - 1 ) + ( suffix.length > 0 ? Stream.of( suffix ).collect( Collectors.joining( ".", ".", "" ) ) : "" ) ) );
        OutputStream os;
        Files.copy( file, ( os = gzip ? new GZIPOutputStream( Files.newOutputStream( path, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC ) ) 
                                      : Files.newOutputStream( path, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC ) ) );
        os.flush();
        os.close();
        Assert.assertTrue( Files.exists( path ) );
        Assert.assertTrue( Files.isRegularFile( path ) );
        return path;
    }
    

    private File
    createOutputFolder() throws IOException
    {
        File output = File.createTempFile( "test", "test" );
        Assert.assertTrue( output.delete() );
        Assert.assertTrue( output.mkdirs() );
        return output;
    }
    
    /* Test cases */
    /* 1. Single run with duplication */
    /* 2. Paired run with one file with duplication */
    /* 3. Paired run with two files with duplication in first file */
    /* 4. Paired run with two files with duplication from first file in second file */
    /* 5. Paired run with two files with duplication from second file in second file */
    @Test public void 
    testCase1() throws SecurityException, NoSuchMethodException, DataFeederException, IOException, InterruptedException
    {
        File output_dir = createOutputFolder();
        Path f1 = saveRandomized( "@NAME1/1\nACGT\n+\n1234\n"
                                + "@NAME1/1\nACGT\n+\n1234\n" 
                                + "@NAME2/1\nACGT\n+\n1234", output_dir.toPath(), true, "fastq-1", "gz" );
        FastqScanner fs = new FastqScanner( expected_reads );
        RawReadsFile rf1 = new RawReadsFile();
        rf1.setFilename( f1.toFile().getCanonicalPath() );
        
        ValidationResult vr = fs.checkFiles( rf1 );
        Assert.assertEquals( toString( vr.getMessages( Severity.ERROR ) ), 1, vr.getMessages( Severity.ERROR ).size() );
    }

    
    @Test public void 
    testCase2() throws SecurityException, NoSuchMethodException, DataFeederException, IOException, InterruptedException
    {
        File output_dir = createOutputFolder();
        Path f1 = saveRandomized( "@NAME1/1\nACGT\n+\n1234\n"
                                + "@NAME1/2\nACGT\n+\n1234\n" 
                                + "@NAME1/1\nACGT\n+\n1234", output_dir.toPath(), true, "fastq-1", "gz" );
        FastqScanner fs = new FastqScanner( expected_reads );
        RawReadsFile rf1 = new RawReadsFile();
        rf1.setFilename( f1.toFile().getCanonicalPath() );
        
        ValidationResult vr = fs.checkFiles( rf1 );
        Assert.assertEquals( toString( vr.getMessages( Severity.ERROR ) ), 1, vr.getMessages( Severity.ERROR ).size() );
    }
    
    
    @Test public void 
    testCase3() throws SecurityException, NoSuchMethodException, DataFeederException, IOException, InterruptedException
    {
        File output_dir = createOutputFolder();
        Path f1 = saveRandomized( "@NAME1\nACGT\n+\n1234\n"
                                + "@NAME1\nACGT\n+\n1234", output_dir.toPath(), true, "fastq-1", "gz" );
        Path f2 = saveRandomized( "@NAME2\nACGT\n+\n1234", output_dir.toPath(), true, "fastq-2", "gz" );
        FastqScanner fs = new FastqScanner( expected_reads );
        RawReadsFile rf1 = new RawReadsFile();
        rf1.setFilename( f1.toFile().getCanonicalPath() );
        
        RawReadsFile rf2 = new RawReadsFile();
        rf2.setFilename( f2.toFile().getCanonicalPath() );

        ValidationResult vr = fs.checkFiles( rf1, rf2 );
        
        Assert.assertEquals( toString( vr.getMessages( Severity.ERROR ) ), 1, vr.getMessages( Severity.ERROR ).size() );
    }
    
    /* 4. Paired run with two files with duplication from first file in second file */
    /* logically this is not true. The suffixes can be inherited from the file streams */
    @Test public void 
    testCase4() throws SecurityException, NoSuchMethodException, DataFeederException, IOException, InterruptedException
    {
        File output_dir = createOutputFolder();
        Path f1 = saveRandomized( "@NAME\nACGT\n+\n1234", output_dir.toPath(), true, "fastq-1", "gz" );
        Path f2 = saveRandomized( "@NAME\nACGT\n+\n1234", output_dir.toPath(), true, "fastq-2", "gz" );
        FastqScanner fs = new FastqScanner( expected_reads );
        RawReadsFile rf1 = new RawReadsFile();
        rf1.setFilename( f1.toFile().getCanonicalPath() );
        
        RawReadsFile rf2 = new RawReadsFile();
        rf2.setFilename( f2.toFile().getCanonicalPath() );

        ValidationResult vr = fs.checkFiles( rf1, rf2 );
        
        Assert.assertEquals( toString( vr.getMessages( Severity.ERROR ) ), 1, vr.getMessages( Severity.ERROR ).size() );
    }
    
    /* */
    @Test public void 
    testCase5() throws SecurityException, NoSuchMethodException, DataFeederException, IOException, InterruptedException
    {
        File output_dir = createOutputFolder();
        Path f1 = saveRandomized( "@NAME1\nACGT\n+\n1234", output_dir.toPath(), true, "fastq-1", "gz" );
        Path f2 = saveRandomized( "@NAME2\nACGT\n+\n1234\n"
                                + "@NAME2\nACGT\n+\n2341", output_dir.toPath(), true, "fastq-2", "gz" );
        
        FastqScanner fs = new FastqScanner( expected_reads );
        RawReadsFile rf1 = new RawReadsFile();
        rf1.setFilename( f1.toFile().getCanonicalPath() );
        
        RawReadsFile rf2 = new RawReadsFile();
        rf2.setFilename( f2.toFile().getCanonicalPath() );

        ValidationResult vr = fs.checkFiles( rf1, rf2 );
        
        Assert.assertEquals( toString( vr.getMessages( Severity.ERROR ) ), 1, vr.getMessages( Severity.ERROR ).size() );
    }

    
    /* three read labels */
    @Test public void 
    testCase6() throws SecurityException, NoSuchMethodException, DataFeederException, IOException, InterruptedException
    {
        File output_dir = createOutputFolder();
        Path f1 = saveRandomized( "@NAME\nACGT\n+\n1234", output_dir.toPath(), true, "fastq-1", "gz" );
        Path f2 = saveRandomized( "@NAME/2\nACGT\n+\n1234\n"
                                + "@NAME/3\nACGT\n+\n2341", output_dir.toPath(), true, "fastq-2", "gz" );
        
        FastqScanner fs = new FastqScanner( expected_reads );
        RawReadsFile rf1 = new RawReadsFile();
        rf1.setFilename( f1.toFile().getCanonicalPath() );
        
        RawReadsFile rf2 = new RawReadsFile();
        rf2.setFilename( f2.toFile().getCanonicalPath() );

        ValidationResult vr = fs.checkFiles( rf1, rf2 );
        
        Assert.assertEquals( toString( vr.getMessages( Severity.ERROR ) ), 1, vr.getMessages( Severity.ERROR ).size() );
    }


    /* Wrong pair set in two files */
    @Test public void 
    testCase7() throws SecurityException, NoSuchMethodException, DataFeederException, IOException, InterruptedException
    {
        File output_dir = createOutputFolder();
        Path f1 = saveRandomized( "@NAME1/1\nACGT\n+\n1234", output_dir.toPath(), true, "fastq-1", "gz" );
        Path f2 = saveRandomized( "@NAME2/2\nACGT\n+\n1234\n"
                                + "@NAME/2\nACGT\n+\n2341", output_dir.toPath(), true, "fastq-2", "gz" );
        
        FastqScanner fs = new FastqScanner( expected_reads );
        RawReadsFile rf1 = new RawReadsFile();
        rf1.setFilename( f1.toFile().getCanonicalPath() );
        
        RawReadsFile rf2 = new RawReadsFile();
        rf2.setFilename( f2.toFile().getCanonicalPath() );

        ValidationResult vr = fs.checkFiles( rf1, rf2 );
        
        Assert.assertEquals( toString( vr.getMessages( Severity.ERROR ) ), 1, vr.getMessages( Severity.ERROR ).size() );
    }
    
    
    /* Wrong pair set in one file */
    @Test public void 
    testCase8() throws SecurityException, NoSuchMethodException, DataFeederException, IOException, InterruptedException
    {
        File output_dir = createOutputFolder();
        Path f1 = saveRandomized( "@NAME2/1\nACGT\n+\n1234\n"
                                + "@NAME/2\nACGT\n+\n2341", output_dir.toPath(), true, "fastq-2", "gz" );
        
        FastqScanner fs = new FastqScanner( expected_reads );
        RawReadsFile rf1 = new RawReadsFile();
        rf1.setFilename( f1.toFile().getCanonicalPath() );
        
        ValidationResult vr = fs.checkFiles( rf1 );
        
        Assert.assertEquals( toString( vr.getMessages( Severity.ERROR ) ), 1, vr.getMessages( Severity.ERROR ).size() );
    }
    
    
    @Test public void 
    testPairWithDuplication() throws SecurityException, NoSuchMethodException, DataFeederException, IOException, InterruptedException
    {
        URL  url2 = GenomeAssemblyWebinCliTest.class.getClassLoader().getResource( "uk/ac/ebi/ena/rawreads/EP0_GTTCCTT_S1.txt.dup.gz" );
        URL  url1 = GenomeAssemblyWebinCliTest.class.getClassLoader().getResource( "uk/ac/ebi/ena/rawreads/EP0_GTTCCTT_S2.txt.gz" );

        FastqScanner fs = new FastqScanner( expected_reads );
        
        RawReadsFile rf1 = new RawReadsFile();
        rf1.setFilename( new File( url1.getFile() ).getCanonicalPath() );
        
        RawReadsFile rf2 = new RawReadsFile();
        rf2.setFilename( new File( url2.getFile() ).getCanonicalPath() );

        ValidationResult vr = fs.checkFiles( rf1, rf2 );
        Assert.assertEquals( toString( vr.getMessages( Severity.ERROR ) ), 2, vr.getMessages( Severity.ERROR ).size() );
    }
    

    @Test public void 
    testPairWithDuplication2() throws SecurityException, NoSuchMethodException, DataFeederException, IOException, InterruptedException
    {
        URL  url2 = GenomeAssemblyWebinCliTest.class.getClassLoader().getResource( "uk/ac/ebi/ena/rawreads/EP0_GTTCCTT_S1.txt.dup.gz" );
        //URL  url1 = GenomeAssemblyWebinCliTest.class.getClassLoader().getResource( "uk/ac/ebi/ena/rawreads/EP0_GTTCCTT_S2.txt.gz" );
        FastqScanner fs = new FastqScanner( expected_reads );
        //RawReadsFile rf1 = new RawReadsFile();
        //rf1.setFilename( new File( url1.getFile() ).getCanonicalPath() );
        
        RawReadsFile rf2 = new RawReadsFile();
        rf2.setFilename( new File( url2.getFile() ).getCanonicalPath() );

        ValidationResult vr = fs.checkFiles( rf2, rf2 );
        
        Assert.assertEquals( toString( vr.getMessages( Severity.ERROR ) ), 2, vr.getMessages( Severity.ERROR ).size() );
    }
    
    
    String
    toString( ValidationResult result ) throws IOException
    {
        return toString( result.getMessages() );
    }

    
    String
    toString( Collection<ValidationMessage<Origin>> result ) throws IOException
    {
        StringWriter sw = new StringWriter();
        for( ValidationMessage<?> m : result )
        {
            sw.write( String.format( "%s: %s%s\n", m.getSeverity(), m.getMessage(), Arrays.asList( m.getParams() ) ) );
        }
        return sw.toString();
    }
    
    
    
    Path
    generateRandomFastq( int number_of_reads,
                         int min_name_len, 
                         int max_name_len, 
                         int read_len ) throws IOException
    {
        Path result = Files.createTempFile( "TEMP", ".fastq" );
        
        while( number_of_reads--> 0 )
        {
            StringBuilder read = new StringBuilder();
           
            read.append( "@" )
                .append( ThreadLocalRandom.current()
                                          .ints( ThreadLocalRandom.current().nextInt( min_name_len, max_name_len ), 55, 95 )
                                          .mapToObj( e -> String.valueOf( Character.toString( (char)e ) ) )
                                          .collect( Collectors.joining() ) )
                .append( '\n' )
                .append( ThreadLocalRandom.current()
                        .ints( read_len, 0, 5 )
                        .mapToObj( e -> e == 0 ? "A" : e == 1 ? "C" : e == 2 ? "G" : e == 3 ? "T" : "N" )
                        .collect( Collectors.joining() ) )
                .append( '\n' )
                .append( '+' )
                .append( '\n' )
                .append( ThreadLocalRandom.current()
                        .ints( read_len, 33, 33 + 64 )
                        .mapToObj( e -> String.valueOf( Character.toString( (char)e ) ) )
                        .collect( Collectors.joining() ) )
                .append( '\n' );
            Files.write( result, read.toString().getBytes(), StandardOpenOption.SYNC, StandardOpenOption.APPEND );
        }        
        return result;
            
    }

    //TODO remove probabilistic nature
    @Test public void 
    testGeneratedSingleDuplications() throws SecurityException, NoSuchMethodException, DataFeederException, IOException, InterruptedException
    {
        FastqScanner fs = new FastqScanner( expected_reads );
        RawReadsFile rf = new RawReadsFile();
        Path path = generateRandomFastq( 1000, 2, 3, 80 );
        rf.setFilename( path.toString() );
        
        ValidationResult vr = fs.checkFiles( rf );
        
        Assert.assertEquals( toString( vr ), false, vr.getMessages( Severity.ERROR ).isEmpty() );
    }

}