package application;

import java.util.List;

import model.ast.ProgramNode;
import model.entities.Lexer;
import model.entities.Parser;
import model.entities.SemanticAnalyzer;
import util.FileReaderUtil;

public class Program {
    public static void main(String[] args) {
        String path = "C:/temp/compiladores/programa.go";

        System.out.println("### INICIANDO ANÁLISE LÉXICA ###");
        List<String> sourceCode = FileReaderUtil.readSourceFile(path);

        Lexer lexer = new Lexer(sourceCode);
        lexer.analex();

        System.out.println("### INICIANDO ANÁLISE SINTÁTICA ###");
        Parser parser = new Parser(lexer.getTokens());
        ProgramNode program = parser.parse();

        System.out.println("### INICIANDO ANÁLISE SEMÂNTICA ###");
        SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer(program);
        semanticAnalyzer.analyze();
    }
}