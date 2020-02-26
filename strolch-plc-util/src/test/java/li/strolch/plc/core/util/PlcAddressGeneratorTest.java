package li.strolch.plc.core.util;

import java.io.IOException;

import org.junit.Test;

public class PlcAddressGeneratorTest {

	@Test
	public void shouldGenerate() throws IOException {

		String templatesFile = "../example/Templates.xml";
		String importFile = "/home/eitch/src/git/mfs-soh/mfs-soh-plc/runtime/data/mfs-soh-plc.csv";
		String exportFile = "/home/eitch/src/git/mfs-soh/mfs-soh-plc/runtime/data/mfs-soh-plc-generated.xml";

		PlcAddressGenerator.main(new String[] { templatesFile, importFile, exportFile });
	}
}
