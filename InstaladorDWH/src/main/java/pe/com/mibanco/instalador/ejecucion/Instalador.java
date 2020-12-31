/**
 * 
 */
package pe.com.mibanco.instalador.ejecucion;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.util.FileUtils;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import pe.com.mibanco.instalador.excepcion.InstaladorException;

/**
 * @author Edwin
 *
 */
public class Instalador {

	private static final Logger log = LogManager.getLogger(Instalador.class);

	Session session = null;
	Channel channel = null;

	private String numcaso = "";
	private String verinsta = "";
	private String verrever = "";
	private File fileprop = null;
	Properties prop = new Properties();
	String componentesInstalacion = "";
	String componentesReversa = "";
	boolean existeKettlers = false;
	boolean existeScriptBd = false;

	public Instalador(String numcaso, String verinsta, String verrever, String arcpropi) throws InstaladorException {
		try {
			this.numcaso = numcaso;
			this.verinsta = verinsta;
			this.verrever = verrever;
			fileprop = new File(arcpropi);
			InputStream is = new FileInputStream(fileprop);
			prop.load(is);
		} catch (FileNotFoundException e) {
			throw new InstaladorException(e.getMessage(), e);
		} catch (IOException e) {
			throw new InstaladorException(e.getMessage(), e);
		}
	}

	private void conectarConServidor() throws InstaladorException {
		try {

			log.info("Inicia Conexion al servidor " + prop.getProperty("instalador.dwh.servidor.host"));

			Properties config = new Properties();
			config.put("StrictHostKeyChecking", "no");

			JSch jsch = new JSch();
			int puerto = Integer.parseInt(prop.getProperty("instalador.dwh.servidor.puerto"));
			session = jsch.getSession(prop.getProperty("instalador.dwh.servidor.usuario"),
					prop.getProperty("instalador.dwh.servidor.host"), puerto);
			session.setPassword(prop.getProperty("instalador.dwh.servidor.credencial"));
			session.setConfig(config);
			session.connect();
			log.info("Conexion Exitosa al servidor");
		} catch (NumberFormatException e) {
			throw new InstaladorException(e.getMessage(), e);
		} catch (JSchException e) {
			throw new InstaladorException(e.getMessage(), e);
		}
	}

	private void abreCanal() throws InstaladorException {
		try {
			channel = session.openChannel("exec");
		} catch (JSchException e) {
			throw new InstaladorException(e.getMessage(), e);
		}
	}

	private void cierraCanal() throws InstaladorException {
		if (channel != null) {
			channel.disconnect();
		}
	}

	private List<String> ejecutarComando(String comando) throws InstaladorException {
		List<String> salida = new ArrayList<>();
		try {
			log.info("Ejecutando Comando " + comando);
			abreCanal();

			((ChannelExec) channel).setCommand(comando);
			channel.setInputStream(null);
			((ChannelExec) channel).setErrStream(System.err);

			InputStream in = channel.getInputStream();
			channel.connect();
			byte[] tmp = new byte[1024];
			while (true) {
				while (in.available() > 0) {
					int i = in.read(tmp, 0, 1024);
					if (i < 0) {
						break;
					}
					String s = new String(tmp, 0, i);
					s = StringUtils.normalizeSpace(s);
					log.debug(s);
					salida.add(s);
				}
				if (channel.isClosed()) {
					log.debug("exit-status: " + channel.getExitStatus());
					break;
				}
				try {
					Thread.sleep(1000);
				} catch (Exception e) {
					throw new InstaladorException(e.getMessage(), e);
				}
			}
			log.info("Comando " + comando + " ejecutado satisfactoriamente");
			return salida;
		} catch (JSchException e) {
			throw new InstaladorException(e.getMessage(), e);
		} catch (IOException e) {
			throw new InstaladorException(e.getMessage(), e);
		} catch (InstaladorException e) {
			throw new InstaladorException(e.getMessage(), e);
		} finally {
			cierraCanal();
		}
	}

	private void descomprimirComponentes() throws InstaladorException {
		String nomArchivo = "CASO-" + numcaso + "_COMPONENTES_V" + verinsta + ".0.zip";
		log.info("Descomprimiendo componente de instalacion "+nomArchivo);
		File archivoComprimido = new File(nomArchivo);
		componentesInstalacion = descomprimirArchivo(archivoComprimido);
		log.info("Componentes Instalacion ::"+componentesInstalacion);
		
		nomArchivo = "CASO-" + numcaso + "_REVERSA_V" + verrever + ".0.zip";
		log.info("Descomprimiendo componente de reversa "+nomArchivo);
		archivoComprimido = new File(nomArchivo);
		componentesReversa = descomprimirArchivo(archivoComprimido);
		log.info("Componentes reversa ::"+componentesReversa);
	}

	private String descomprimirArchivo(File archivoComprimido) throws InstaladorException {
		ZipInputStream zis = null;
		String rutaArchivo = null;
		try {
			String directorioZip = prop.getProperty("instalador.dwh.fuente");
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
		log.debug("ruta script bd ::"+rutaScriptBd);
		File ruta = new File(rutaScriptBd);
		File[] archivosDentro = ruta.listFiles();
		log.debug("archivos dentro ::"+archivosDentro.length);
		for (File file : archivosDentro) {
			if (file.isDirectory()) {
				File[] archivos2 = file.listFiles();
				for (File file2 : archivos2) {
					if (file2.isFile()) {
						String extension = FileUtils.getFileExtension(file2);
						log.debug("Extension archivo ::"+extension);
						existeScriptBd = ("sql".equalsIgnoreCase(extension));
						if (!existeScriptBd) {
							break;
						}
					}
				}
			}
			else if (file.isFile()) {
				String extension = FileUtils.getFileExtension(file);
				log.debug("Extension archivo ::"+extension);
				existeScriptBd = ("sql".equalsIgnoreCase(extension));
				if (!existeScriptBd) {
					break;
				}
			}
		}
		log.info("Validacion de Script BD");
		log.info("Existe Script BD :: "+(existeScriptBd?"Si":"No"));
	}

	private void validaKettlers() {
		String kettlersParam = prop.getProperty("instalador.dwh.ruta.kettlers");
		kettlersParam = StringUtils.replace(kettlersParam, "-", File.separator); 
		String rutaKettlers = componentesInstalacion + File.separator + kettlersParam;
		log.debug("ruta kettlers ::"+rutaKettlers);
		File ruta = new File(rutaKettlers);
		this.existeKettlers = (ruta.listFiles().length > 0);
		log.info("Validacion de Kettlers");
		log.info("Existe kettlers :: "+(existeKettlers?"Si":"No"));
	}

	public void ejecutar() throws InstaladorException {
		validacion();
		/*
		 * try {
		 * 
		 * } catch (InstaladorException e) { throw new
		 * InstaladorException(e.getMessage(), e); } finally { if (session != null) {
		 * session.disconnect(); } }
		 */
	}

	private void preparacion() throws InstaladorException {
		try {
			List<String> salida;
			conectarConServidor();

			String comando = "";
			comando = "ls -l /home/edwin/app/PASES/I_CASO_" + numcaso + " | grep V" + verinsta + "_E";
			salida = ejecutarComando(comando);
			int n = 0;
			if (salida.size() == 0) {
				comando = "mkdir /home/edwin/app/PASES/I_CASO_" + numcaso;
				salida = ejecutarComando(comando);
				n = 1;
			} else {
				n = encontrarMayor(salida);
				n = n + 1;
			}
			comando = "mkdir /home/edwin/app/PASES/I_CASO_" + numcaso + "/V" + verinsta + "_E" + n;
			salida = ejecutarComando(comando);

			comando = comando + "/componentes";
			salida = ejecutarComando(comando);
		} catch (InstaladorException e) {
			throw new InstaladorException(e.getMessage(), e);
		}
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
}
