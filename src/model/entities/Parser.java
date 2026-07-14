package model.entities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import model.enums.TokenType;
import model.exceptions.SyntaxException;

public class Parser {

    private static final String TYPE_UNKNOWN = "desconhecido";
    private static final String TYPE_INFERRED = "inferido";
    private static final String TYPE_BOOL = "bool";
    private static final String TYPE_INT = "int";
    private static final String TYPE_FLOAT = "float64";
    private static final String TYPE_STRING = "string";
    private static final String TYPE_RUNE = "rune";
    private static final String TYPE_NIL = "nil";
    private static final String TYPE_VOID = "void";

    private int errorLine = 0;

    private List<String> errors;
    private List<Integer> errorLines;

    private List<Token> tokens;
    private int currentTokenIndex;
    private Token currentToken;

    private Map<String, Symbol> symbolTable;
    private int currentScopeLevel;
    private int nextMemoryAddress;
    private Set<String> importedPackages;

    private static class Parameter {
        private Token token;
        private String type;

        public Parameter(Token token, String type) {
            this.token = token;
            this.type = type;
        }
    }

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
        this.currentTokenIndex = 0;
        this.symbolTable = new HashMap<>();
        this.currentScopeLevel = 0;
        this.nextMemoryAddress = 0;
        this.importedPackages = new HashSet<>();
        this.errors = new ArrayList<>();
        this.errorLines = new ArrayList<>();

        if (!tokens.isEmpty()) {
            this.currentToken = tokens.get(0);
        }
    }

    private void advance() {
        currentTokenIndex++;
        if (currentTokenIndex < tokens.size()) {
            currentToken = tokens.get(currentTokenIndex);
        } else if (!tokens.isEmpty()) {
            currentToken = tokens.get(tokens.size() - 1);
        }
    }

    private TokenType peekType(int offset) {
        int index = currentTokenIndex + offset;
        if (index >= 0 && index < tokens.size()) {
            return tokens.get(index).getType();
        }
        return TokenType.EOF;
    }

    private SyntaxException syntaxError(String message) {
        return new SyntaxException(
                String.format("Erro Sintatico [Linha %d, Coluna %d]. %s Token encontrado: %s ('%s').",
                        currentToken.getLine(), currentToken.getColumn(), message, currentToken.getType(),
                        currentToken.getValue()));
    }

    private SyntaxException expected(TokenType expectedType) {
        return syntaxError("Esperado " + expectedType + ".");
    }

    private SyntaxException semanticError(Token token, String message) {
        return new SyntaxException(
                String.format("Erro Semantico [Linha %d, Coluna %d]. %s",
                        token.getLine(), token.getColumn(), message));
    }

    private void validateToken() {
        if (currentToken.getType() == TokenType.UNKNOWN) {
            throw syntaxError("Token invalido.");
        }
    }

    private Token match(TokenType expectedType) {
        validateToken();
        if (currentToken.getType() == expectedType) {
            Token consumedToken = currentToken;
            advance();
            return consumedToken;
        }
        throw expected(expectedType);
    }

    private void reportError(SyntaxException e) {
        errors.add(e.getMessage());

        int line = extractErrorLine(e.getMessage());
        errorLines.add(line);

        if (errorLine == 0) {
            errorLine = line;
        }
    }

    private int extractErrorLine(String message) {
        if (message == null) {
            return currentToken != null ? currentToken.getLine() : 0;
        }

        String marker = "[Linha ";
        int markerIndex = message.indexOf(marker);

        if (markerIndex >= 0) {
            int start = markerIndex + marker.length();
            int end = start;

            while (end < message.length() && Character.isDigit(message.charAt(end))) {
                end++;
            }

            if (end > start) {
                return Integer.parseInt(message.substring(start, end));
            }
        }

        return currentToken != null ? currentToken.getLine() : 0;
    }

    private String getSymbolKey(String name, int scopeLevel) {
        return name + "#" + scopeLevel;
    }

    private Symbol lookupSymbol(String name) {
        for (int level = currentScopeLevel; level >= 0; level--) {
            Symbol symbol = symbolTable.get(getSymbolKey(name, level));
            if (symbol != null) {
                return symbol;
            }
        }

        return null;
    }

    private boolean isDeclaredInCurrentScope(String name) {
        return symbolTable.containsKey(getSymbolKey(name, currentScopeLevel));
    }

    private Symbol addSymbol(Token idToken, String type, String category, int scopeLevel, boolean isInitialized,
            String assignedValue) {
        String name = idToken.getValue();

        if (symbolTable.containsKey(getSymbolKey(name, scopeLevel))) {
            throw semanticError(idToken,
                    String.format("O identificador '%s' ja foi declarado neste escopo.", name));
        }

        Symbol sym = new Symbol(name, normalizeType(type), category, scopeLevel, isInitialized, assignedValue);

        if ("VAR".equals(category) || "CONST".equals(category) || "PARAM".equals(category)) {
            sym.setMemoryAddress(nextMemoryAddress);
            sym.setSizeInBytes(getSizeInBytes(type));
            nextMemoryAddress += sym.getSizeInBytes();
        }

        symbolTable.put(getSymbolKey(name, scopeLevel), sym);
        System.out.println("Tabela de Simbolos -> Adicionado: " + sym);
        return sym;
    }

    private void removeSymbolsFromScope(int scopeLevel) {
        symbolTable.entrySet().removeIf(entry -> entry.getValue().getScopeLevel() == scopeLevel);
    }

    private int getSizeInBytes(String type) {
        String normalizedType = normalizeType(type);

        if (TYPE_BOOL.equals(normalizedType) || "byte".equals(normalizedType)) {
            return 1;
        }

        if ("int16".equals(normalizedType) || "uint16".equals(normalizedType)) {
            return 2;
        }

        if ("int32".equals(normalizedType) || "uint32".equals(normalizedType)
                || "float32".equals(normalizedType) || TYPE_RUNE.equals(normalizedType)) {
            return 4;
        }

        if (isNumericType(normalizedType) || normalizedType.startsWith("*")
                || TYPE_STRING.equals(normalizedType) || normalizedType.startsWith("slice_")
                || normalizedType.startsWith("array_") || normalizedType.startsWith("map_")
                || normalizedType.startsWith("chan_")) {
            return 8;
        }

        return 0;
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return TYPE_UNKNOWN;
        }

        if ("float".equals(type)) {
            return TYPE_FLOAT;
        }

        return type;
    }

    private boolean isPrimitiveType(String type) {
        String normalizedType = normalizeType(type);

        return TYPE_BOOL.equals(normalizedType)
                || TYPE_STRING.equals(normalizedType)
                || TYPE_RUNE.equals(normalizedType)
                || "byte".equals(normalizedType)
                || "error".equals(normalizedType)
                || isNumericType(normalizedType);
    }

    private boolean isNumericType(String type) {
        String normalizedType = normalizeType(type);

        return TYPE_INT.equals(normalizedType)
                || "int8".equals(normalizedType)
                || "int16".equals(normalizedType)
                || "int32".equals(normalizedType)
                || "int64".equals(normalizedType)
                || "uint".equals(normalizedType)
                || "uint8".equals(normalizedType)
                || "uint16".equals(normalizedType)
                || "uint32".equals(normalizedType)
                || "uint64".equals(normalizedType)
                || "uintptr".equals(normalizedType)
                || "float32".equals(normalizedType)
                || TYPE_FLOAT.equals(normalizedType);
    }

    private boolean isIntegerType(String type) {
        String normalizedType = normalizeType(type);

        return TYPE_INT.equals(normalizedType)
                || "int8".equals(normalizedType)
                || "int16".equals(normalizedType)
                || "int32".equals(normalizedType)
                || "int64".equals(normalizedType)
                || "uint".equals(normalizedType)
                || "uint8".equals(normalizedType)
                || "uint16".equals(normalizedType)
                || "uint32".equals(normalizedType)
                || "uint64".equals(normalizedType)
                || "uintptr".equals(normalizedType)
                || TYPE_RUNE.equals(normalizedType)
                || "byte".equals(normalizedType);
    }

    private boolean isNumericOrUnknown(String type) {
        String normalizedType = normalizeType(type);
        return TYPE_UNKNOWN.equals(normalizedType) || isNumericType(normalizedType);
    }

    private boolean isIntegerOrUnknown(String type) {
        String normalizedType = normalizeType(type);
        return TYPE_UNKNOWN.equals(normalizedType) || isIntegerType(normalizedType);
    }

    private boolean isKnownType(String type) {
        String normalizedType = normalizeType(type);

        if (TYPE_UNKNOWN.equals(normalizedType) || TYPE_INFERRED.equals(normalizedType)
                || TYPE_NIL.equals(normalizedType) || TYPE_VOID.equals(normalizedType)) {
            return true;
        }

        if (isPrimitiveType(normalizedType)) {
            return true;
        }

        if (normalizedType.startsWith("*")) {
            return isKnownType(normalizedType.substring(1));
        }

        if (normalizedType.startsWith("slice_")) {
            return isKnownType(normalizedType.substring("slice_".length()));
        }

        if (normalizedType.startsWith("array_")) {
            return isKnownType(normalizedType.substring("array_".length()));
        }

        if (normalizedType.startsWith("map_")) {
            return isKnownType(normalizedType.substring("map_".length()));
        }

        if (normalizedType.startsWith("chan_")) {
            return isKnownType(normalizedType.substring("chan_".length()));
        }

        Symbol symbol = lookupSymbol(normalizedType);
        return symbol != null && "TYPE".equals(symbol.getCategory());
    }

    private boolean areTypesCompatible(String expectedType, String actualType) {
        String expected = normalizeType(expectedType);
        String actual = normalizeType(actualType);

        if (TYPE_UNKNOWN.equals(expected) || TYPE_UNKNOWN.equals(actual)
                || TYPE_INFERRED.equals(expected) || TYPE_INFERRED.equals(actual)) {
            return true;
        }

        if (expected.equals(actual)) {
            return true;
        }

        if (TYPE_NIL.equals(actual) && (expected.startsWith("*")
                || expected.startsWith("slice_") || expected.startsWith("map_")
                || expected.startsWith("chan_") || "interface".equals(expected))) {
            return true;
        }

        return (("float32".equals(expected) || TYPE_FLOAT.equals(expected)) && isIntegerType(actual));
    }

    private void validateAssignmentTypes(Token token, String expectedType, String actualType) {
        if (!areTypesCompatible(expectedType, actualType)) {
            throw semanticError(token,
                    String.format("Tipo incompativel: esperado '%s', recebido '%s'.",
                            normalizeType(expectedType), normalizeType(actualType)));
        }
    }

    private String combineNumericTypes(Token operatorToken, String leftType, String rightType) {
        if (!isNumericOrUnknown(leftType) || !isNumericOrUnknown(rightType)) {
            throw semanticError(operatorToken,
                    String.format("Operador '%s' exige operandos numericos.", operatorToken.getValue()));
        }

        if (TYPE_UNKNOWN.equals(normalizeType(leftType))) {
            return normalizeType(rightType);
        }
        if (TYPE_UNKNOWN.equals(normalizeType(rightType))) {
            return normalizeType(leftType);
        }

        if (TYPE_FLOAT.equals(normalizeType(leftType)) || TYPE_FLOAT.equals(normalizeType(rightType))
                || "float32".equals(normalizeType(leftType)) || "float32".equals(normalizeType(rightType))) {
            return TYPE_FLOAT;
        }

        return normalizeType(leftType);
    }

    private void validateBoolExpression(Token token, String expressionType, String context) {
        if (!TYPE_BOOL.equals(normalizeType(expressionType)) && !TYPE_UNKNOWN.equals(normalizeType(expressionType))) {
            reportError(semanticError(token,
                    String.format("A expressao do %s deve ser do tipo bool, mas recebeu '%s'.",
                            context, normalizeType(expressionType))));
        }
    }

    private void validateCaseType(Token token, String switchType, String caseType) {
        if (!areTypesCompatible(switchType, caseType) && !areTypesCompatible(caseType, switchType)) {
            reportError(semanticError(token,
                    String.format("Tipo do case incompativel: esperado '%s', recebido '%s'.",
                            normalizeType(switchType), normalizeType(caseType))));
        }
    }

    private void validateRangeableExpression(Token token, String expressionType) {
        String normalizedType = normalizeType(expressionType);
        boolean isIterable = TYPE_STRING.equals(normalizedType)
                || normalizedType.startsWith("slice_") || normalizedType.startsWith("array_")
                || normalizedType.startsWith("map_") || normalizedType.startsWith("chan_")
                || TYPE_UNKNOWN.equals(normalizedType) || TYPE_INFERRED.equals(normalizedType);

        if (!isIterable) {
            reportError(semanticError(token,
                    String.format("A expressao do range deve ser iteravel (slice, array, map, string ou chan), mas recebeu '%s'.",
                            normalizedType)));
        }
    }

    private List<String> getParameterTypes(List<Parameter> parameters) {
        List<String> parameterTypes = new ArrayList<>();

        for (Parameter parameter : parameters) {
            parameterTypes.add(parameter.type);
        }

        return parameterTypes;
    }

    private boolean isBuiltInFunction(String name) {
        return "print".equals(name) || "println".equals(name) || "len".equals(name)
                || "cap".equals(name) || "append".equals(name) || "make".equals(name)
                || "new".equals(name);
    }

    private String getBuiltInFunctionReturnType(String name, List<String> argumentTypes) {
        if ("len".equals(name) || "cap".equals(name)) {
            return TYPE_INT;
        }

        if ("append".equals(name) && !argumentTypes.isEmpty()) {
            return argumentTypes.get(0);
        }

        if ("new".equals(name) && !argumentTypes.isEmpty()) {
            return "*" + argumentTypes.get(0);
        }

        if ("make".equals(name) && !argumentTypes.isEmpty()) {
            return argumentTypes.get(0);
        }

        return TYPE_VOID;
    }

    private String validateFunctionCall(Token functionToken, Symbol functionSymbol, List<String> argumentTypes) {
        if (functionSymbol == null || !"FUNC".equals(functionSymbol.getCategory())) {
            throw semanticError(functionToken,
                    String.format("O identificador '%s' nao e uma funcao.", functionToken.getValue()));
        }

        List<String> parameterTypes = functionSymbol.getParameterTypes();
        if (parameterTypes.size() != argumentTypes.size()) {
            throw semanticError(functionToken,
                    String.format("A funcao '%s' espera %d argumento(s), mas recebeu %d.",
                            functionToken.getValue(), parameterTypes.size(), argumentTypes.size()));
        }

        for (int i = 0; i < parameterTypes.size(); i++) {
            if (!areTypesCompatible(parameterTypes.get(i), argumentTypes.get(i))) {
                throw semanticError(functionToken,
                        String.format("Argumento %d da funcao '%s' deve ser '%s', mas recebeu '%s'.",
                                i + 1, functionToken.getValue(), normalizeType(parameterTypes.get(i)),
                                normalizeType(argumentTypes.get(i))));
            }
        }

        if (functionSymbol.getReturnTypes().isEmpty()) {
            return TYPE_VOID;
        }

        return functionSymbol.getReturnTypes().get(0);
    }

    private void recoverDeclaration() {
        if (currentToken == null || currentToken.getType() == TokenType.EOF) {
            return;
        }

        if (currentToken.getType() == TokenType.SEMICOLON || currentToken.getType() == TokenType.RBRACE) {
            advance();
            return;
        }

        if (isTopLevelStart(currentToken.getType())) {
            return;
        }

        advance();

        while (currentToken.getType() != TokenType.EOF) {
            if (currentToken.getType() == TokenType.SEMICOLON || currentToken.getType() == TokenType.RBRACE) {
                advance();
                return;
            }

            if (isTopLevelStart(currentToken.getType())) {
                return;
            }

            advance();
        }
    }

    private boolean isTopLevelStart(TokenType type) {
        return type == TokenType.IMPORT || type == TokenType.FUNC || type == TokenType.VAR
                || type == TokenType.CONST || type == TokenType.TYPE;
    }

    private void recoverStatement() {
        recoverToStatementStart(true, false);
    }

    private void recoverSwitchCase() {
        recoverToStatementStart(true, true);
    }

    private void recoverToStatementStart(boolean stopAtBlockEnd, boolean stopAtSwitchCase) {
        if (currentToken == null || currentToken.getType() == TokenType.EOF) {
            return;
        }

        if (currentToken.getType() == TokenType.SEMICOLON) {
            advance();
            return;
        }

        if (isRecoveryBoundary(currentToken.getType(), stopAtBlockEnd, stopAtSwitchCase)) {
            return;
        }

        advance();

        while (currentToken.getType() != TokenType.EOF) {
            if (currentToken.getType() == TokenType.SEMICOLON) {
                advance();
                return;
            }

            if (isRecoveryBoundary(currentToken.getType(), stopAtBlockEnd, stopAtSwitchCase)
                    || isDeclarationOrStatementStart(currentToken.getType())) {
                return;
            }

            advance();
        }
    }

    private boolean isRecoveryBoundary(TokenType type, boolean stopAtBlockEnd, boolean stopAtSwitchCase) {
        return (stopAtBlockEnd && type == TokenType.RBRACE)
                || (stopAtSwitchCase && (type == TokenType.CASE || type == TokenType.DEFAULT));
    }

    private boolean isDeclarationOrStatementStart(TokenType type) {
        switch (type) {
            case FUNC:
            case VAR:
            case CONST:
            case TYPE:
            case IDENTIFIER:
            case IF:
            case FOR:
            case SWITCH:
            case RETURN:
            case GO:
            case DEFER:
            case BREAK:
            case CONTINUE:
            case FALLTHROUGH:
                return true;
            default:
                return false;
        }
    }

    public void parse() {
        try {
            parseProgram();
        } catch (SyntaxException e) {
            reportError(e);
            recoverDeclaration();
        }

        if (errors.isEmpty()) {
            System.out.println("\nAnalise Sintatica e Semantica concluida com sucesso!\n");
        } else {
            System.out.println("\nForam encontrados " + errors.size() + " erros.\n");
            errors.forEach(System.err::println);
        }
    }

    private void parseProgram() {
        try {
            match(TokenType.PACKAGE);
            match(TokenType.IDENTIFIER);
        } catch (SyntaxException e) {
            reportError(e);
            recoverDeclaration();
        }

        parseImports();
        parseTopLevelDeclarations();

        match(TokenType.EOF);
    }

    private void parseImports() {
        while (currentToken.getType() == TokenType.IMPORT) {
            try {
                advance();
                if (currentToken.getType() == TokenType.LPAREN) {
                    advance();
                    while (currentToken.getType() == TokenType.STRING_LITERAL) {
                        registerImportedPackage(currentToken);
                        advance();
                    }
                    match(TokenType.RPAREN);
                } else {
                    registerImportedPackage(match(TokenType.STRING_LITERAL));
                }
            } catch (SyntaxException e) {
                reportError(e);
                recoverDeclaration();
            }
        }
    }

    private void registerImportedPackage(Token importToken) {
        String value = importToken.getValue().replace("\"", "").replace("`", "");
        if (value.isBlank()) {
            return;
        }

        int slashIndex = value.lastIndexOf("/");
        if (slashIndex >= 0 && slashIndex < value.length() - 1) {
            value = value.substring(slashIndex + 1);
        }

        importedPackages.add(value);
    }

    private void parseTopLevelDeclarations() {
        while (currentToken.getType() != TokenType.EOF) {
            try {
                if (currentToken.getType() == TokenType.SEMICOLON) {
                    advance();
                    continue;
                }

                TokenType type = currentToken.getType();
                if (type == TokenType.VAR || type == TokenType.CONST) {
                    parseVarOrConstDeclaration();
                } else if (type == TokenType.TYPE) {
                    parseTypeDeclaration();
                } else if (type == TokenType.FUNC) {
                    parseFuncDeclaration();
                } else {
                    throw syntaxError("Declaracao de topo invalida.");
                }
            } catch (SyntaxException e) {
                reportError(e);
                recoverDeclaration();
            }
        }
    }

    private void parseVarOrConstDeclaration() {
        boolean isConst = currentToken.getType() == TokenType.CONST;
        advance();

        Token idToken = match(TokenType.IDENTIFIER);
        String typeStr = TYPE_INFERRED;
        String assignedVal = null;
        String expressionType = TYPE_UNKNOWN;

        if (currentToken.getType() != TokenType.ASSIGN) {
            typeStr = parseType();
        }

        boolean isInitialized = false;
        if (currentToken.getType() == TokenType.ASSIGN) {
            isInitialized = true;
            advance();

            int exprStartIndex = currentTokenIndex;
            expressionType = parseExpression();
            int exprEndIndex = currentTokenIndex;
            assignedVal = buildExpressionText(exprStartIndex, exprEndIndex);
        }

        if (TYPE_INFERRED.equals(typeStr)) {
            if (!isInitialized) {
                throw semanticError(idToken,
                        String.format("Nao foi possivel inferir o tipo de '%s'.", idToken.getValue()));
            }
            typeStr = expressionType;
        } else if (isInitialized) {
            validateAssignmentTypes(idToken, typeStr, expressionType);
        }

        addSymbol(idToken, typeStr, isConst ? "CONST" : "VAR", currentScopeLevel, isInitialized, assignedVal);
    }

    private String buildExpressionText(int startIndex, int endIndex) {
        StringBuilder exprBuilder = new StringBuilder();

        for (int i = startIndex; i < endIndex; i++) {
            if (i < tokens.size()) {
                exprBuilder.append(tokens.get(i).getValue());

                if (i < endIndex - 1
                        && !isPunctuation(tokens.get(i).getType())
                        && !isPunctuation(tokens.get(i + 1).getType())) {
                    exprBuilder.append(" ");
                }
            }
        }

        return exprBuilder.toString().trim();
    }

    private boolean isPunctuation(TokenType type) {
        return type == TokenType.DOT || type == TokenType.COMMA || type == TokenType.SEMICOLON
                || type == TokenType.LPAREN || type == TokenType.RPAREN
                || type == TokenType.LBRACKET || type == TokenType.RBRACKET
                || type == TokenType.LBRACE || type == TokenType.RBRACE
                || type == TokenType.COLON || type == TokenType.ASSIGN
                || type == TokenType.DEFINE || type == TokenType.PLUS_ASSIGN
                || type == TokenType.MINUS || type == TokenType.PLUS
                || type == TokenType.MULTIPLY || type == TokenType.DIVIDE || type == TokenType.MOD
                || type == TokenType.EQUAL || type == TokenType.NOT_EQUAL
                || type == TokenType.GREATER || type == TokenType.GREATER_EQUAL
                || type == TokenType.LESS || type == TokenType.LESS_EQUAL
                || type == TokenType.LOGICAL_AND || type == TokenType.LOGICAL_OR
                || type == TokenType.LOGICAL_NOT || type == TokenType.BITWISE_AND
                || type == TokenType.ARROW || type == TokenType.INCREMENT || type == TokenType.DECREMENT;
    }

    private void parseTypeDeclaration() {
        match(TokenType.TYPE);
        Token idToken = match(TokenType.IDENTIFIER);

        if (currentToken.getType() == TokenType.STRUCT) {
            advance();
            match(TokenType.LBRACE);
            while (currentToken.getType() == TokenType.IDENTIFIER) {
                advance();
                parseType();
            }
            match(TokenType.RBRACE);
            addSymbol(idToken, "struct", "TYPE", currentScopeLevel, false, null);
        } else if (currentToken.getType() == TokenType.INTERFACE) {
            advance();
            match(TokenType.LBRACE);
            while (currentToken.getType() == TokenType.IDENTIFIER) {
                advance();
                match(TokenType.LPAREN);
                parseOptionalParameters();
                match(TokenType.RPAREN);
                if (currentToken.getType() != TokenType.RBRACE) {
                    parseFuncReturnTypes();
                }
            }
            match(TokenType.RBRACE);
            addSymbol(idToken, "interface", "TYPE", currentScopeLevel, false, null);
        } else {
            parseType();
            addSymbol(idToken, "alias", "TYPE", currentScopeLevel, false, null);
        }
    }

    private void parseFuncDeclaration() {
        match(TokenType.FUNC);

        if (currentToken.getType() == TokenType.LPAREN) {
            advance();
            match(TokenType.IDENTIFIER);
            parseType();
            match(TokenType.RPAREN);
        }

        Token idToken = match(TokenType.IDENTIFIER);
        Symbol functionSymbol = addSymbol(idToken, "func", "FUNC", currentScopeLevel, false, null);

        match(TokenType.LPAREN);
        List<Parameter> parameters = parseOptionalParameters();
        match(TokenType.RPAREN);

        List<String> returnTypes = new ArrayList<>();
        if (currentToken.getType() != TokenType.LBRACE) {
            returnTypes = parseFuncReturnTypes();
        }

        functionSymbol.setParameterTypes(getParameterTypes(parameters));
        functionSymbol.setReturnTypes(returnTypes);

        parseBlock(parameters);
    }

    private List<Parameter> parseOptionalParameters() {
        List<Parameter> parameters = new ArrayList<>();

        if (currentToken.getType() == TokenType.IDENTIFIER) {
            Token parameterToken = match(TokenType.IDENTIFIER);
            String parameterType = parseType();
            parameters.add(new Parameter(parameterToken, parameterType));

            while (currentToken.getType() == TokenType.COMMA) {
                advance();
                parameterToken = match(TokenType.IDENTIFIER);
                parameterType = parseType();
                parameters.add(new Parameter(parameterToken, parameterType));
            }
        }

        return parameters;
    }

    private List<String> parseFuncReturnTypes() {
        List<String> returnTypes = new ArrayList<>();

        if (currentToken.getType() == TokenType.LPAREN) {
            advance();
            returnTypes.add(parseType());
            while (currentToken.getType() == TokenType.COMMA) {
                advance();
                returnTypes.add(parseType());
            }
            match(TokenType.RPAREN);
        } else {
            returnTypes.add(parseType());
        }

        return returnTypes;
    }

    private String parseType() {
        if (currentToken.getType() == TokenType.MULTIPLY) {
            advance();
            return "*" + parseType();
        } else if (currentToken.getType() == TokenType.LBRACKET) {
            advance();
            if (currentToken.getType() == TokenType.RBRACKET) {
                advance();
                return "slice_" + parseType();
            } else {
                String dimensionType = parseExpression();
                if (!isIntegerType(dimensionType) && !TYPE_UNKNOWN.equals(normalizeType(dimensionType))) {
                    throw semanticError(currentToken, "A dimensao do array deve ser inteira.");
                }
                match(TokenType.RBRACKET);
                return "array_" + parseType();
            }
        } else if (currentToken.getType() == TokenType.MAP) {
            advance();
            match(TokenType.LBRACKET);
            parseType();
            match(TokenType.RBRACKET);
            return "map_" + parseType();
        } else if (currentToken.getType() == TokenType.CHAN) {
            advance();
            return "chan_" + parseType();
        } else {
            Token typeToken = match(TokenType.IDENTIFIER);
            String parsedType = normalizeType(typeToken.getValue());

            if (!isKnownType(parsedType)) {
                throw semanticError(typeToken,
                        String.format("Tipo '%s' nao declarado.", typeToken.getValue()));
            }

            return parsedType;
        }
    }

    private void parseBlock() {
        parseBlock(new ArrayList<>());
    }

    private void parseBlock(List<Parameter> parameters) {
        match(TokenType.LBRACE);
        currentScopeLevel++;

        for (Parameter parameter : parameters) {
            addSymbol(parameter.token, parameter.type, "PARAM", currentScopeLevel, true, null);
        }

        while (currentToken.getType() != TokenType.RBRACE && currentToken.getType() != TokenType.EOF) {
            try {
                parseStatement();
                if (currentToken.getType() == TokenType.SEMICOLON) {
                    advance();
                }
            } catch (SyntaxException e) {
                reportError(e);
                recoverStatement();
            }
        }

        match(TokenType.RBRACE);
        removeSymbolsFromScope(currentScopeLevel);
        currentScopeLevel--;
    }

    private void parseStatement() {
        validateToken();
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
            parseReturnStatement();
        } else if (type == TokenType.BREAK || type == TokenType.CONTINUE || type == TokenType.FALLTHROUGH) {
            advance();
        } else if (type == TokenType.GO || type == TokenType.DEFER) {
            advance();
            parseExpression();
        } else if (type == TokenType.ELSE) {
            throw syntaxError("'else' sem um 'if' correspondente.");
        } else if (type == TokenType.IDENTIFIER) {
            parseIdentifierStatement();
        } else if (type == TokenType.MULTIPLY) {
            String leftType = parseExpression();
            parsePossibleAssignment(leftType, currentToken);
        } else {
            parseExpression();
        }
    }

    private void parseReturnStatement() {
        advance();
        if (currentToken.getType() != TokenType.RBRACE && currentToken.getType() != TokenType.SEMICOLON) {
            parseExpression();
            while (currentToken.getType() == TokenType.COMMA) {
                advance();
                parseExpression();
            }
        }
    }

    private void parseIdentifierStatement() {
        Token idToken = currentToken;
        TokenType nextType = peekType(1);

        if (isDirectAssignmentOperator(nextType)) {
            advance();
            Token operatorToken = currentToken;
            advance();
            String rightType = parseExpression();

            if (operatorToken.getType() == TokenType.DEFINE) {
                if (isDeclaredInCurrentScope(idToken.getValue())) {
                    throw semanticError(idToken,
                            String.format("O identificador '%s' ja foi declarado neste escopo.", idToken.getValue()));
                }
                addSymbol(idToken, rightType, "VAR", currentScopeLevel, true, null);
            } else {
                Symbol symbol = lookupSymbol(idToken.getValue());
                if (symbol == null) {
                    throw semanticError(idToken,
                            String.format("Variavel '%s' nao declarada.", idToken.getValue()));
                }

                validateAssignmentOperator(operatorToken, symbol.getType(), rightType);
                symbol.setInitialized(true);
            }
            return;
        }

        if (nextType == TokenType.INCREMENT || nextType == TokenType.DECREMENT) {
            Symbol symbol = lookupSymbol(idToken.getValue());
            if (symbol == null) {
                throw semanticError(idToken,
                        String.format("Variavel '%s' nao declarada.", idToken.getValue()));
            }
            if (!isNumericOrUnknown(symbol.getType())) {
                throw semanticError(idToken,
                        String.format("Operador '%s' exige variavel numerica.", tokens.get(currentTokenIndex + 1).getValue()));
            }
            advance();
            advance();
            return;
        }

        String leftType = parseExpression();
        parsePossibleAssignment(leftType, idToken);
    }

    private void parsePossibleAssignment(String leftType, Token leftToken) {
        if (isAssignmentOperator(currentToken.getType())) {
            Token operatorToken = currentToken;
            advance();
            String rightType = parseExpression();
            validateAssignmentOperator(operatorToken, leftType, rightType);
        } else if (currentToken.getType() == TokenType.INCREMENT || currentToken.getType() == TokenType.DECREMENT) {
            if (!isNumericOrUnknown(leftType)) {
                throw semanticError(leftToken,
                        String.format("Operador '%s' exige variavel numerica.", currentToken.getValue()));
            }
            advance();
        }
    }

    private boolean isDirectAssignmentOperator(TokenType type) {
        return type == TokenType.ASSIGN || type == TokenType.DEFINE
                || type == TokenType.PLUS_ASSIGN || type == TokenType.MINUS_ASSIGN
                || type == TokenType.MULTIPLY_ASSIGN || type == TokenType.DIVIDE_ASSIGN
                || type == TokenType.MOD_ASSIGN || type == TokenType.AND_ASSIGN
                || type == TokenType.OR_ASSIGN || type == TokenType.XOR_ASSIGN
                || type == TokenType.LEFT_SHIFT_ASSIGN || type == TokenType.RIGHT_SHIFT_ASSIGN
                || type == TokenType.BIT_CLEAR_ASSIGN;
    }

    private void validateAssignmentOperator(Token operatorToken, String leftType, String rightType) {
        if (operatorToken.getType() == TokenType.DEFINE) {
            return;
        }

        validateAssignmentTypes(operatorToken, leftType, rightType);

        if (operatorToken.getType() != TokenType.ASSIGN) {
            if (operatorToken.getType() == TokenType.PLUS_ASSIGN && TYPE_STRING.equals(normalizeType(leftType))) {
                return;
            }

            if (!isNumericOrUnknown(leftType) || !isNumericOrUnknown(rightType)) {
                throw semanticError(operatorToken,
                        String.format("Operador '%s' exige operandos numericos.", operatorToken.getValue()));
            }
        }
    }

    private void parseIfStatement() {
        Token ifToken = match(TokenType.IF);
        String conditionType = parseExpression();
        validateBoolExpression(ifToken, conditionType, "if");
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
        String switchType = TYPE_BOOL;
        if (currentToken.getType() != TokenType.LBRACE) {
            switchType = parseExpression();
        }
        match(TokenType.LBRACE);

        while (currentToken.getType() == TokenType.CASE || currentToken.getType() == TokenType.DEFAULT) {
            if (currentToken.getType() == TokenType.CASE) {
                advance();
                Token caseToken = currentToken;
                String caseType = parseExpression();
                validateCaseType(caseToken, switchType, caseType);
                match(TokenType.COLON);
            } else {
                advance();
                match(TokenType.COLON);
            }

            while (currentToken.getType() != TokenType.CASE && currentToken.getType() != TokenType.DEFAULT
                    && currentToken.getType() != TokenType.RBRACE && currentToken.getType() != TokenType.EOF) {
                try {
                    parseStatement();
                    if (currentToken.getType() == TokenType.SEMICOLON) {
                        advance();
                    }
                } catch (SyntaxException e) {
                    reportError(e);
                    recoverSwitchCase();
                }
            }
        }
        match(TokenType.RBRACE);
    }

    private void parseForStatement() {
        Token forToken = match(TokenType.FOR);

        if (currentToken.getType() == TokenType.LBRACE) {
            parseBlock();
            return;
        }

        if (isRangeForStart()) {
            parseRangeForStatement();
            return;
        }

        if (currentToken.getType() == TokenType.SEMICOLON) {
            advance();
            String conditionType = parseExpression();
            validateBoolExpression(forToken, conditionType, "for");
            match(TokenType.SEMICOLON);
            parseStatement();
            parseBlock();
            return;
        }

        if (currentToken.getType() == TokenType.IDENTIFIER && isDirectAssignmentOperator(peekType(1))) {
            currentScopeLevel++;
            try {
                parseIdentifierStatement();
                if (currentToken.getType() != TokenType.SEMICOLON) {
                    throw syntaxError("Declaracao 'for' invalida ou faltando ';'.");
                }

                advance();
                String conditionType = parseExpression();
                validateBoolExpression(forToken, conditionType, "for");
                match(TokenType.SEMICOLON);
                parseStatement();
                parseBlock();
            } finally {
                removeSymbolsFromScope(currentScopeLevel);
                currentScopeLevel--;
            }
            return;
        }

        String conditionType = parseExpression();
        validateBoolExpression(forToken, conditionType, "for");
        parseBlock();
    }

    private boolean isRangeForStart() {
        return currentToken.getType() == TokenType.RANGE
                || (currentToken.getType() == TokenType.IDENTIFIER
                        && peekType(1) == TokenType.DEFINE && peekType(2) == TokenType.RANGE)
                || (currentToken.getType() == TokenType.IDENTIFIER
                        && peekType(1) == TokenType.COMMA && peekType(2) == TokenType.IDENTIFIER
                        && peekType(3) == TokenType.DEFINE && peekType(4) == TokenType.RANGE);
    }

    private void parseRangeForStatement() {
        if (currentToken.getType() == TokenType.RANGE) {
            Token rangeToken = currentToken;
            advance();
            String rangeType = parseExpression();
            validateRangeableExpression(rangeToken, rangeType);
            parseBlock();
            return;
        }

        Token firstToken = match(TokenType.IDENTIFIER);
        Token secondToken = null;

        if (currentToken.getType() == TokenType.COMMA) {
            advance();
            secondToken = match(TokenType.IDENTIFIER);
        }

        match(TokenType.DEFINE);
        Token rangeToken = match(TokenType.RANGE);
        String rangeType = parseExpression();
        validateRangeableExpression(rangeToken, rangeType);

        currentScopeLevel++;
        try {
            if (isDeclaredInCurrentScope(firstToken.getValue())) {
                throw semanticError(firstToken,
                        String.format("O identificador '%s' ja foi declarado neste escopo.", firstToken.getValue()));
            }
            addSymbol(firstToken, TYPE_UNKNOWN, "VAR", currentScopeLevel, true, null);

            if (secondToken != null) {
                if (isDeclaredInCurrentScope(secondToken.getValue())) {
                    throw semanticError(secondToken,
                            String.format("O identificador '%s' ja foi declarado neste escopo.", secondToken.getValue()));
                }
                addSymbol(secondToken, TYPE_UNKNOWN, "VAR", currentScopeLevel, true, null);
            }

            parseBlock();
        } finally {
            removeSymbolsFromScope(currentScopeLevel);
            currentScopeLevel--;
        }
    }

    private String parseExpression() {
        return parseLogicalOr();
    }

    private String parseLogicalOr() {
        String leftType = parseLogicalAnd();
        while (currentToken.getType() == TokenType.LOGICAL_OR) {
            Token operatorToken = currentToken;
            advance();
            String rightType = parseLogicalAnd();
            if (!TYPE_BOOL.equals(normalizeType(leftType)) || !TYPE_BOOL.equals(normalizeType(rightType))) {
                throw semanticError(operatorToken,
                        String.format("Operador '%s' exige operandos bool.", operatorToken.getValue()));
            }
            leftType = TYPE_BOOL;
        }
        return leftType;
    }

    private String parseLogicalAnd() {
        String leftType = parseRelational();
        while (currentToken.getType() == TokenType.LOGICAL_AND) {
            Token operatorToken = currentToken;
            advance();
            String rightType = parseRelational();
            if (!TYPE_BOOL.equals(normalizeType(leftType)) || !TYPE_BOOL.equals(normalizeType(rightType))) {
                throw semanticError(operatorToken,
                        String.format("Operador '%s' exige operandos bool.", operatorToken.getValue()));
            }
            leftType = TYPE_BOOL;
        }
        return leftType;
    }

    private String parseRelational() {
        String leftType = parseSum();
        while (isRelationalOperator(currentToken.getType())) {
            Token operatorToken = currentToken;
            advance();
            String rightType = parseSum();

            if (operatorToken.getType() == TokenType.EQUAL || operatorToken.getType() == TokenType.NOT_EQUAL) {
                if (!areTypesCompatible(leftType, rightType) && !areTypesCompatible(rightType, leftType)) {
                    throw semanticError(operatorToken,
                            String.format("Comparacao entre tipos incompativeis: '%s' e '%s'.",
                                    normalizeType(leftType), normalizeType(rightType)));
                }
            } else if (!isNumericOrUnknown(leftType) && !TYPE_STRING.equals(normalizeType(leftType))) {
                throw semanticError(operatorToken,
                        String.format("Operador '%s' exige tipo ordenavel.", operatorToken.getValue()));
            } else if (!areTypesCompatible(leftType, rightType) && !areTypesCompatible(rightType, leftType)) {
                throw semanticError(operatorToken,
                        String.format("Comparacao entre tipos incompativeis: '%s' e '%s'.",
                                normalizeType(leftType), normalizeType(rightType)));
            }

            leftType = TYPE_BOOL;
        }
        return leftType;
    }

    private String parseSum() {
        String leftType = parseMult();
        while (currentToken.getType() == TokenType.PLUS || currentToken.getType() == TokenType.MINUS) {
            Token operatorToken = currentToken;
            advance();
            String rightType = parseMult();

            if (operatorToken.getType() == TokenType.PLUS
                    && (TYPE_STRING.equals(normalizeType(leftType)) || TYPE_STRING.equals(normalizeType(rightType)))) {
                if (!TYPE_STRING.equals(normalizeType(leftType)) || !TYPE_STRING.equals(normalizeType(rightType))) {
                    throw semanticError(operatorToken, "Concatenacao exige dois operandos string.");
                }
                leftType = TYPE_STRING;
            } else {
                leftType = combineNumericTypes(operatorToken, leftType, rightType);
            }
        }
        return leftType;
    }

    private String parseMult() {
        String leftType = parseUnary();
        while (currentToken.getType() == TokenType.MULTIPLY || currentToken.getType() == TokenType.DIVIDE
                || currentToken.getType() == TokenType.MOD) {
            Token operatorToken = currentToken;
            advance();
            String rightType = parseUnary();

            if (operatorToken.getType() == TokenType.MOD
                    && (!isIntegerOrUnknown(leftType) || !isIntegerOrUnknown(rightType))) {
                throw semanticError(operatorToken, "Operador '%' exige operandos inteiros.");
            }

            leftType = combineNumericTypes(operatorToken, leftType, rightType);
        }
        return leftType;
    }

    private String parseUnary() {
        TokenType type = currentToken.getType();
        if (type == TokenType.MINUS) {
            Token operatorToken = currentToken;
            advance();
            String unaryType = parseUnary();
            if (!isNumericOrUnknown(unaryType)) {
                throw semanticError(operatorToken, "Operador '-' exige operando numerico.");
            }
            return unaryType;
        } else if (type == TokenType.LOGICAL_NOT) {
            Token operatorToken = currentToken;
            advance();
            String unaryType = parseUnary();
            if (!TYPE_BOOL.equals(normalizeType(unaryType))) {
                throw semanticError(operatorToken, "Operador '!' exige operando bool.");
            }
            return TYPE_BOOL;
        } else if (type == TokenType.BITWISE_AND) {
            advance();
            return "*" + parseUnary();
        } else if (type == TokenType.MULTIPLY) {
            Token operatorToken = currentToken;
            advance();
            String unaryType = parseUnary();
            if (normalizeType(unaryType).startsWith("*")) {
                return unaryType.substring(1);
            }
            if (TYPE_UNKNOWN.equals(normalizeType(unaryType))) {
                return TYPE_UNKNOWN;
            }
            throw semanticError(operatorToken, "Operador '*' exige ponteiro.");
        } else if (type == TokenType.ARROW) {
            advance();
            parseUnary();
            return TYPE_UNKNOWN;
        }

        return parseFactor();
    }

    private String parseFactor() {
        validateToken();
        TokenType type = currentToken.getType();

        if (isLiteral(type)) {
            String literalType = getLiteralType(type);
            advance();
            return literalType;
        } else if (type == TokenType.NIL) {
            advance();
            return TYPE_NIL;
        } else if (type == TokenType.LPAREN) {
            advance();
            String expressionType = parseExpression();
            match(TokenType.RPAREN);
            return expressionType;
        } else if (type == TokenType.IDENTIFIER) {
            return parseIdentifierFactor();
        }

        throw syntaxError("Fator inesperado na expressao.");
    }

    private String parseIdentifierFactor() {
        Token idToken = match(TokenType.IDENTIFIER);
        String name = idToken.getValue();

        if (isBuiltInFunction(name) && currentToken.getType() == TokenType.LPAREN) {
            advance();
            List<String> argumentTypes = parseOptionalArguments();
            match(TokenType.RPAREN);
            return getBuiltInFunctionReturnType(name, argumentTypes);
        }

        Symbol symbol = lookupSymbol(name);
        boolean importedPackage = importedPackages.contains(name);

        if (currentToken.getType() == TokenType.LPAREN && isKnownType(name)
                && (symbol == null || "TYPE".equals(symbol.getCategory()))) {
            advance();
            parseOptionalArguments();
            match(TokenType.RPAREN);
            return normalizeType(name);
        }

        if (symbol == null && !importedPackage) {
            throw semanticError(idToken,
                    String.format("Variavel '%s' nao declarada.", name));
        }

        String resultType = symbol != null ? symbol.getType() : TYPE_UNKNOWN;
        boolean simpleFunctionCall = symbol != null && "FUNC".equals(symbol.getCategory());

        while (true) {
            if (currentToken.getType() == TokenType.DOT) {
                advance();
                match(TokenType.IDENTIFIER);
                simpleFunctionCall = false;
                resultType = TYPE_UNKNOWN;
            } else if (currentToken.getType() == TokenType.LPAREN) {
                advance();
                List<String> argumentTypes = parseOptionalArguments();
                match(TokenType.RPAREN);
                if (simpleFunctionCall) {
                    resultType = validateFunctionCall(idToken, symbol, argumentTypes);
                } else if (symbol != null && !"FUNC".equals(symbol.getCategory())) {
                    resultType = TYPE_UNKNOWN;
                }
            } else if (currentToken.getType() == TokenType.LBRACKET) {
                resultType = parseIndexAccess(resultType);
                simpleFunctionCall = false;
            } else {
                break;
            }
        }

        return resultType;
    }

    private String parseIndexAccess(String collectionType) {
        advance();
        if (currentToken.getType() != TokenType.COLON) {
            String indexType = parseExpression();
            if (!isIntegerType(indexType) && !TYPE_UNKNOWN.equals(normalizeType(indexType))) {
                throw semanticError(currentToken, "Indice deve ser inteiro.");
            }
        }

        if (currentToken.getType() == TokenType.COLON) {
            advance();
        }

        if (currentToken.getType() != TokenType.RBRACKET) {
            String indexType = parseExpression();
            if (!isIntegerType(indexType) && !TYPE_UNKNOWN.equals(normalizeType(indexType))) {
                throw semanticError(currentToken, "Indice deve ser inteiro.");
            }
        }

        match(TokenType.RBRACKET);
        String normalizedType = normalizeType(collectionType);

        if (normalizedType.startsWith("slice_")) {
            return normalizedType.substring("slice_".length());
        }

        if (normalizedType.startsWith("array_")) {
            return normalizedType.substring("array_".length());
        }

        if (normalizedType.startsWith("map_")) {
            return normalizedType.substring("map_".length());
        }

        return TYPE_UNKNOWN;
    }

    private List<String> parseOptionalArguments() {
        List<String> argumentTypes = new ArrayList<>();

        if (currentToken.getType() != TokenType.RPAREN) {
            argumentTypes.add(parseExpression());
            while (currentToken.getType() == TokenType.COMMA) {
                advance();
                argumentTypes.add(parseExpression());
            }
        }

        return argumentTypes;
    }

    private boolean isRelationalOperator(TokenType type) {
        return type == TokenType.EQUAL || type == TokenType.NOT_EQUAL
                || type == TokenType.GREATER || type == TokenType.GREATER_EQUAL
                || type == TokenType.LESS || type == TokenType.LESS_EQUAL;
    }

    private boolean isAssignmentOperator(TokenType type) {
        return type == TokenType.ASSIGN || type == TokenType.DEFINE
                || type == TokenType.PLUS_ASSIGN || type == TokenType.MINUS_ASSIGN
                || type == TokenType.MULTIPLY_ASSIGN || type == TokenType.DIVIDE_ASSIGN
                || type == TokenType.MOD_ASSIGN || type == TokenType.AND_ASSIGN
                || type == TokenType.OR_ASSIGN || type == TokenType.XOR_ASSIGN
                || type == TokenType.LEFT_SHIFT_ASSIGN || type == TokenType.RIGHT_SHIFT_ASSIGN
                || type == TokenType.BIT_CLEAR_ASSIGN;
    }

    private boolean isLiteral(TokenType type) {
        return type == TokenType.INT_LITERAL || type == TokenType.FLOAT_LITERAL
                || type == TokenType.STRING_LITERAL || type == TokenType.RAW_STRING_LITERAL
                || type == TokenType.RUNE_LITERAL || type == TokenType.TRUE || type == TokenType.FALSE;
    }

    private String getLiteralType(TokenType type) {
        if (type == TokenType.INT_LITERAL) {
            return TYPE_INT;
        }

        if (type == TokenType.FLOAT_LITERAL) {
            return TYPE_FLOAT;
        }

        if (type == TokenType.STRING_LITERAL || type == TokenType.RAW_STRING_LITERAL) {
            return TYPE_STRING;
        }

        if (type == TokenType.RUNE_LITERAL) {
            return TYPE_RUNE;
        }

        if (type == TokenType.TRUE || type == TokenType.FALSE) {
            return TYPE_BOOL;
        }

        return TYPE_UNKNOWN;
    }

    public int getErrorLine() {
        return errorLine;
    }

    public List<Integer> getErrorLines() {
        return Collections.unmodifiableList(errorLines);
    }
}
