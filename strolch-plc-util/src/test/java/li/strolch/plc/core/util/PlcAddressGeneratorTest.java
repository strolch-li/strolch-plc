package li.strolch.plc.core.util;

import org.junit.Test;

import java.io.IOException;

public class PlcAddressGeneratorTest {

	@Test
	public void shouldGenerate() throws IOException {

		String templatesFile = "../example/data/plc-templates.xml";
		String importFile = "../example/data/strolch-plc-example.csv";
		String exportFile = "../example/data/strolch-plc-example.xml";

		PlcAddressGenerator.main(new String[]{templatesFile, importFile, exportFile});
	}
}
