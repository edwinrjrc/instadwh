/**
 * 
 */
package pe.com.mibanco.instalador.ejecucion;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import pe.com.mibanco.instalador.excepcion.InstaladorException;

/**
 * @author Edwin
 *
 */
public class Principal {
	
	private static final Logger log = LogManager.getLogger(Principal.class);

	/**
	 * -nc 17231 -vi 2 -vr 3 -ap C:\parametros.properties
	 * @param args
	 */
	public static void main(String[] args) {
		/*try {
			String numcaso = "";
			String verinsta = "";
			String verrever = "";
			String fileprop = null;
			String ambiente = "";
			String acciones = "";
			
			int param = 0;
			
			if (args.length == 8) {
				while (args.length > param) {
					if (Constantes.PARAM_NC.equals(args[param])) {
						numcaso = args[param+1];
					}
					else if (Constantes.PARAM_VI.equals(args[param])) {
						verinsta = args[param+1];
					}
					else if (Constantes.PARAM_VR.equals(args[param])) {
						verrever = args[param+1];
					}
					else if (Constantes.PARAM_AP.equals(args[param])) {
						fileprop = args[param+1];
					}
					else if (Constantes.PARAM_AC.equals(args[param])) {
						acciones = args[param+1];
					}
					else if (Constantes.PARAM_AM.equals(args[param])) {
						ambiente = args[param+1];
					}
					param += 2;
				}
			}
			else {
				throw new InstaladorException("No se enviaron los parametros necesarios para ejecutar el instalador");
			}
			
			Instalador instalador = new Instalador(numcaso, verinsta, verrever, fileprop, ambiente, acciones);
			instalador.ejecutar();
		} catch (InstaladorException e) {
			log.error(e.getMessage(),e);
		}*/
		try {
			Instalador instalador = new Instalador();
		} catch (InstaladorException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
