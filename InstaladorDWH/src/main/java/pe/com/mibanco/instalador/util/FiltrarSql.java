/**
 * 
 */
package pe.com.mibanco.instalador.util;

import java.io.File;
import java.io.FilenameFilter;

import org.apache.commons.lang3.StringUtils;

/**
 * @author Edwin
 *
 */
public class FiltrarSql implements FilenameFilter {

	public FiltrarSql() {
		// TODO Auto-generated constructor stub
	}
	
	@Override
	public boolean accept(File dir, String name) {
		
		return StringUtils.contains(name, ".sql");
	}

}
