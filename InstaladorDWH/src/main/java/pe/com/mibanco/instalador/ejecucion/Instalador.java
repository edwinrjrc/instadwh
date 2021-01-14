/**
 * 
 */
package pe.com.mibanco.instalador.ejecucion;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.SocketException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;
import org.apache.commons.net.util.TrustManagerUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.util.FileUtils;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.client.subsystem.sftp.SftpClient;
import org.apache.sshd.client.subsystem.sftp.SftpClientFactory;
import org.apache.sshd.common.channel.Channel;

import oracle.dbtools.db.ResultSetFormatter;
import oracle.dbtools.raptor.newscriptrunner.ScriptExecutor;
import oracle.dbtools.raptor.newscriptrunner.ScriptRunnerContext;
import pe.com.mibanco.instalador.excepcion.InstaladorException;
import pe.com.mibanco.instalador.util.Constantes;

/**
 * @author Edwin
 *
 */
public class Instalador extends Thread implements Runnable{

	private static final Logger log = LogManager.getLogger(Instalador.class);

	// Session session = null;
	// Channel channel = null;

	SshClient clientMina = null;
	ClientChannel channelMina = null;
	ClientSession session = null;
	OutputStream pipedIn = null;

	FTPSClient ftp = null;

	private String numcaso = "";
	private String verinsta = "";
	private String verrever = "";
	private String ambiente = "";
	private String acciones = "";
	private File fileprop = null;
	Properties prop = new Properties();
	String componentesInstalacion = "";
	String componentesReversa = "";
	boolean existeKettlers = false;
	boolean existeScriptBd = false;

	public Instalador(String numcaso, String verinsta, String verrever, String arcpropi, String ambiente, String acciones) throws InstaladorException {
		try {
			this.numcaso = numcaso;
			this.verinsta = verinsta;
			this.verrever = verrever;
			fileprop = new File(arcpropi);
			InputStream is = new FileInputStream(fileprop);
			prop.load(is);
			this.ambiente = ambiente;
			this.acciones = acciones;
		} catch (FileNotFoundException e) {
			throw new InstaladorException(e.getMessage(), e);
		} catch (IOException e) {
			throw new InstaladorException(e.getMessage(), e);
		}
	}

	private void conectarConServidor2() throws InstaladorException {
		ServerKeyVerifier serverKey = AcceptAllServerKeyVerifier.INSTANCE;

		SshClient client = SshClient.setUpDefaultClient();
		client.setServerKeyVerifier(serverKey);
	}

	private void iniciarCliente() throws InstaladorException {
		clientMina = SshClient.setUpDefaultClient();
		clientMina.start();

		int puerto = Integer.parseInt(prop.getProperty("instalador.dwh.servidor.puerto"));
		try {
			session = clientMina
					.connect(prop.getProperty("instalador.dwh.servidor.usuario"),
							prop.getProperty("instalador.dwh.servidor.host"), puerto)
					.verify(Constantes.DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS).getSession();

			session.addPasswordIdentity(prop.getProperty("instalador.dwh.servidor.credencial"));
			session.auth().verify(Constantes.DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

			channelMina = session.createChannel(Channel.CHANNEL_SHELL);
			ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
			channelMina.setOut(responseStream);
			channelMina.open().verify(Constantes.DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			pipedIn = channelMina.getInvertedIn();

			log.info(" channelMina Iniciado");
		} catch (IOException e) {
			throw new InstaladorException(e.getMessage(), e);
		}
	}

	private void ejecutarComandoMina(String comando) throws InstaladorException {
		try {
			log.info("Ejecutando comando :: " + comando);
			comando = comando + "\n";
			pipedIn.write(comando.getBytes());
			pipedIn.flush();
			long segundos = Constantes.DEFAULT_TIMEOUT_SECONDS;
			segundos = 2;
			channelMina.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), TimeUnit.SECONDS.toMillis(segundos));
		} catch (IOException e) {
			throw new InstaladorException(e.getMessage(), e);
		}
	}

	private void terminarSesionSsh() throws InstaladorException {
		try {
			pipedIn.close();
			channelMina.close(false);
			session.close();
			clientMina.stop();
			clientMina.close();
		} catch (IOException e) {
			throw new InstaladorException(e.getMessage(), e);
		}

	}

	private void ejecutarSshMina(String command) throws InstaladorException {
		try {
			SshClient client = SshClient.setUpDefaultClient();
			client.start();

			try (ClientSession session = client
					.connect(prop.getProperty("instalador.dwh.servidor.usuario"),
							prop.getProperty("instalador.dwh.servidor.host"), 22)
					.verify(10, TimeUnit.SECONDS).getSession()) {
				session.addPasswordIdentity(prop.getProperty("instalador.dwh.servidor.credencial"));
				session.auth().verify(10, TimeUnit.SECONDS);

				try (ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
						ClientChannel channel = session.createChannel(Channel.CHANNEL_SHELL)) {
					channel.setOut(responseStream);
					try {
						channel.open().verify(10, TimeUnit.SECONDS);
						try (OutputStream pipedIn = channel.getInvertedIn()) {
							pipedIn.write(command.getBytes());
							pipedIn.flush();
						}

						channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), TimeUnit.SECONDS.toMillis(10));
						String responseString = new String(responseStream.toByteArray());
						System.out.println(responseString);
					} finally {
						channel.close(false);
					}
				}
			} finally {
				client.stop();
			}
		} catch (IOException e) {
			throw new InstaladorException(e.getMessage(), e);
		}
	}

	private void descomprimirComponentes() throws InstaladorException {
		String nomArchivo = obtenerNombreComponenteInstalacion();
		log.info("Descomprimiendo componente de instalacion " + nomArchivo);
		File archivoComprimido = new File(nomArchivo);
		componentesInstalacion = descomprimirArchivo(archivoComprimido);
		log.info("Componentes Instalacion ::" + componentesInstalacion);

		nomArchivo = obtenerNombreComponenteReversa();
		log.info("Descomprimiendo componente de reversa " + nomArchivo);
		archivoComprimido = new File(nomArchivo);
		componentesReversa = descomprimirArchivo(archivoComprimido);
		log.info("Componentes reversa ::" + componentesReversa);
	}

	private String obtenerNombreComponenteInstalacion() {
		String nomArchivo = this.obtenerNombreCarpetaComponenteInstalacion() + ".zip";
		return nomArchivo;
	}
	
	private String obtenerNombreCarpetaComponenteInstalacion() {
		String nomArchivo = "CASO-" + numcaso + "_COMPONENTES_V" + verinsta + ".0";
		return nomArchivo;
	}

	private String obtenerNombreComponenteReversa() {
		String nomArchivo = "CASO-" + numcaso + "_REVERSA_V" + verrever + ".0.zip";
		return nomArchivo;
	}

	private String descomprimirArchivo(File archivoComprimido) throws InstaladorException {
		ZipInputStream zis = null;
		String rutaArchivo = null;
		try {
			String directorioZip = prop.getProperty("instalador.dwh.ruta.fuente");
			directorioZip = directorioZip + File.separator + numcaso + File.separator;
			rutaArchivo = directorioZip + File.separator + archivoComprimido;
			zis = new ZipInputStream(new FileInputStream(rutaArchivo));
			ZipEntry salida;

			while (null != (salida = zis.getNextEntry())) {
				if (salida.isDirectory()) {
					File d = new File(directorioZip + salida.getName());
					if (!d.exists()) {
						d.mkdirs();
					}
				} else {
					FileOutputStream fos = new FileOutputStream(directorioZip + salida.getName());
					int leer;
					byte[] buffer = new byte[1024];
					while (0 < (leer = zis.read(buffer))) {
						fos.write(buffer, 0, leer);
					}
					fos.flush();
					fos.close();
				}
			}
			int ultimoPunto = StringUtils.lastIndexOf(rutaArchivo, ".");
			return StringUtils.substring(rutaArchivo, 0, ultimoPunto);
		} catch (FileNotFoundException e) {
			throw new InstaladorException(e.getMessage(), e);
		} catch (IOException e) {
			throw new InstaladorException(e.getMessage(), e);
		} finally {
			try {
				if (zis != null) {
					zis.closeEntry();
					zis.close();
				}
			} catch (IOException e) {
				throw new InstaladorException(e.getMessage(), e);
			}
		}
	}

	public void validacion() throws InstaladorException {
		descomprimirComponentes();
		validaComponentes();
	}

	private void validaComponentes() {
		validaKettlers();
		validarScriptBd();
	}

	private void validarScriptBd() {
		String scriptBdParam = prop.getProperty("instalador.dwh.ruta.scriptbd");
		scriptBdParam = StringUtils.replace(scriptBdParam, "-", File.separator);
		String rutaScriptBd = componentesInstalacion + File.separator + scriptBdParam;
		log.debug("ruta script bd ::" + rutaScriptBd);
		File ruta = new File(rutaScriptBd);
		File[] archivosDentro = ruta.listFiles();
		log.debug("archivos dentro ::" + archivosDentro.length);
		for (File file : archivosDentro) {
			if (file.isDirectory()) {
				File[] archivos2 = file.listFiles();
				for (File file2 : archivos2) {
					if (file2.isFile()) {
						String extension = FileUtils.getFileExtension(file2);
						log.debug("Extension archivo ::" + extension);
						existeScriptBd = ("sql".equalsIgnoreCase(extension));
						if (!existeScriptBd) {
							break;
						}
					}
				}
			} else if (file.isFile()) {
				String extension = FileUtils.getFileExtension(file);
				log.debug("Extension archivo ::" + extension);
				existeScriptBd = ("sql".equalsIgnoreCase(extension));
				if (!existeScriptBd) {
					break;
				}
			}
		}
		log.info("Validacion de Script BD");
		log.info("Existe Script BD :: " + (existeScriptBd ? "Si" : "No"));
	}

	private void validaKettlers() {
		String kettlersParam = prop.getProperty("instalador.dwh.ruta.kettlers");
		kettlersParam = StringUtils.replace(kettlersParam, "-", File.separator);
		String rutaKettlers = componentesInstalacion + File.separator + kettlersParam;
		log.debug("ruta kettlers ::" + rutaKettlers);
		File ruta = new File(rutaKettlers);
		this.existeKettlers = (ruta.listFiles().length > 0);
		log.info("Validacion de Kettlers");
		log.info("Existe kettlers :: " + (existeKettlers ? "Si" : "No"));
	}

	public void ejecutar() throws InstaladorException {
		// validacion();
		instalacion();
	}

	private void instalacion() throws InstaladorException {
		if (existeKettlers && false) {
			instalarAplicacion();
		}
		if (existeScriptBd || true) {
			instalarBd();
		}
	}

	private void instalarBd() {
		determinaBd();
		
	}

	private void determinaBd() {
		String fuentes = prop.getProperty("instalador.dwh.ruta.fuente");
		fuentes = fuentes + File.separator + this.numcaso;
		fuentes = fuentes + File.separator + this.obtenerNombreComponenteInstalacion();
		
		String rutaScriptBd = fuentes + File.separator;
		rutaScriptBd = rutaScriptBd + File.separator + "Componentes";
		rutaScriptBd = rutaScriptBd + File.separator + "00_ScriptsBD";
		
		File directorioFuentes = new File (rutaScriptBd);
		File[] listadodirectorios = directorioFuentes.listFiles();
	}

	private void instalarBd3() throws InstaladorException {
		Connection conn = null;
		try {
			String jdbcUrl = "jdbc:oracle:thin:@//"+prop.getProperty("instalador.dwh.bd.dwhdesa.host")+":1521/DWHDESA";
			conn = DriverManager.getConnection("jdbc:oracle:thin:@//desk1:1521/DWHDESA", "DWHADM", "admin");
			conn.setAutoCommit(false);

			// #create sqlcl
			ScriptExecutor sqlcl = new ScriptExecutor(conn);

			// #setup the context
			ScriptRunnerContext ctx = new ScriptRunnerContext();

			// set the output max rows
			ResultSetFormatter.setMaxRows(10000);
			// #set the context
			sqlcl.setScriptRunnerContext(ctx);
			ctx.setBaseConnection(conn);

			// Capture the results without this it goes to STDOUT
			ByteArrayOutputStream bout = new ByteArrayOutputStream();
			BufferedOutputStream buf = new BufferedOutputStream(bout);
			sqlcl.setOut(buf);

			// # run a whole file
			// adjust the path as it needs to be absolute
			String folder = sqlcl.getDirectory();
			log.info(folder);
			sqlcl.setDirectory("D:\\casos\\17231\\CASO-17231_COMPONENTES_V14.0\\Componentes\\00_ScriptsBD\\DWHPROD");
			sqlcl.setStmt("Script_00_CASO-17231_Principal.sql");

			String results = bout.toString("UTF8");
			results = results.replaceAll(" force_print\n", "");
			log.info(results);

		} catch (UnsupportedEncodingException e) {
			throw new InstaladorException(e.getMessage(), e);
		} catch (SQLException e) {
			throw new InstaladorException(e.getMessage(), e);
		} finally {
			try {
				if (conn != null) {
					conn.close();
				}
			} catch (SQLException e) {
				throw new InstaladorException(e.getMessage(), e);
			}
		}

	}

	private void instalarBd2() throws InstaladorException {
		try {
			ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", "d:", 
					"\"cd d:\\casos\\17231\\CASO-17231_COMPONENTES_V14.0\\Componentes\\00_ScriptsBD\\DWHPROD\" end", "sqlplus",
					"dwhadm/admin@DWHDESA", "@Script_00_CASO-17231_Principal.sql");
			builder.redirectErrorStream(true);
			log.info("1");
			Process p = builder.start();

			log.info("2");
			BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
			log.info("3");
			String line;
			while (true) {
				line = r.readLine();
				if (line == null) {
					break;
				}
				log.info(line);
			}
			log.info("4");
		} catch (IOException e) {
			throw new InstaladorException(e.getMessage(), e);
		}
	}
	
	private void instalarBd4() throws InstaladorException {
		try {
			Process p = Runtime.getRuntime()
					.exec("cmd /c d: && cd d:\\casos\\17231\\CASO-17231_COMPONENTES_V14.0\\Componentes\\00_ScriptsBD\\DWHPROD && dir");

		} catch (IOException e) {
			throw new InstaladorException(e.getMessage(), e);
		}
		
	}
	
	private void instalarBd5() throws InstaladorException {
		try {
			CommandLine cmdLine = CommandLine.parse("cmd /c d: && cd d:\\casos\\17231\\CASO-17231_COMPONENTES_V14.0\\Componentes\\00_ScriptsBD\\DWHPROD && sqlplus dwhadm/admin@DWHDESA && @Script_00_CASO-17231_Principal.sql");
			DefaultExecutor executor = new DefaultExecutor();
			int exitValue = executor.execute(cmdLine);
		} catch (ExecuteException e) {
			throw new InstaladorException(e.getMessage(), e);
		} catch (IOException e) {
			throw new InstaladorException(e.getMessage(), e);
		}
	}

	private void instalarBd1() throws InstaladorException {
		try {
			DefaultExecutor executor = new DefaultExecutor();
			String line = "";
			CommandLine cmdLine = null;

			DefaultExecuteResultHandler resultHandler = new DefaultExecuteResultHandler();

			ExecuteWatchdog watchdog = new ExecuteWatchdog(600000);
			executor.setWatchdog(watchdog);

			line = "cmd";
			cmdLine = CommandLine.parse(line);
			executor.execute(cmdLine, resultHandler);

			int exitValue = executor.execute(cmdLine);

			log.info(exitValue);

			line = "D:";
			cmdLine = CommandLine.parse(line);
			executor.execute(cmdLine);

			line = "cd D:\\casos\\17231\\CASO-17231_COMPONENTES_V14.0\\Componentes\\00_ScriptsBD\\DWHPROD";
			cmdLine = CommandLine.parse(line);
			executor.execute(cmdLine);

			line = "sqlplus dwhadm/admin@DWHDESA";
			cmdLine = CommandLine.parse(line);
			executor.execute(cmdLine);

			line = "spool instalacion.log";
			cmdLine = CommandLine.parse(line);
			executor.execute(cmdLine);

			line = "spool off";
			cmdLine = CommandLine.parse(line);
			executor.execute(cmdLine);

			line = "exit";
			cmdLine = CommandLine.parse(line);
			executor.execute(cmdLine);

		} catch (ExecuteException e) {
			throw new InstaladorException(e.getMessage(), e);
		} catch (IOException e) {
			throw new InstaladorException(e.getMessage(), e);
		}

	}

	private void instalarAplicacion() throws InstaladorException {
		// iniciarCliente();
		// prepararCarpetaInstalacion();
		// terminarSesionSsh();
		subirComponentes();
	}

	private void subirComponentes() throws InstaladorException {
		log.info("Iniciando subir componentes");
		// conectarAFtpServer();
		// subirArchivos();
		subirArchivos2();
	}

	private void subirArchivos() throws InstaladorException {
		try {
			log.info("Iniciando Subir Archivos");
			String archivoCargar = prop.getProperty("instalador.dwh.ruta.fuente") + File.separator + this.numcaso
					+ File.separator;
			archivoCargar = archivoCargar + this.obtenerNombreComponenteInstalacion();
			try (InputStream input = new FileInputStream(new File(archivoCargar))) {
				this.ftp.storeFile(
						prop.getProperty("instalador.dwh.ruta.pases") + this.obtenerNombreComponenteInstalacion(),
						input);
			}
		} catch (FileNotFoundException e) {
			throw new InstaladorException(e.getMessage(), e);
		} catch (IOException e) {
			throw new InstaladorException(e.getMessage(), e);
		}
	}

	private void subirArchivos2() throws InstaladorException {
		try {
			SshClient client = SshClient.setUpDefaultClient();
			client.start();
			int puerto = Integer.parseInt(prop.getProperty("instalador.dwh.servidor.puerto"));
			ClientSession session = client.connect(prop.getProperty("instalador.dwh.servidor.usuario"),
					prop.getProperty("instalador.dwh.servidor.host"), puerto).verify().getSession();
			session.addPasswordIdentity(prop.getProperty("instalador.dwh.servidor.credencial"));
			session.auth().verify();

			SftpClientFactory factory = SftpClientFactory.instance();
			SftpClient sftp = factory.createSftpClient(session);

			SftpClient.CloseableHandle handle = sftp.open(
					prop.getProperty("instalador.dwh.ruta.pases") + "/" + this.obtenerNombreComponenteInstalacion(),
					EnumSet.of(SftpClient.OpenMode.Write, SftpClient.OpenMode.Create));
			String rutaLocal = prop.getProperty("instalador.dwh.ruta.fuente") + File.separator + this.numcaso
					+ File.separator + this.obtenerNombreComponenteInstalacion();
			FileInputStream in = new FileInputStream(rutaLocal);
			int buff_size = 1024 * 1024;
			byte[] src = new byte[buff_size];
			int len;
			long fileOffset = 0l;
			while ((len = in.read(src)) != -1) {
				sftp.write(handle, fileOffset, src, 0, len);
				fileOffset += len;
			}

			in.close();
			sftp.close(handle);

			session.close(false);
			client.stop();
		} catch (IOException e) {
			throw new InstaladorException(e.getMessage(), e);
		}
	}

	private void conectarAFtpServer() throws InstaladorException {
		try {
			log.info("Iniciando Conectar Ftp Server");
			ftp = new FTPSClient(true);
			FTPClientConfig config = new FTPClientConfig("LINUX");
			config.setUnparseableEntries(true);
			int reply = 0;
			ftp.configure(config);
			ftp.setTrustManager(TrustManagerUtils.getAcceptAllTrustManager());
			ftp.connect("desk1", 22);
			reply = ftp.getReplyCode();
			if (!FTPReply.isPositiveCompletion(reply)) {
				ftp.disconnect();
				throw new InstaladorException("Excepcion conectando por ftp");
			}
			ftp.login(prop.getProperty("instalador.dwh.servidor.usuario"),
					prop.getProperty("instalador.dwh.servidor.credencial"));
			ftp.setFileType(FTP.BINARY_FILE_TYPE);
			ftp.enterLocalPassiveMode();
		} catch (SocketException e) {
			throw new InstaladorException(e.getMessage(), e);
		} catch (IOException e) {
			throw new InstaladorException(e.getMessage(), e);
		} catch (Exception e) {
			throw new InstaladorException(e.getMessage(), e);
		}
	}

	private void prepararCarpetaInstalacion() throws InstaladorException {
		String comando = "";

		comando = "cd " + prop.getProperty("instalador.dwh.ruta.pases");
		ejecutarComandoMina(comando);

		comando = "rm -R CASO-" + numcaso;
		ejecutarComandoMina(comando);
	}

	private int encontrarMayor(List<String> lista) {
		int mayor = 0;
		if (lista != null && !lista.isEmpty()) {
			int n = 0;
			String a = "";
			for (String s : lista) {
				a = StringUtils.substring(s, StringUtils.length(s) - 1, StringUtils.length(s));
				n = Integer.parseInt(a);
				if (n >= mayor) {
					mayor = n;
				}
			}
		}
		return mayor;
	}
	
	@Override
	public void run() {
		// TODO Auto-generated method stub
		super.run();
	}
	
}