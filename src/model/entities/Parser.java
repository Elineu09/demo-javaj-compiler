package model.entities;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import model.enums.TokenType;
import model.exceptions.SyntaxException;

public class Parser {

    private List<Token> tokens;
    private int currentTokenIndex;
    private Token currentToken;
    
    private Map<String, Symbol> symbolTable;
    private int currentScopeLevel;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
        this.currentTokenIndex = 0;
        this.symbolTable = new HashMap<>();
        this.currentScopeLevel = 0;
        
        if (!tokens.isEmpty()) {
            this.currentToken = tokens.get(0);
        }
    }

    // ─── Funções Auxiliares ─────────────────────────────────────────────────

    private void advance() {
        currentTokenIndex++;
        if (currentTokenIndex < tokens.size()) {
            currentToken = tokens.get(currentTokenIndex);
        } else {
            currentToken = tokens.get(tokens.size() - 1); 
        }
    }

    private Token match(TokenType expectedType) {
        if (currentToken.getType() == expectedType) {
            Token consumedToken = currentToken;
            advance();
            return consumedToken;
        } else {
            throw new SyntaxException(String.format("Erro Sintático [Linha %d]. Esperado %s, mas encontrado %s ('%s').", 
                currentToken.toString().substring(1, 3), expectedType, currentToken.getType(), currentToken.getValue()));
        }
    }

    private void addSymbol(String name, String type, String category, int scopeLevel, boolean isInitialized, String assignedValue) {
        if (symbolTable.containsKey(name) && symbolTable.get(name).getScopeLevel() == currentScopeLevel) {
            throw new SyntaxException("Erro Semântico Inicial: O identificador '" + name + "' já foi declarado neste escopo.");
        }
        Symbol sym = new Symbol(name, type, category, scopeLevel, isInitialized, assignedValue);
        symbolTable.put(name, sym);
        System.out.println("Tabela de Símbolos -> Adicionado: " + sym);
    }

    // ─── Início da Análise top-down ─────────────────────────────────────────

    public void parse() {
        try {
            parseProgram();
            System.out.println("\nAnálise Sintática concluída com sucesso!");
        } catch (SyntaxException e) {
            System.err.println(e.getMessage());
        }
    }

    private void parseProgram() {
        match(TokenType.PACKAGE);
        match(TokenType.IDENTIFIER);
        
        parseImports();
        parseTopLevelDeclarations();
        
        match(TokenType.EOF);
    }

    private void parseImports() {
        while (currentToken.getType() == TokenType.IMPORT) {
            advance();
            if (currentToken.getType() == TokenType.LPAREN) {
                advance();
                while (currentToken.getType() == TokenType.STRING_LITERAL) {
                    advance();
                }
                match(TokenType.RPAREN);
            } else {
                match(TokenType.STRING_LITERAL);
            }
        }
    }

    // ─── Declarações de Topo (Var, Const, Type, Func) ───────────────────────

    private void parseTopLevelDeclarations() {
        while (currentToken.getType() != TokenType.EOF) {
            TokenType type = currentToken.getType();
            if (type == TokenType.VAR || type == TokenType.CONST) {
                parseVarOrConstDeclaration();
            } else if (type == TokenType.TYPE) {
                parseTypeDeclaration();
            } else if (type == TokenType.FUNC) {
                parseFuncDeclaration();
            } else {
                throw new SyntaxException("Declaração de topo inválida: " + currentToken.getValue());
            }
        }
    }

    // Lida com 'var x int' ou 'const y = 10'
    private void parseVarOrConstDeclaration() {
        boolean isConst = currentToken.getType() == TokenType.CONST;
        advance(); // Consome var ou const
        
        Token idToken = match(TokenType.IDENTIFIER);
        String typeStr = "inferido";
        String assignedVal = null; // Initialize assignedValue

        // Se não for um '=', assume-se que vem a declaração do tipo (ex: int, *float64)
        if (currentToken.getType() != TokenType.ASSIGN) {
            typeStr = parseType();
        }

        boolean isInitialized = false; // Assume not initialized by default
        if (currentToken.getType() == TokenType.ASSIGN) {
            isInitialized = true; // Mark as initialized
            advance();
            
            // Capture the tokens of the expression to reconstruct the assigned value string
            int exprStartIndex = currentTokenIndex;
            parseExpression();
            int exprEndIndex = currentTokenIndex; // currentTokenIndex is now past the expression

            StringBuilder exprBuilder = new StringBuilder();
            // Iterate from the start of the expression to the end (exclusive of currentTokenIndex)
            for (int i = exprStartIndex; i < exprEndIndex; i++) {
                if (i < tokens.size()) { 
                    exprBuilder.append(tokens.get(i).getValue());
                    // Add space only if it's not the last token and not a specific punctuation
                    if (i < exprEndIndex - 1 && 
                        !isPunctuation(tokens.get(i).getType()) && 
                        !isPunctuation(tokens.get(i+1).getType())) {
                        exprBuilder.append(" ");
                    }
                }
            }
            assignedVal = exprBuilder.toString().trim();
        }
        
        // Update the addSymbol call
        addSymbol(idToken.getValue(), typeStr, isConst ? "CONST" : "VAR", currentScopeLevel, isInitialized, assignedVal);
    }

    // Helper to identify punctuation for better string reconstruction in assignedValue
    private boolean isPunctuation(TokenType type) {
        return type == TokenType.DOT || type == TokenType.COMMA || type == TokenType.SEMICOLON ||
               type == TokenType.LPAREN || type == TokenType.RPAREN || 
               type == TokenType.LBRACKET || type == TokenType.RBRACKET ||
               type == TokenType.LBRACE || type == TokenType.RBRACE ||
               type == TokenType.COLON || type == TokenType.ASSIGN ||
               type == TokenType.DEFINE || type == TokenType.PLUS_ASSIGN ||
               type == TokenType.MINUS || type == TokenType.PLUS ||
               type == TokenType.MULTIPLY || type == TokenType.DIVIDE || type == TokenType.MOD ||
               type == TokenType.EQUAL || type == TokenType.NOT_EQUAL ||
               type == TokenType.GREATER || type == TokenType.GREATER_EQUAL ||
               type == TokenType.LESS || type == TokenType.LESS_EQUAL ||
               type == TokenType.LOGICAL_AND || type == TokenType.LOGICAL_OR ||
               type == TokenType.LOGICAL_NOT || type == TokenType.BITWISE_AND ||
               type == TokenType.ARROW || type == TokenType.INCREMENT || type == TokenType.DECREMENT;
    }

}


    // Lida com 'type Pessoa struct { ... }' e 'type IPessoa interface { ... }'
    private void parseTypeDeclaration() {
        match(TokenType.TYPE);
        Token idToken = match(TokenType.IDENTIFIER);
        
        if (currentToken.getType() == TokenType.STRUCT) {
            advance();
            match(TokenType.LBRACE);
            while (currentToken.getType() == TokenType.IDENTIFIER) {
                advance(); // Nome do campo
                parseType(); // Tipo do campo
            }
            match(TokenType.RBRACE);
            addSymbol(idToken.getValue(), "struct", "TYPE", currentScopeLevel, false, null);
        } else if (currentToken.getType() == TokenType.INTERFACE) {
            advance();
            match(TokenType.LBRACE);
            while (currentToken.getType() == TokenType.IDENTIFIER) {
                advance(); // Nome do método
                match(TokenType.LPAREN);
                parseOptionalParameters();
                match(TokenType.RPAREN);
                if (currentToken.getType() != TokenType.RBRACE) {
                    parseFuncReturnType();
                }
            }
            match(TokenType.RBRACE);
            addSymbol(idToken.getValue(), "interface", "TYPE", currentScopeLevel, false, null);
        } else {
            // Alias de tipo (ex: type MeuInt int)
            parseType();
            addSymbol(idToken.getValue(), "alias", "TYPE", currentScopeLevel, false, null);
        }
    }

    // ─── Funções e Métodos ──────────────────────────────────────────────────

    private void parseFuncDeclaration() {
        match(TokenType.FUNC);
        
        // Suporte para funções receptoras (Methods): func (p *Pessoa) Falar()
        if (currentToken.getType() == TokenType.LPAREN) {
            advance();
            match(TokenType.IDENTIFIER);
            parseType();
            match(TokenType.RPAREN);
        }
        
        Token idToken = match(TokenType.IDENTIFIER);
        addSymbol(idToken.getValue(), "func", "FUNC");
        
        match(TokenType.LPAREN);
        parseOptionalParameters();
        match(TokenType.RPAREN);
        
        // Retorno (Pode ser simples ou múltiplo)
        if (currentToken.getType() != TokenType.LBRACE) {
            parseFuncReturnType();
        }
        
        parseBlock();
    }

    private void parseOptionalParameters() {
        if (currentToken.getType() == TokenType.IDENTIFIER) {
            advance();
            parseType(); // Tipo do parâmetro
            
            while (currentToken.getType() == TokenType.COMMA) {
                advance();
                match(TokenType.IDENTIFIER);
                parseType();
            }
        }
    }

    private void parseFuncReturnType() {
        // Múltiplos retornos: (int, error)
        if (currentToken.getType() == TokenType.LPAREN) {
            advance();
            parseType(); // Aqui assumimos simplificação (tipo1, tipo2)
            while (currentToken.getType() == TokenType.COMMA) {
                advance();
                parseType();
            }
            match(TokenType.RPAREN);
        } else {
            // Retorno único
            parseType();
        }
    }

    // ─── Análise de Tipos de Dados (Complexos e Primitivos) ─────────────────

    private String parseType() {
        if (currentToken.getType() == TokenType.MULTIPLY) {
            // Ponteiro: *int
            advance();
            return "*" + parseType();
        } else if (currentToken.getType() == TokenType.LBRACKET) {
            advance();
            if (currentToken.getType() == TokenType.RBRACKET) {
                // Slice: []int
                advance();
                return "slice_" + parseType();
            } else {
                // Array: [5]int
                parseExpression();
                match(TokenType.RBRACKET);
                return "array_" + parseType();
            }
        } else if (currentToken.getType() == TokenType.MAP) {
            // Map: map[string]int
            advance();
            match(TokenType.LBRACKET);
            parseType(); // Key type
            match(TokenType.RBRACKET);
            return "map_" + parseType(); // Value type
        } else if (currentToken.getType() == TokenType.CHAN) {
            // Channel: chan int
            advance();
            return "chan_" + parseType();
        } else {
            // Tipo primitivo ou customizado (int, float64, string, bool, Pessoa)
            Token typeToken = match(TokenType.IDENTIFIER);
            return typeToken.getValue();
        }
    }

    // ─── Blocos e Comandos ──────────────────────────────────────────────────

    private void parseBlock() {
        match(TokenType.LBRACE);
        currentScopeLevel++;
        
        while (currentToken.getType() != TokenType.RBRACE && currentToken.getType() != TokenType.EOF) {
            parseStatement();
            if (currentToken.getType() == TokenType.SEMICOLON) advance();
        }
        
        currentScopeLevel--;
        match(TokenType.RBRACE);
    }

    private void parseStatement() {
        TokenType type = currentToken.getType();

        if (type == TokenType.VAR || type == TokenType.CONST) {
            parseVarOrConstDeclaration();
        } else if (type == TokenType.IF) {
            parseIfStatement();
        } else if (type == TokenType.FOR) {
            parseForStatement();
        } else if (type == TokenType.SWITCH) {
            parseSwitchStatement();
        } else if (type == TokenType.RETURN) {
            advance();
            if (currentToken.getType() != TokenType.RBRACE && currentToken.getType() != TokenType.SEMICOLON) {
                parseExpression();
                // Suporte simplificado a múltiplos retornos no return
                while(currentToken.getType() == TokenType.COMMA) {
                    advance(); parseExpression();
                }
            }
        } else if (type == TokenType.BREAK || type == TokenType.CONTINUE || type == TokenType.FALLTHROUGH) {
            advance();
        } else if (type == TokenType.GO || type == TokenType.DEFER) {
            // Goroutines e Defer
            advance();
            parseExpression(); // Exige que seja uma chamada de função
        } else if (type == TokenType.IDENTIFIER || type == TokenType.MULTIPLY) {
            // Factorização à esquerda: Pode ser uma variável, um ponteiro (*p = 5), atribuição, incremento, func call...
            parseExpression();
            
            if (currentToken.getType() == TokenType.ASSIGN || currentToken.getType() == TokenType.DEFINE || currentToken.getType() == TokenType.PLUS_ASSIGN) {
                advance();
                parseExpression();
            } else if (currentToken.getType() == TokenType.INCREMENT || currentToken.getType() == TokenType.DECREMENT) {
                advance();
            }
        } else {
            parseExpression();
        }
    }

    private void parseIfStatement() {
        match(TokenType.IF);
        parseExpression();
        parseBlock();
        
        if (currentToken.getType() == TokenType.ELSE) {
            advance();
            if (currentToken.getType() == TokenType.IF) {
                parseIfStatement();
            } else {
                parseBlock();
            }
        }
    }

    private void parseSwitchStatement() {
        match(TokenType.SWITCH);
        if (currentToken.getType() != TokenType.LBRACE) {
            parseExpression(); // Condição opcional
        }
        match(TokenType.LBRACE);
        
        while (currentToken.getType() == TokenType.CASE || currentToken.getType() == TokenType.DEFAULT) {
            if (currentToken.getType() == TokenType.CASE) {
                advance();
                parseExpression();
                match(TokenType.COLON);
            } else {
                advance();
                match(TokenType.COLON);
            }
            
            // Comandos do case
            while (currentToken.getType() != TokenType.CASE && currentToken.getType() != TokenType.DEFAULT && currentToken.getType() != TokenType.RBRACE) {
                parseStatement();
            }
        }
        match(TokenType.RBRACE);
    }

    private void parseForStatement() {
        match(TokenType.FOR);
        
        if (currentToken.getType() == TokenType.LBRACE) {
            // Infinite loop: for { ... }
            parseBlock();
            return;
        }

        // Tentar resolver Range vs Clássico vs While (fatorização no lookahead)
        parseExpression();

        if (currentToken.getType() == TokenType.LBRACE) {
            // While loop: for x < 10 { ... }
            parseBlock();
        } else if (currentToken.getType() == TokenType.DEFINE && tokens.get(currentTokenIndex + 1).getType() == TokenType.RANGE) {
            // Range loop simplificado (ex: for k, v := range map)
            advance(); // :=
            match(TokenType.RANGE);
            parseExpression();
            parseBlock();
        } else if (currentToken.getType() == TokenType.SEMICOLON) {
            // Classic for loop: for i:=0; i<10; i++
            advance();
            parseExpression(); // Condição
            match(TokenType.SEMICOLON);
            parseStatement(); // Update (i++)
            parseBlock();
        } else if (currentToken.getType() == TokenType.RANGE) {
            // Single range loop (ex: for k := range map or for range map)
            advance(); // range
            parseExpression();
            parseBlock();
        } else {
            // If none of the above ForTail options matched after an Expression, it's a syntax error.
            throw new SyntaxException(String.format("Erro Sintático [Linha %d]. Declaração 'for' inválida ou faltando bloco: '%s'.",
                currentToken.toString().substring(1, currentToken.toString().length() - 1).split(",")[0].trim(), currentToken.getValue()));
        }
    }

    // ─── Expressões (Descendente Recursivo) ─────────────────────────────────

    private void parseExpression() {
        parseLogicalOr();
    }

    private void parseLogicalOr() {
        parseLogicalAnd();
        while (currentToken.getType() == TokenType.LOGICAL_OR) {
            advance(); parseLogicalAnd();
        }
    }

    private void parseLogicalAnd() {
        parseRelational();
        while (currentToken.getType() == TokenType.LOGICAL_AND) {
            advance(); parseRelational();
        }
    }

    private void parseRelational() {
        parseSum();
        while (isRelationalOperator(currentToken.getType())) {
            advance(); parseSum();
        }
    }

    private void parseSum() {
        parseMult();
        while (currentToken.getType() == TokenType.PLUS || currentToken.getType() == TokenType.MINUS) {
            advance(); parseMult();
        }
    }

    private void parseMult() {
        parseUnary();
        while (currentToken.getType() == TokenType.MULTIPLY || currentToken.getType() == TokenType.DIVIDE || currentToken.getType() == TokenType.MOD) {
            advance(); parseUnary();
        }
    }

    private void parseUnary() {
        // Unários do Go: -, !, &, *, <- (receção de canal)
        TokenType type = currentToken.getType();
        if (type == TokenType.MINUS || type == TokenType.LOGICAL_NOT || 
            type == TokenType.BITWISE_AND || type == TokenType.MULTIPLY || type == TokenType.ARROW) {
            advance();
            parseUnary();
        } else {
            parseFactor();
        }
    }

    private void parseFactor() {
        TokenType type = currentToken.getType();

        if (isLiteral(type)) {
            advance();
        } else if (type == TokenType.LPAREN) {
            advance();
            parseExpression();
            match(TokenType.RPAREN);
        } else if (type == TokenType.IDENTIFIER) {
            advance();
            
            // Tratamento de encadeamentos: a.b()[0].c
            while (true) {
                if (currentToken.getType() == TokenType.DOT) {
                    advance();
                    match(TokenType.IDENTIFIER);
                } else if (currentToken.getType() == TokenType.LPAREN) {
                    advance();
                    parseOptionalArguments();
                    match(TokenType.RPAREN);
                } else if (currentToken.getType() == TokenType.LBRACKET) {
                    advance();
                    if (currentToken.getType() != TokenType.COLON) parseExpression();
                    if (currentToken.getType() == TokenType.COLON) advance(); // Slicing [1:3]
                    if (currentToken.getType() != TokenType.RBRACKET) parseExpression();
                    match(TokenType.RBRACKET);
                } else {
                    break;
                }
            }
        } else {
            throw new SyntaxException("Fator inesperado na expressão: " + currentToken.getValue());
        }
    }

    private void parseOptionalArguments() {
        if (currentToken.getType() != TokenType.RPAREN) {
            parseExpression();
            while (currentToken.getType() == TokenType.COMMA) {
                advance();
                parseExpression();
            }
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private boolean isRelationalOperator(TokenType type) {
        return type == TokenType.EQUAL || type == TokenType.NOT_EQUAL ||
               type == TokenType.GREATER || type == TokenType.GREATER_EQUAL ||
               type == TokenType.LESS || type == TokenType.LESS_EQUAL;
    }

    private boolean isLiteral(TokenType type) {
        return type == TokenType.INT_LITERAL || type == TokenType.FLOAT_LITERAL ||
               type == TokenType.STRING_LITERAL || type == TokenType.RAW_STRING_LITERAL ||
               type == TokenType.TRUE || type == TokenType.FALSE;
    }
}