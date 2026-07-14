package application;

import javax.swing.*;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import model.ast.ProgramNode;
import model.entities.Lexer;
import model.entities.Parser;
import model.entities.SemanticAnalyzer;

public class CompilerInterface extends JFrame {

	private JTextPane editorPane;
	private JTextArea lineArea;
	private JTextArea consoleArea;

	private File currentFile = null;

	// Flags para evitar loops infinitos nos listeners
	private boolean isHighlighting = false;
	private boolean isUpdatingCaret = false;

	// Highlighter customizado
	private final Highlighter.HighlightPainter currentLinePainter = new DefaultHighlighter.DefaultHighlightPainter(
			new Color(234, 234, 234));

	private final Highlighter.HighlightPainter errorLinePainter = new DefaultHighlighter.DefaultHighlightPainter(
			new Color(255, 120, 120));

	private Object currentLineHighlight;
	private Object errorLineHighlight;
	private List<Object> errorLineHighlights = new ArrayList<>();

	// Lista de palavras-chave
	private static final Set<String> KEYWORDS = new HashSet<>(List.of("break", "default", "func", "interface", "select",
			"case", "defer", "go", "map", "struct", "chan", "else", "goto", "package", "switch", "const", "fallthrough",
			"if", "range", "type", "continue", "for", "import", "return", "var", "true", "false", "nil"));

	public CompilerInterface() {

		setTitle("JGO - Go Compiler");
		setSize(950, 700);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setLocationRelativeTo(null);

		// Configuração do editor
		editorPane = new JTextPane();
		editorPane.setFont(new Font("Consolas", Font.PLAIN, 14));
		editorPane.setEditorKit(new StyledEditorKit());

		// Configuração da área de linhas
		lineArea = new JTextArea("1");
		lineArea.setFont(new Font("Consolas", Font.PLAIN, 14));
		lineArea.setBackground(new Color(245, 245, 245));
		lineArea.setForeground(Color.GRAY);
		lineArea.setEditable(false);
		lineArea.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Color.LIGHT_GRAY));

		// Painel principal do editor
		JPanel fullEditorPanel = new JPanel(new BorderLayout());
		fullEditorPanel.add(lineArea, BorderLayout.WEST);
		fullEditorPanel.add(editorPane, BorderLayout.CENTER);

		JScrollPane editorScroll = new JScrollPane(fullEditorPanel);

		// Consola
		consoleArea = new JTextArea();
		consoleArea.setFont(new Font("Consolas", Font.PLAIN, 12));
		consoleArea.setBackground(new Color(30, 30, 30));
		consoleArea.setForeground(Color.GREEN);
		consoleArea.setEditable(false);

		JScrollPane consoleScroll = new JScrollPane(consoleArea);

		// Split principal
		JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editorScroll, consoleScroll);

		splitPane.setDividerLocation(420);

		add(splitPane, BorderLayout.CENTER);

		// Painel de botões
		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

		JButton openButton = new JButton("Abrir Fiehceiro");
		JButton saveButton = new JButton("Guardar");
		JButton compileButton = new JButton("Analizar");

		buttonPanel.add(openButton);
		buttonPanel.add(saveButton);
		buttonPanel.add(compileButton);

		add(buttonPanel, BorderLayout.NORTH);

		// Listener de documento
		editorPane.getDocument().addDocumentListener(new DocumentListener() {

			@Override
			public void insertUpdate(DocumentEvent e) {
				updateInterface();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				updateInterface();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {

			}
		});

		// Listener do cursor
		editorPane.addCaretListener(new CaretListener() {

			@Override
			public void caretUpdate(CaretEvent e) {

				if (isUpdatingCaret || isHighlighting) {
					return;
				}

				SwingUtilities.invokeLater(() -> highlightCurrentLine());
			}
		});

		// Eventos dos botões
		openButton.addActionListener(e -> openFile());
		saveButton.addActionListener(e -> saveFile());
		compileButton.addActionListener(e -> executeCompiler());

		// Redireciona saída para consola
		redirectConsole();
	}

	private void updateInterface() {

		SwingUtilities.invokeLater(() -> {

			updateLineNumbers();
			applySyntaxHighlight();
		});
	}

	private void updateLineNumbers() {

		String text = editorPane.getText();

		int totalLines = text.split("\\r?\\n", -1).length;

		StringBuilder builder = new StringBuilder();

		for (int i = 1; i <= totalLines; i++) {
			builder.append(i).append("\n");
		}

		lineArea.setText(builder.toString());
	}

	private void applySyntaxHighlight() {

		if (isHighlighting) {
			return;
		}

		isHighlighting = true;

		StyledDocument document = editorPane.getStyledDocument();

		String text = editorPane.getText();

		// Estilo padrão
		Style defaultStyle = editorPane.addStyle("Default", null);

		StyleConstants.setForeground(defaultStyle, Color.BLACK);
		StyleConstants.setBold(defaultStyle, false);

		// Estilo keyword
		Style keywordStyle = editorPane.addStyle("Keyword", null);

		StyleConstants.setForeground(keywordStyle, new Color(0, 0, 180));
		StyleConstants.setBold(keywordStyle, true);

		// Estilo string
		Style stringStyle = editorPane.addStyle("String", null);

		StyleConstants.setForeground(stringStyle, new Color(160, 30, 30));

		// Reseta tudo
		document.setCharacterAttributes(0, text.length(), defaultStyle, true);

		// Destaca keywords
		Pattern keywordPattern = Pattern.compile("\\b[a-zA-Z_][a-zA-Z0-9_]*\\b");

		Matcher keywordMatcher = keywordPattern.matcher(text);

		while (keywordMatcher.find()) {

			String word = keywordMatcher.group();

			if (KEYWORDS.contains(word)) {

				document.setCharacterAttributes(keywordMatcher.start(), word.length(), keywordStyle, false);
			}
		}

		// Destaca strings
		Pattern stringPattern = Pattern.compile("\"[^\"]*\"");

		Matcher stringMatcher = stringPattern.matcher(text);

		while (stringMatcher.find()) {

			document.setCharacterAttributes(stringMatcher.start(), stringMatcher.group().length(), stringStyle, false);
		}

		isHighlighting = false;

		// Reaplica highlight da linha atual
		highlightCurrentLine();
	}

	/**
	 * Destaca a linha atual inteira
	 */
	private void highlightCurrentLine() {

		if (isUpdatingCaret) {
			return;
		}

		isUpdatingCaret = true;

		try {

			Highlighter highlighter = editorPane.getHighlighter();

			// Remove highlight anterior
			if (currentLineHighlight != null) {
				highlighter.removeHighlight(currentLineHighlight);
			}

			int caretPosition = editorPane.getCaretPosition();

			int lineStart = Utilities.getRowStart(editorPane, caretPosition);
			int lineEnd = Utilities.getRowEnd(editorPane, caretPosition);

			// Expande highlight até ao final visual da linha
			currentLineHighlight = highlighter.addHighlight(lineStart, lineEnd + 1, currentLinePainter);

		} catch (Exception ex) {

			ex.printStackTrace();
		}

		isUpdatingCaret = false;
	}

	public void highlightError(int line) {

		clearErrorHighlights();

		try {

			Highlighter highlighter = editorPane.getHighlighter();

			// Remove highlight anterior de erro
			if (errorLineHighlight != null) {

				highlighter.removeHighlight(errorLineHighlight);
				errorLineHighlight = null;
			}

			// Se não existir erro, termina aqui
			if (line <= 0) {
				return;
			}

			Element root = editorPane.getDocument().getDefaultRootElement();

			if (line > root.getElementCount()) {
				return;
			}

			Element lineElement = root.getElement(line - 1);

			int startOffset = lineElement.getStartOffset();
			int endOffset = lineElement.getEndOffset();

			errorLineHighlight = highlighter.addHighlight(startOffset, endOffset, errorLinePainter);

		} catch (Exception ex) {

			ex.printStackTrace();
		}
	}

	private void clearErrorHighlights() {

		try {

			Highlighter highlighter = editorPane.getHighlighter();

			if (errorLineHighlight != null) {
				highlighter.removeHighlight(errorLineHighlight);
				errorLineHighlight = null;
			}

			for (Object highlight : errorLineHighlights) {
				highlighter.removeHighlight(highlight);
			}

			errorLineHighlights.clear();

		} catch (Exception ex) {

			ex.printStackTrace();
		}
	}

	public void highlightErrors(List<Integer> lines) {

		clearErrorHighlights();

		try {

			Element root = editorPane.getDocument().getDefaultRootElement();
			Highlighter highlighter = editorPane.getHighlighter();

			for (int line : lines) {

				if (line <= 0 || line > root.getElementCount()) {
					continue;
				}

				Element lineElement = root.getElement(line - 1);

				int startOffset = lineElement.getStartOffset();
				int endOffset = lineElement.getEndOffset();

				Object highlight = highlighter.addHighlight(startOffset, endOffset, errorLinePainter);
				errorLineHighlights.add(highlight);
			}

		} catch (Exception ex) {

			ex.printStackTrace();
		}
	}

	private void openFile() {

		JFileChooser fileChooser = new JFileChooser();

		if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {

			currentFile = fileChooser.getSelectedFile();

			try {

				String content = Files.readString(currentFile.toPath(), StandardCharsets.UTF_8);

				editorPane.setText(content);

				consoleArea.setText("Ficheiro Carregado: " + currentFile.getName() + "\n");

				updateInterface();

			} catch (IOException ex) {

				consoleArea.setText("Erro ao ler ficheiro: " + ex.getMessage());
			}
		}
	}

	private void saveFile() {

		if (currentFile == null) {

			JFileChooser fileChooser = new JFileChooser();

			if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {

				currentFile = fileChooser.getSelectedFile();

			} else {

				return;
			}
		}

		try {

			Files.writeString(currentFile.toPath(), editorPane.getText(), StandardCharsets.UTF_8);

			consoleArea.append("Ficheiro salvo com sucesso!\n");

		} catch (IOException ex) {

			consoleArea.append("Erro ao salvar ficheiro: " + ex.getMessage() + "\n");
		}
	}

	private void executeCompiler() {

		clearErrorHighlights();

		if (editorPane.getText().trim().isEmpty()) {

			consoleArea.setText("O editor está vazio.");

			return;
		}

		consoleArea.setText("");

		try {

			List<String> sourceCode = List.of(editorPane.getText().split("\\r?\\n"));

			System.out.println("### INICIANDO ANÁLISE LÉXICA ###");

			Lexer lexer = new Lexer(sourceCode);

			lexer.analex();

			System.out.println("\n### INICIANDO ANÁLISE SINTÁTICA ###");

			Parser parser = new Parser(lexer.getTokens());

			ProgramNode program = parser.parse();

			System.out.println("\n### INICIANDO ANÁLISE SEMÂNTICA ###");

			SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer(program);

			semanticAnalyzer.analyze();

			List<Integer> errorLines = new ArrayList<>(parser.getErrorLines());
			errorLines.addAll(semanticAnalyzer.getErrorLines());
			highlightErrors(errorLines);

		} catch (Exception ex) {

			System.err.println("Erro crítico na execução: " + ex.getMessage());
		}
	}

	private void redirectConsole() {

		// Buffer para suportar UTF-8 corretamente
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();

		OutputStream outputStream = new OutputStream() {

			@Override
			public void write(int b) {

				buffer.write(b);

				if (b == '\n') {

					consoleArea.append(buffer.toString(StandardCharsets.UTF_8));

					buffer.reset();

					consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
				}
			}
		};

		System.setOut(new PrintStream(outputStream, true, StandardCharsets.UTF_8));

		System.setErr(new PrintStream(outputStream, true, StandardCharsets.UTF_8));
	}

	public static void main(String[] args) {

		SwingUtilities.invokeLater(() -> new CompilerInterface().setVisible(true));
	}
}
