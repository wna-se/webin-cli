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
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import de.vandermeer.asciitable.AT_Renderer;
import de.vandermeer.asciitable.AsciiTable;
import de.vandermeer.asciitable.CWC_FixedWidth;
import de.vandermeer.skb.interfaces.transformers.textformat.TextAlignment;
import org.apache.commons.lang3.StringUtils;
import org.fusesource.jansi.AnsiConsole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.OutputStreamAppender;
import picocli.CommandLine;

import uk.ac.ebi.ena.webin.cli.entity.Version;
import uk.ac.ebi.ena.webin.cli.manifest.*;
import uk.ac.ebi.ena.webin.cli.manifest.processor.CVFieldProcessor;
import uk.ac.ebi.ena.webin.cli.service.LoginService;
import uk.ac.ebi.ena.webin.cli.service.SubmitService;
import uk.ac.ebi.ena.webin.cli.service.VersionService;
import uk.ac.ebi.ena.webin.cli.submit.SubmissionBundle;
import uk.ac.ebi.ena.webin.cli.upload.ASCPService;
import uk.ac.ebi.ena.webin.cli.upload.FtpService;
import uk.ac.ebi.ena.webin.cli.upload.UploadService;

public class WebinCli {
	public final static int SUCCESS = 0;
	public final static int SYSTEM_ERROR = 1;
	public final static int USER_ERROR = 2;
	public final static int VALIDATION_ERROR = 3;

	private final WebinCliParameters parameters;
	private final WebinCliExecutor<?, ?> executor;

	private final static String LOG_FILE_NAME= "webin-cli.report";
	private final static Logger log = LoggerFactory.getLogger(WebinCli.class);

    public static void 
    main( String... args )
    {
        System.exit( __main( args ) );
	}

    private static int
    __main( String... args )
    {
    	System.setProperty("picocli.trace", "OFF");
        try 
        {
            WebinCliCommand params = parseParameters( args );
			if( null == params )
				return USER_ERROR;

            if (params.help || params.fields || params.version)
				return SUCCESS;

            checkLogin( params );
            checkVersion( params.test );

            WebinCli webinCli = new WebinCli( params );

			webinCli.execute();

			return SUCCESS;
		}
        catch( WebinCliException ex ) {
			switch( ex.getErrorType() )
			{
				case USER_ERROR:
					return USER_ERROR;
				case VALIDATION_ERROR:
					return VALIDATION_ERROR;
				default:
					return SYSTEM_ERROR;
			}
        }
        catch( Throwable e )
        {
            log.error( e.getMessage(), e );
            return SYSTEM_ERROR;
        }
    }

	public WebinCli(WebinCliCommand cmd) {
        this(initParameters(cmd));
    }

	public WebinCli(WebinCliParameters parameters) {
		this.parameters = parameters;
		this.executor = parameters.getContext().createExecutor(parameters);

		// initTimedConsoleLogger();
		initTimedFileLogger(parameters);
	}

	private static WebinCliParameters initParameters(WebinCliCommand cmd) {
		if( !cmd.inputDir.isDirectory() )
			throw WebinCliException.userError( WebinCliMessage.CLI_INPUT_PATH_NOT_DIR.format( cmd.inputDir.getPath() ) );
		if( !cmd.outputDir.isDirectory() )
			throw WebinCliException.userError( WebinCliMessage.CLI_OUTPUT_PATH_NOT_DIR.format( cmd.outputDir.getPath() ) );
		WebinCliParameters parameters = new WebinCliParameters();
		parameters.setContext( cmd.context );
		parameters.setManifestFile( cmd.manifest );
		parameters.setInputDir( cmd.inputDir  );
		parameters.setOutputDir( cmd.outputDir );
		parameters.setUsername( cmd.userName );
		parameters.setPassword( cmd.password );
		parameters.setCenterName( cmd.centerName );
		parameters.setValidate( cmd.validate );
		parameters.setSubmit( cmd.submit );
		parameters.setTest( cmd.test );
		parameters.setAscp( cmd.ascp );
		return parameters;
	}
	
	private void 
	initTimedAppender( String name, OutputStreamAppender<ILoggingEvent> appender ) 
	{
		LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
		PatternLayoutEncoder encoder = new PatternLayoutEncoder();
		encoder.setContext(loggerContext);
		encoder.setPattern( "%d{\"yyyy-MM-dd'T'HH:mm:ss\"} %-5level: %msg%n" );
		encoder.start();
		appender.setName( name );
		appender.setContext( loggerContext );
		appender.setEncoder( encoder );
		appender.start();
	}

	
	private void 
	initTimedFileLogger( WebinCliParameters parameters )
	{
		FileAppender<ILoggingEvent> fileAppender = new FileAppender<>();
		String logFile = new File( createOutputDir( parameters.getOutputDir(), "." ), LOG_FILE_NAME ).getAbsolutePath();
		fileAppender.setFile( logFile );
		fileAppender.setAppend( false );
		initTimedAppender( "FILE", fileAppender );

		log.info( "Creating report file: " + logFile );

		ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger( Logger.ROOT_LOGGER_NAME );
		logger.addAppender( fileAppender );
	}

	void
	execute()
	{
		try {
			executor.readManifest();

			if (parameters.isValidate() || executor.readSubmissionBundle() == null) {
				validate(executor);
			}

			if (parameters.isSubmit()) {
				submit(executor);
			}
		}
		catch (WebinCliException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw WebinCliException.systemError(ex);
		}
	}
	
	private void
	validate(WebinCliExecutor<?,?> executor)
	{
	   try 
	   {
		   executor.validateSubmission();

		   executor.prepareSubmissionBundle();

           log.info( WebinCliMessage.CLI_VALIDATE_SUCCESS.text() );
           
	   } catch( WebinCliException ex )
	   {
	      switch( ex.getErrorType() )
	      { 
	          case USER_ERROR:
				  throw WebinCliException.userError( ex, StringUtils.isBlank(ex.getMessage())? WebinCliMessage.CLI_VALIDATE_USER_ERROR_EX.format(executor.getValidationDir()):
						  WebinCliMessage.CLI_VALIDATE_USER_ERROR.format(ex.getMessage(), executor.getValidationDir()));
	          case VALIDATION_ERROR:
				  throw WebinCliException.validationError( ex, StringUtils.isBlank(ex.getMessage())? WebinCliMessage.CLI_VALIDATE_USER_ERROR_EX.format(executor.getValidationDir()):
						  WebinCliMessage.CLI_VALIDATE_USER_ERROR.format(ex.getMessage(), executor.getValidationDir()));
	               
	          case SYSTEM_ERROR:
	               throw WebinCliException.systemError( ex, WebinCliMessage.CLI_VALIDATE_SYSTEM_ERROR.format(ex.getMessage(), executor.getValidationDir()));
	      }
	   } catch( Exception ex )
	   {
	       StringWriter sw = new StringWriter();
	       ex.printStackTrace( new PrintWriter( sw ) );
	      throw WebinCliException.systemError( ex, WebinCliMessage.CLI_VALIDATE_SYSTEM_ERROR.format( null == ex.getMessage() ? sw.toString() : ex.getMessage(), executor.getValidationDir()));
	   }
	}

	private void
	submit(WebinCliExecutor<?,?> executor )
    {
		SubmissionBundle bundle = executor.readSubmissionBundle();

        UploadService ftpService = parameters.isAscp() && new ASCPService().isAvailable() ? new ASCPService() : new FtpService();
        
        try 
        {
            ftpService.connect( parameters.getUsername(), parameters.getPassword() );
            ftpService.upload( bundle.getUploadFileList(), bundle.getUploadDir(), executor.getParameters().getInputDir().toPath() );
			log.info( WebinCliMessage.CLI_UPLOAD_SUCCESS.text() );

        } catch( WebinCliException e ) 
        {
        	throw WebinCliException.error(e, WebinCliMessage.CLI_UPLOAD_ERROR.format(e.getErrorType().text));
        } finally
        {
            ftpService.disconnect();
        }

        try 
        {
            SubmitService submitService = new SubmitService.Builder()
                                                           .setSubmitDir( bundle.getSubmitDir().getPath() )
                                                           .setUserName( parameters.getUsername() )
                                                           .setPassword( parameters.getPassword() )
                                                           .setTest( parameters.isTest() )
                                                           .build();
            submitService.doSubmission( bundle.getXMLFileList(), bundle.getCenterName(), getVersionForSubmission() );

        } catch( WebinCliException e ) 
        {
			throw WebinCliException.error(e, WebinCliMessage.CLI_SUBMIT_ERROR.format(e.getErrorType().text));
        }
    }
	

    private static WebinCliCommand
    parseParameters( String... args ) 
    {
		AnsiConsole.systemInstall();
		WebinCliCommand params = new WebinCliCommand();
		CommandLine commandLine = new CommandLine(params);
		commandLine.setExpandAtFiles(false);
		commandLine.parse(args);

        String cmd = "java -jar webin-cli-" + getVersionForUsage() + ".jar";
        commandLine.setCommandName(cmd);

		try
		{
			commandLine.parse(args);
			if (commandLine.isUsageHelpRequested()) {
				if (params.help) {
					commandLine.usage(System.out);
				}
				if (params.fields) {
					if (params.context != null) {
						printManifestHelp(params.context, System.out);
					}
					else {
						for (WebinCliContext context: WebinCliContext.values()) {
							printManifestHelp(context, System.out);
						}
					}
				}
				return params;
			}

			if (commandLine.isVersionHelpRequested()) {
				commandLine.printVersionHelp(System.out);
				return params;
			}

			if (!params.manifest.isFile() || !Files.isReadable(params.manifest.toPath())) {
				log.error("Unable to read the manifest file.");
				printHelp();
				return null;
			}
			params.manifest = params.manifest.getAbsoluteFile();

			if (params.inputDir == null) {
				params.inputDir = Paths.get(".").toFile().getAbsoluteFile();
			}
			params.inputDir = params.inputDir.getAbsoluteFile();

			if (params.outputDir == null) {
				params.outputDir = params.manifest.getParentFile();
			}
			params.outputDir = params.outputDir.getAbsoluteFile();

			if (!params.inputDir.canRead()) {
				log.error("Unable to read from the input directory: " + params.inputDir.getAbsolutePath());
				printHelp();
				return null;
			}

			if (!params.outputDir.canWrite()) {
				log.error("Unable to write to the output directory: " + params.outputDir.getAbsolutePath());
				printHelp();
				return null;
			}

			if( !params.validate && !params.submit ) {
				log.error("Either -validate or -submit option must be provided.");
				printHelp();
				return null;
			}
			
	        return params;
	        
		} catch( Exception e )
		{
			log.error( e.getMessage(), e );
			printHelp();
			return null;
		}
	}

	public static void printManifestHelp(WebinCliContext context, PrintStream out) {
		ManifestReader<?> manifestReader =
				new ManifestReaderBuilder(context.getManifestReaderClass()).build();
		out.println();
		out.println("Manifest fields for '" + context.name() + "' context:");
		out.println();
		printManifestFieldHelp(manifestReader, out);
		out.println();
		out.println("Data files for '" + context.name() + "' context:");
		out.println();
		printManifestFileGroupHelp(manifestReader, out);
	}

	private static void printManifestFieldHelp(ManifestReader<?> manifestReader, PrintStream out) {
		AsciiTable table = new AsciiTable();
		AT_Renderer renderer = AT_Renderer.create();
		CWC_FixedWidth cwc = new CWC_FixedWidth();
		cwc.add(20);
		cwc.add(11);
		cwc.add(45);
		renderer.setCWC(cwc);
		table.setRenderer(renderer);
		table.addRule();
		table.addRow("Field", "Cardinality", "Description");

		Comparator<ManifestFieldDefinition> comparator = (f1, f2) ->
		{
			ManifestFieldType t1 = f1.getType();
			ManifestFieldType t2 = f2.getType();
			int min1 = f1.getRecommendedMinCount();
			int min2 = f2.getRecommendedMinCount();
			if(t1 == t2 && min1 == min2) {
				return 0;
			}
			if(t1 == t2 && min1 > min2) {
				return -1;
			}
			if(t1 == t2 && min1 < min2) {
				return 1;
			}
			if(t1 == ManifestFieldType.META) {
				return -1;
			}
			return 1;
		};
		manifestReader.getFields().stream()
				.filter(field -> field.getRecommendedMaxCount() > 0)
				.sorted(comparator)
				.forEach(field ->
			printManifestFieldHelp(table, field)
		);
		table.addRule();
		table.setPadding(0);
		table.setTextAlignment(TextAlignment.LEFT);
		out.println(table.render());
	}

	private static void printManifestFieldHelp(AsciiTable table, ManifestFieldDefinition field) {
    	String name = field.getName();
		if (field.getSynonym() != null) {
			name  += " (" + field.getSynonym() + ")";
		}

		String cardinality;
    	int minCount = field.getRecommendedMinCount();
		int maxCount = field.getRecommendedMaxCount();
		if (field.getType() == ManifestFieldType.META) {
			cardinality = minCount > 0 ? "Mandatory" : "Optional";
		}
		else {
			if (minCount == maxCount) {
				cardinality = minCount + " file";
			}
			else {
				cardinality = minCount + "-" + maxCount + " files";
			}
		}
		String value = "";
		if (field.getType() == ManifestFieldType.META) {
			for (ManifestFieldProcessor processor : field.getFieldProcessors()) {
				if (processor instanceof CVFieldProcessor) {
					value = ": <br/>* " + ((CVFieldProcessor) processor).getValues().stream().collect(Collectors.joining("<br/>* "));
				}
			}
		}
		table.addRule();
		table.addRow(name, cardinality, field.getDescription() + value);
	}

	private static void printManifestFileGroupHelp(ManifestReader<?> manifestReader, PrintStream out) {
		List<ManifestFieldDefinition> fields = manifestReader.getFields()
				.stream()
				.filter(field -> field.getType() == ManifestFieldType.FILE)
				.collect(Collectors.toList());

		List<ManifestFileGroup> groups =
				manifestReader.getFileGroups()
						.stream()
						.sorted(Comparator.comparingInt(ManifestFileGroup::getFileCountsSize))
						.collect(Collectors.toList());

		AsciiTable table = new AsciiTable();
		AT_Renderer renderer = AT_Renderer.create();
		CWC_FixedWidth cwc = new CWC_FixedWidth();
		int tableWidth = 80;
		int descriptionWidth = 30;
		cwc.add(descriptionWidth);
		fields.forEach( field ->
				cwc.add((tableWidth - descriptionWidth - 2 - fields.size())/fields.size()));
		renderer.setCWC(cwc);
		table.setRenderer(renderer);
		table.addRule();

		ArrayList<String> row = new ArrayList<>();
		row.add("Data files");
		fields.stream().forEach( field -> row.add(field.getName()));
		table.addRow(row);
		table.addRule();

		groups.stream().forEach( group -> {
			row.clear();
			row.add(group.getDescription());
			fields.stream().forEach( field ->
				row.add(printManifestFileCountHelp(field, group)));
			table.addRow(row);
			table.addRule();
		} );
		table.setPadding(0);
		table.setTextAlignment(TextAlignment.LEFT);
		out.println(table.render());
	}

	private static String printManifestFileCountHelp(ManifestFieldDefinition field, ManifestFileGroup group) {
		ManifestFileCount count = null;
		for (ManifestFileCount fileCount : group.getFileCounts()) {
			if (field.getName().equals(fileCount.getFileType())) {
				count = fileCount;
				break;
			}
		}
		if (count == null) {
			return "";
		}
		if (count.getMaxCount() != null) {
			if (count.getMinCount() == count.getMaxCount()) {
				return String.valueOf(count.getMinCount());
			}
			return count.getMinCount() + "-" + count.getMaxCount();
		}
		return ">=" + count.getMinCount();
	}

	public WebinCliParameters getParameters() {
		return parameters;
	}

	public WebinCliExecutor<?, ?> getExecutor() {
		return executor;
	}

	private static void
	printHelp() 
	{
		log.info( "Please use " + WebinCliCommand.Options.help + " option to see all command line options." );
	}

	
    public static String 
	getVersionForSubmission()
	{
		String version = getVersion();
		return String.format( "%s:%s", WebinCli.class.getSimpleName(), null == version ? "" : version );
	}


	public static String 
	getVersionForUsage() 
	{
		String version = getVersion();
		return String.format( "%s", null == version ? "?" : version );
	}

	
	private static String 
	getVersion() 
	{
		return WebinCli.class.getPackage().getImplementationVersion();
	}


	private static void checkLogin( WebinCliCommand parameters )
	{
		// Replace user name with the Webin-N submission account name.
		parameters.userName = new LoginService(
				parameters.userName,
				parameters.password,
				parameters.test).login();
	}

	private static void checkVersion( boolean test )
	{
		String currentVersion = getVersion();
		
		if( null == currentVersion || currentVersion.isEmpty() )
		    return;

		Version version = new VersionService.Builder()
				.setTest( test )
				.build().getVersion( currentVersion );

		log.info(WebinCliMessage.CLI_CURRENT_VERSION.format(currentVersion));

		if (!version.valid) {
			throw WebinCliException.userError(WebinCliMessage.CLI_UNSUPPORTED_VERSION.format(
					version.minVersion,
					version.latestVersion));
		}

		if (version.expire) {
			log.info(WebinCliMessage.CLI_EXPIRYING_VERSION.format(
					new SimpleDateFormat("dd MMM yyyy").format(version.nextMinVersionDate),
					version.nextMinVersion,
					version.latestVersion));
		}
		else if (version.update) {
			log.info(WebinCliMessage.CLI_NEW_VERSION.format(version.latestVersion));
		}

		if (version.comment != null) {
			log.info(version.comment);
		}
	}

	// Directory creation.
	static File
	getReportFile( File dir, String filename, String suffix )
	{
		if( dir == null || !dir.isDirectory() )
			throw WebinCliException.systemError( WebinCliMessage.CLI_INVALID_REPORT_DIR_ERROR.format(filename ));

		return new File( dir, Paths.get( filename ).getFileName().toString() + suffix );
	}

	
	public static File
	createOutputDir( File outputDir, String... dirs ) throws WebinCliException
	{
		if (outputDir == null) {
			throw WebinCliException.systemError( WebinCliMessage.CLI_MISSING_OUTPUT_DIR_ERROR.text());
		}

		String[] safeDirs = getSafeOutputDirs(dirs);

		Path p;

		try {
			p = Paths.get(outputDir.getPath(), safeDirs);
		} catch (InvalidPathException ex) {
			throw WebinCliException.systemError( WebinCliMessage.CLI_CREATE_DIR_ERROR.format(ex.getInput()));
		}

		File dir = p.toFile();

		if (!dir.exists() && !dir.mkdirs()) {
			throw WebinCliException.systemError( WebinCliMessage.CLI_CREATE_DIR_ERROR.format(dir.getPath()));
		}

		return dir;
	}


	public static String
	getSafeOutputDir(String dir )
	{
		return dir
				.replaceAll( "[^a-zA-Z0-9-_\\.]", "_" )
				.replaceAll( "_+", "_" )
				.replaceAll( "^_+(?=[^_])","" )
				.replaceAll( "(?<=[^_])_+$", "" );
	}

	public static String[]
	getSafeOutputDirs(String ... dirs )
	{
		return Arrays.stream( dirs )
		             .map( dir -> getSafeOutputDir(dir) )
		             .toArray( String[]::new );
	}
}
