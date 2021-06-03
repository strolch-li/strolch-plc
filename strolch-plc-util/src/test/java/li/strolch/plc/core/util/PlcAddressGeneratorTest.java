package li.strolch.plc.core.util;

import java.io.IOException;

import org.junit.Test;

public class PlcAddressGeneratorTest {

	@Test
	public void shouldGenerate() throws IOException {

		String templatesFile = "../example/plc-templates.xml";
		String importFile = "../example/strolch-plc-example.csv";
		String exportFile = "../example/strolch-plc-example.xml";

		PlcAddressGenerator.main(new String[] { templatesFile, importFile, exportFile });
	}
}
