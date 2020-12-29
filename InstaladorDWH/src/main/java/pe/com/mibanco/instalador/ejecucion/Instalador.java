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
		File archivoComprimido = new File(nomArchivo);
		descomprimirArchivo(archivoComprimido);
	}

	private void descomprimirArchivo(File archivoComprimido) throws InstaladorException {
		ZipInputStream zis = null;
		try {
			String directorioZip = prop.getProperty("instalador.dwh.fuente");
			directorioZip = directorioZip + File.separator + numcaso + File.separator;
			zis = new ZipInputStream(new FileInputStream(directorioZip + File.separator + archivoComprimido));

			ZipEntry salida;

			while (null != (salida = zis.getNextEntry())) {
				if (salida.isDirectory()) {
					File d = new File(directorioZip + salida.getName());
					if (!d.exists())
						d.mkdirs();
				} else {
					FileOutputStream fos = new FileOutputStream(directorioZip + salida.getName());
					int leer;
					byte[] buffer = new byte[1024];
					while (0 < (leer = zis.read(buffer))) {
						fos.write(buffer, 0, leer);
					}
					fos.close();
				}
			}
		} catch (FileNotFoundException e) {
			throw new InstaladorException(e.getMessage(), e);
		} catch (IOException e) {
			throw new InstaladorException(e.getMessage(), e);
		} finally {
			try {
				if (zis != null) {
					zis.closeEntry();
				}
			} catch (IOException e) {
				throw new InstaladorException(e.getMessage(), e);
			}
		}

	}

	public void validacion() throws InstaladorException {
		descomprimirComponentes();
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
