/**
 * 
 */
package pe.com.mibanco.instalador.ejecucion;

import java.util.Properties;
import java.util.ResourceBundle;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import pe.com.mibanco.instalador.excepcion.InstaladorException;

/**
 * @author Edwin
 *
 */
public class Instalador {

	Session session = null;

	public void ejecutar() throws InstaladorException {
		conectarConServidor();
		
	}
}
