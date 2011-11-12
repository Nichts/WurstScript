package de.peeeq.parseq;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

import de.peeeq.parseq.ast.Program;
import de.peeeq.parseq.parser.ParseqParser;
import de.peeeq.parseq.parser.ParseqScanner;

public class Main {

	/**
	 * @param args
	 * @throws FileNotFoundException 
	 */
	public static void main(String[] args) throws FileNotFoundException {
		if (args.length != 2) {
			System.out.println("2 parameters required.");
			return;
		}
		String inputFile = args[0];
		String outputFolder = args[1];
		
		ParseqScanner scanner = new ParseqScanner(new FileInputStream(inputFile));
		ParseqParser parser = new ParseqParser(scanner);
		Program prog = parser.parse();
		Generator gen = new Generator(prog, outputFolder);
		gen.generate();
	}

}