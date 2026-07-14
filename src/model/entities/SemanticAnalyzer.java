package model.entities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import model.ast.ArrayTypeNode;
import model.ast.AssignStmtNode;
import model.ast.BinaryExprNode;
import model.ast.BlockNode;
import model.ast.CallExprNode;
import model.ast.CaseClauseNode;
import model.ast.ChanTypeNode;
import model.ast.DeclNode;
import model.ast.ExprNode;
import model.ast.ExprStmtNode;
import model.ast.FieldAccessExprNode;
import model.ast.ForStmtNode;
import model.ast.FuncDeclNode;
import model.ast.IdentifierExprNode;
import model.ast.IfStmtNode;
import model.ast.IncDecStmtNode;
import model.ast.IndexExprNode;
import model.ast.KeywordStmtNode;
import model.ast.LiteralExprNode;
import model.ast.MapTypeNode;
import model.ast.MethodSigNode;
import model.ast.NamedTypeNode;
import model.ast.ParamNode;
import model.ast.PointerTypeNode;
import model.ast.ProgramNode;
import model.ast.RangeForStmtNode;
import model.ast.ReturnStmtNode;
import model.ast.ShortVarDeclStmtNode;
import model.ast.SliceExprNode;
import model.ast.SliceTypeNode;
import model.ast.StmtNode;
import model.ast.SwitchStmtNode;
import model.ast.TypeDeclNode;
import model.ast.TypeNode;
import model.ast.UnaryExprNode;
import model.ast.VarDeclNode;
import model.enums.TokenType;
import model.exceptions.SyntaxException;

public class SemanticAnalyzer {

    private static final String TYPE_UNKNOWN = "desconhecido";
    private static final String TYPE_INFERRED = "inferido";
    private static final String TYPE_BOOL = "bool";
    private static final String TYPE_INT = "int";
    private static final String TYPE_FLOAT = "float64";
    private static final String TYPE_STRING = "string";
    private static final String TYPE_RUNE = "rune";
    private static final String TYPE_NIL = "nil";
    private static final String TYPE_VOID = "void";

    private final ProgramNode program;

    private int errorLine = 0;
    private List<String> errors;
    private List<Integer> errorLines;

    private Map<String, Symbol> symbolTable;
    private int currentScopeLevel;
    private int nextMemoryAddress;
    private Set<String> importedPackages;

    public SemanticAnalyzer(ProgramNode program) {
        this.program = program;
        this.symbolTable = new HashMap<>();
        this.currentScopeLevel = 0;
        this.nextMemoryAddress = 0;
        this.importedPackages = new HashSet<>(program.getImports());
        this.errors = new ArrayList<>();
        this.errorLines = new ArrayList<>();
    }

    private SyntaxException semanticError(Token token, String message) {
        return new SyntaxException(
                String.format("Erro Semantico [Linha %d, Coluna %d]. %s",
                        token.getLine(), token.getColumn(), message));
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
            return 0;
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

        return 0;
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

    public void analyze() {
        for (DeclNode decl : program.getDeclarations()) {
            try {
                visitDeclaration(decl);
            } catch (SyntaxException e) {
                reportError(e);
            }
        }

        if (errors.isEmpty()) {
            System.out.println("\nAnalise Semantica concluida com sucesso!\n");
        } else {
            System.out.println("\nForam encontrados " + errors.size() + " erros semanticos.\n");
            errors.forEach(System.err::println);
        }
    }

    private void visitDeclaration(DeclNode decl) {
        if (decl instanceof VarDeclNode varDecl) {
            visitVarDecl(varDecl);
        } else if (decl instanceof TypeDeclNode typeDecl) {
            visitTypeDecl(typeDecl);
        } else if (decl instanceof FuncDeclNode funcDecl) {
            visitFuncDecl(funcDecl);
        }
    }

    private void visitVarDecl(VarDeclNode node) {
        String typeStr = TYPE_INFERRED;
        if (node.getDeclaredType() != null) {
            typeStr = resolveTypeName(node.getDeclaredType());
        }

        boolean isInitialized = node.getInitializer() != null;
        String expressionType = TYPE_UNKNOWN;
        String assignedVal = null;
        if (isInitialized) {
            expressionType = evaluateExpression(node.getInitializer());
            assignedVal = exprToString(node.getInitializer());
        }

        if (TYPE_INFERRED.equals(typeStr)) {
            if (!isInitialized) {
                throw semanticError(node.getNameToken(),
                        String.format("Nao foi possivel inferir o tipo de '%s'.", node.getNameToken().getValue()));
            }
            typeStr = expressionType;
        } else if (isInitialized) {
            validateAssignmentTypes(node.getNameToken(), typeStr, expressionType);
        }

        addSymbol(node.getNameToken(), typeStr, node.isConst() ? "CONST" : "VAR", currentScopeLevel, isInitialized,
                assignedVal);
    }

    private void visitTypeDecl(TypeDeclNode node) {
        if (node.getKind() == TypeDeclNode.TypeDeclKind.STRUCT) {
            for (TypeNode fieldType : node.getStructFieldTypes()) {
                resolveTypeName(fieldType);
            }
            addSymbol(node.getNameToken(), "struct", "TYPE", currentScopeLevel, false, null);
        } else if (node.getKind() == TypeDeclNode.TypeDeclKind.INTERFACE) {
            for (MethodSigNode method : node.getInterfaceMethods()) {
                for (TypeNode paramType : method.getParamTypes()) {
                    resolveTypeName(paramType);
                }
                for (TypeNode returnType : method.getReturnTypes()) {
                    resolveTypeName(returnType);
                }
            }
            addSymbol(node.getNameToken(), "interface", "TYPE", currentScopeLevel, false, null);
        } else {
            resolveTypeName(node.getAliasedType());
            addSymbol(node.getNameToken(), "alias", "TYPE", currentScopeLevel, false, null);
        }
    }

    private void visitFuncDecl(FuncDeclNode node) {
        Symbol functionSymbol = addSymbol(node.getNameToken(), "func", "FUNC", currentScopeLevel, false, null);

        List<String> parameterTypes = new ArrayList<>();
        for (ParamNode param : node.getParams()) {
            parameterTypes.add(resolveTypeName(param.getType()));
        }

        List<String> returnTypes = new ArrayList<>();
        for (TypeNode returnType : node.getReturnTypes()) {
            returnTypes.add(resolveTypeName(returnType));
        }

        functionSymbol.setParameterTypes(parameterTypes);
        functionSymbol.setReturnTypes(returnTypes);

        visitBlock(node.getBody(), node.getParams());
    }

    private String resolveTypeName(TypeNode type) {
        if (type instanceof PointerTypeNode pointerType) {
            return "*" + resolveTypeName(pointerType.getBase());
        } else if (type instanceof SliceTypeNode sliceType) {
            return "slice_" + resolveTypeName(sliceType.getElement());
        } else if (type instanceof ArrayTypeNode arrayType) {
            String dimensionType = evaluateExpression(arrayType.getDimension());
            if (!isIntegerType(dimensionType) && !TYPE_UNKNOWN.equals(normalizeType(dimensionType))) {
                throw semanticError(firstToken(arrayType.getDimension()), "A dimensao do array deve ser inteira.");
            }
            return "array_" + resolveTypeName(arrayType.getElement());
        } else if (type instanceof MapTypeNode mapType) {
            resolveTypeName(mapType.getKeyType());
            return "map_" + resolveTypeName(mapType.getValueType());
        } else if (type instanceof ChanTypeNode chanType) {
            return "chan_" + resolveTypeName(chanType.getElement());
        } else if (type instanceof NamedTypeNode namedType) {
            String parsedType = normalizeType(namedType.getNameToken().getValue());
            if (!isKnownType(parsedType)) {
                throw semanticError(namedType.getNameToken(),
                        String.format("Tipo '%s' nao declarado.", namedType.getNameToken().getValue()));
            }
            return parsedType;
        }

        return TYPE_UNKNOWN;
    }

    private void visitBlock(BlockNode block) {
        visitBlock(block, Collections.emptyList());
    }

    private void visitBlock(BlockNode block, List<ParamNode> preDeclared) {
        currentScopeLevel++;
        try {
            for (ParamNode param : preDeclared) {
                addSymbol(param.getNameToken(), resolveTypeName(param.getType()), "PARAM", currentScopeLevel, true,
                        null);
            }

            for (StmtNode stmt : block.getStatements()) {
                try {
                    visitStatement(stmt);
                } catch (SyntaxException e) {
                    reportError(e);
                }
            }
        } finally {
            removeSymbolsFromScope(currentScopeLevel);
            currentScopeLevel--;
        }
    }

    private void visitStatement(StmtNode stmt) {
        if (stmt instanceof VarDeclNode varDecl) {
            visitVarDecl(varDecl);
        } else if (stmt instanceof IfStmtNode ifStmt) {
            visitIfStmt(ifStmt);
        } else if (stmt instanceof ForStmtNode forStmt) {
            visitForStmt(forStmt);
        } else if (stmt instanceof RangeForStmtNode rangeForStmt) {
            visitRangeForStmt(rangeForStmt);
        } else if (stmt instanceof SwitchStmtNode switchStmt) {
            visitSwitchStmt(switchStmt);
        } else if (stmt instanceof ReturnStmtNode returnStmt) {
            visitReturnStmt(returnStmt);
        } else if (stmt instanceof KeywordStmtNode) {
            // break/continue/fallthrough: nenhuma verificacao semantica exigida
        } else if (stmt instanceof ExprStmtNode exprStmt) {
            visitExprStmt(exprStmt);
        } else if (stmt instanceof AssignStmtNode assignStmt) {
            visitAssignStmt(assignStmt);
        } else if (stmt instanceof ShortVarDeclStmtNode shortVarDecl) {
            visitShortVarDeclStmt(shortVarDecl);
        } else if (stmt instanceof IncDecStmtNode incDecStmt) {
            visitIncDecStmt(incDecStmt);
        }
    }

    private void visitReturnStmt(ReturnStmtNode node) {
        for (ExprNode value : node.getValues()) {
            evaluateExpression(value);
        }
    }

    private void visitExprStmt(ExprStmtNode node) {
        evaluateExpression(node.getExpr());
    }

    private void visitAssignStmt(AssignStmtNode node) {
        if (node.getTarget() instanceof IdentifierExprNode idExpr) {
            Token idToken = idExpr.getToken();
            String rightType = evaluateExpression(node.getValue());

            Symbol symbol = lookupSymbol(idToken.getValue());
            if (symbol == null) {
                throw semanticError(idToken, String.format("Variavel '%s' nao declarada.", idToken.getValue()));
            }

            validateAssignmentOperator(node.getOperatorToken(), symbol.getType(), rightType);
            symbol.setInitialized(true);
        } else {
            String leftType = evaluateExpression(node.getTarget());
            String rightType = evaluateExpression(node.getValue());
            validateAssignmentOperator(node.getOperatorToken(), leftType, rightType);
        }
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

    private void visitShortVarDeclStmt(ShortVarDeclStmtNode node) {
        String rightType = evaluateExpression(node.getValue());

        if (isDeclaredInCurrentScope(node.getNameToken().getValue())) {
            throw semanticError(node.getNameToken(),
                    String.format("O identificador '%s' ja foi declarado neste escopo.", node.getNameToken().getValue()));
        }
        addSymbol(node.getNameToken(), rightType, "VAR", currentScopeLevel, true, null);
    }

    private void visitIncDecStmt(IncDecStmtNode node) {
        if (node.getTarget() instanceof IdentifierExprNode idExpr) {
            Token idToken = idExpr.getToken();
            Symbol symbol = lookupSymbol(idToken.getValue());
            if (symbol == null) {
                throw semanticError(idToken, String.format("Variavel '%s' nao declarada.", idToken.getValue()));
            }
            if (!isNumericOrUnknown(symbol.getType())) {
                throw semanticError(idToken,
                        String.format("Operador '%s' exige variavel numerica.", node.getOperatorToken().getValue()));
            }
        } else {
            String targetType = evaluateExpression(node.getTarget());
            if (!isNumericOrUnknown(targetType)) {
                throw semanticError(node.getOperatorToken(),
                        String.format("Operador '%s' exige variavel numerica.", node.getOperatorToken().getValue()));
            }
        }
    }

    private void visitIfStmt(IfStmtNode node) {
        String conditionType = evaluateExpression(node.getCondition());
        validateBoolExpression(node.getIfToken(), conditionType, "if");
        visitBlock(node.getThenBlock());

        if (node.getElseBranch() instanceof BlockNode elseBlock) {
            visitBlock(elseBlock);
        } else if (node.getElseBranch() instanceof IfStmtNode elseIf) {
            visitIfStmt(elseIf);
        }
    }

    private void visitForStmt(ForStmtNode node) {
        currentScopeLevel++;
        try {
            if (node.getInit() != null) {
                visitStatement(node.getInit());
            }

            if (node.getCondition() != null) {
                String conditionType = evaluateExpression(node.getCondition());
                validateBoolExpression(node.getForToken(), conditionType, "for");
            }

            if (node.getPost() != null) {
                visitStatement(node.getPost());
            }

            visitBlock(node.getBody());
        } finally {
            removeSymbolsFromScope(currentScopeLevel);
            currentScopeLevel--;
        }
    }

    private void visitRangeForStmt(RangeForStmtNode node) {
        String rangeType = evaluateExpression(node.getRangeExpr());
        validateRangeableExpression(node.getRangeToken(), rangeType);

        currentScopeLevel++;
        try {
            if (node.getKeyToken() != null) {
                if (isDeclaredInCurrentScope(node.getKeyToken().getValue())) {
                    throw semanticError(node.getKeyToken(),
                            String.format("O identificador '%s' ja foi declarado neste escopo.",
                                    node.getKeyToken().getValue()));
                }
                addSymbol(node.getKeyToken(), TYPE_UNKNOWN, "VAR", currentScopeLevel, true, null);
            }

            if (node.getValueToken() != null) {
                if (isDeclaredInCurrentScope(node.getValueToken().getValue())) {
                    throw semanticError(node.getValueToken(),
                            String.format("O identificador '%s' ja foi declarado neste escopo.",
                                    node.getValueToken().getValue()));
                }
                addSymbol(node.getValueToken(), TYPE_UNKNOWN, "VAR", currentScopeLevel, true, null);
            }

            visitBlock(node.getBody());
        } finally {
            removeSymbolsFromScope(currentScopeLevel);
            currentScopeLevel--;
        }
    }

    private void visitSwitchStmt(SwitchStmtNode node) {
        String switchType = TYPE_BOOL;
        if (node.getTagExpr() != null) {
            switchType = evaluateExpression(node.getTagExpr());
        }

        currentScopeLevel++;
        try {
            for (CaseClauseNode caseClause : node.getCases()) {
                for (ExprNode valueExpr : caseClause.getValues()) {
                    try {
                        String caseType = evaluateExpression(valueExpr);
                        validateCaseType(firstToken(valueExpr), switchType, caseType);
                    } catch (SyntaxException e) {
                        reportError(e);
                    }
                }

                currentScopeLevel++;
                try {
                    for (StmtNode stmt : caseClause.getBody()) {
                        try {
                            visitStatement(stmt);
                        } catch (SyntaxException e) {
                            reportError(e);
                        }
                    }
                } finally {
                    removeSymbolsFromScope(currentScopeLevel);
                    currentScopeLevel--;
                }
            }
        } finally {
            removeSymbolsFromScope(currentScopeLevel);
            currentScopeLevel--;
        }
    }

    private String evaluateExpression(ExprNode expr) {
        if (expr instanceof LiteralExprNode literalExpr) {
            return evaluateLiteral(literalExpr.getToken());
        } else if (expr instanceof IdentifierExprNode identifierExpr) {
            return evaluateIdentifier(identifierExpr.getToken());
        } else if (expr instanceof BinaryExprNode binaryExpr) {
            return evaluateBinary(binaryExpr);
        } else if (expr instanceof UnaryExprNode unaryExpr) {
            return evaluateUnary(unaryExpr);
        } else if (expr instanceof CallExprNode callExpr) {
            return evaluateCall(callExpr);
        } else if (expr instanceof IndexExprNode indexExpr) {
            return evaluateIndex(indexExpr);
        } else if (expr instanceof SliceExprNode sliceExpr) {
            return evaluateSlice(sliceExpr);
        } else if (expr instanceof FieldAccessExprNode fieldAccessExpr) {
            evaluateExpression(fieldAccessExpr.getTarget());
            return TYPE_UNKNOWN;
        }

        return TYPE_UNKNOWN;
    }

    private String evaluateLiteral(Token token) {
        TokenType type = token.getType();

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
        if (type == TokenType.NIL) {
            return TYPE_NIL;
        }

        return TYPE_UNKNOWN;
    }

    private String evaluateIdentifier(Token idToken) {
        String name = idToken.getValue();
        Symbol symbol = lookupSymbol(name);
        boolean importedPackage = importedPackages.contains(name);

        if (symbol == null && !importedPackage) {
            throw semanticError(idToken, String.format("Variavel '%s' nao declarada.", name));
        }

        return symbol != null ? symbol.getType() : TYPE_UNKNOWN;
    }

    private String evaluateBinary(BinaryExprNode node) {
        TokenType opType = node.getOperatorToken().getType();
        Token operatorToken = node.getOperatorToken();
        String leftType = evaluateExpression(node.getLeft());
        String rightType = evaluateExpression(node.getRight());

        if (opType == TokenType.LOGICAL_OR || opType == TokenType.LOGICAL_AND) {
            if (!TYPE_BOOL.equals(normalizeType(leftType)) || !TYPE_BOOL.equals(normalizeType(rightType))) {
                throw semanticError(operatorToken,
                        String.format("Operador '%s' exige operandos bool.", operatorToken.getValue()));
            }
            return TYPE_BOOL;
        }

        if (isRelationalOperator(opType)) {
            if (opType == TokenType.EQUAL || opType == TokenType.NOT_EQUAL) {
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
            return TYPE_BOOL;
        }

        if (opType == TokenType.PLUS || opType == TokenType.MINUS) {
            if (opType == TokenType.PLUS
                    && (TYPE_STRING.equals(normalizeType(leftType)) || TYPE_STRING.equals(normalizeType(rightType)))) {
                if (!TYPE_STRING.equals(normalizeType(leftType)) || !TYPE_STRING.equals(normalizeType(rightType))) {
                    throw semanticError(operatorToken, "Concatenacao exige dois operandos string.");
                }
                return TYPE_STRING;
            }
            return combineNumericTypes(operatorToken, leftType, rightType);
        }

        if (opType == TokenType.MULTIPLY || opType == TokenType.DIVIDE || opType == TokenType.MOD) {
            if (opType == TokenType.MOD
                    && (!isIntegerOrUnknown(leftType) || !isIntegerOrUnknown(rightType))) {
                throw semanticError(operatorToken, "Operador '%' exige operandos inteiros.");
            }
            return combineNumericTypes(operatorToken, leftType, rightType);
        }

        return TYPE_UNKNOWN;
    }

    private String evaluateUnary(UnaryExprNode node) {
        TokenType opType = node.getOperatorToken().getType();
        Token operatorToken = node.getOperatorToken();

        if (opType == TokenType.MINUS) {
            String unaryType = evaluateExpression(node.getOperand());
            if (!isNumericOrUnknown(unaryType)) {
                throw semanticError(operatorToken, "Operador '-' exige operando numerico.");
            }
            return unaryType;
        } else if (opType == TokenType.LOGICAL_NOT) {
            String unaryType = evaluateExpression(node.getOperand());
            if (!TYPE_BOOL.equals(normalizeType(unaryType))) {
                throw semanticError(operatorToken, "Operador '!' exige operando bool.");
            }
            return TYPE_BOOL;
        } else if (opType == TokenType.BITWISE_AND) {
            String operandType = evaluateExpression(node.getOperand());
            return "*" + operandType;
        } else if (opType == TokenType.MULTIPLY) {
            String unaryType = evaluateExpression(node.getOperand());
            if (normalizeType(unaryType).startsWith("*")) {
                return unaryType.substring(1);
            }
            if (TYPE_UNKNOWN.equals(normalizeType(unaryType))) {
                return TYPE_UNKNOWN;
            }
            throw semanticError(operatorToken, "Operador '*' exige ponteiro.");
        } else if (opType == TokenType.ARROW) {
            evaluateExpression(node.getOperand());
            return TYPE_UNKNOWN;
        }

        return TYPE_UNKNOWN;
    }

    private String evaluateCall(CallExprNode node) {
        if (node.getCallee() instanceof IdentifierExprNode idExpr) {
            Token idToken = idExpr.getToken();
            String name = idToken.getValue();

            if (isBuiltInFunction(name)) {
                List<String> argumentTypes = evaluateArguments(node.getArgs());
                return getBuiltInFunctionReturnType(name, argumentTypes);
            }

            Symbol symbol = lookupSymbol(name);
            boolean importedPackage = importedPackages.contains(name);

            if (isKnownType(name) && (symbol == null || "TYPE".equals(symbol.getCategory()))) {
                evaluateArguments(node.getArgs());
                return normalizeType(name);
            }

            if (symbol == null && !importedPackage) {
                throw semanticError(idToken, String.format("Variavel '%s' nao declarada.", name));
            }

            if (symbol != null && "FUNC".equals(symbol.getCategory())) {
                List<String> argumentTypes = evaluateArguments(node.getArgs());
                return validateFunctionCall(idToken, symbol, argumentTypes);
            }

            evaluateArguments(node.getArgs());
            return TYPE_UNKNOWN;
        }

        evaluateExpression(node.getCallee());
        evaluateArguments(node.getArgs());
        return TYPE_UNKNOWN;
    }

    private List<String> evaluateArguments(List<ExprNode> args) {
        List<String> types = new ArrayList<>();
        for (ExprNode arg : args) {
            types.add(evaluateExpression(arg));
        }
        return types;
    }

    private String evaluateIndex(IndexExprNode node) {
        String collectionType = evaluateExpression(node.getCollection());

        if (node.getIndex() != null) {
            String indexType = evaluateExpression(node.getIndex());
            if (!isIntegerType(indexType) && !TYPE_UNKNOWN.equals(normalizeType(indexType))) {
                throw semanticError(firstToken(node.getIndex()), "Indice deve ser inteiro.");
            }
        }

        return unwrapCollectionType(collectionType);
    }

    private String evaluateSlice(SliceExprNode node) {
        String collectionType = evaluateExpression(node.getCollection());

        if (node.getLow() != null) {
            String lowType = evaluateExpression(node.getLow());
            if (!isIntegerType(lowType) && !TYPE_UNKNOWN.equals(normalizeType(lowType))) {
                throw semanticError(firstToken(node.getLow()), "Indice deve ser inteiro.");
            }
        }

        if (node.getHigh() != null) {
            String highType = evaluateExpression(node.getHigh());
            if (!isIntegerType(highType) && !TYPE_UNKNOWN.equals(normalizeType(highType))) {
                throw semanticError(firstToken(node.getHigh()), "Indice deve ser inteiro.");
            }
        }

        return unwrapCollectionType(collectionType);
    }

    private String unwrapCollectionType(String collectionType) {
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

    private boolean isRelationalOperator(TokenType type) {
        return type == TokenType.EQUAL || type == TokenType.NOT_EQUAL
                || type == TokenType.GREATER || type == TokenType.GREATER_EQUAL
                || type == TokenType.LESS || type == TokenType.LESS_EQUAL;
    }

    private Token firstToken(ExprNode expr) {
        if (expr instanceof IdentifierExprNode e) {
            return e.getToken();
        } else if (expr instanceof LiteralExprNode e) {
            return e.getToken();
        } else if (expr instanceof BinaryExprNode e) {
            return firstToken(e.getLeft());
        } else if (expr instanceof UnaryExprNode e) {
            return e.getOperatorToken();
        } else if (expr instanceof CallExprNode e) {
            return firstToken(e.getCallee());
        } else if (expr instanceof IndexExprNode e) {
            return firstToken(e.getCollection());
        } else if (expr instanceof SliceExprNode e) {
            return firstToken(e.getCollection());
        } else if (expr instanceof FieldAccessExprNode e) {
            return firstToken(e.getTarget());
        }

        return null;
    }

    private String exprToString(ExprNode expr) {
        if (expr instanceof IdentifierExprNode e) {
            return e.getToken().getValue();
        } else if (expr instanceof LiteralExprNode e) {
            return e.getToken().getValue();
        } else if (expr instanceof BinaryExprNode e) {
            return exprToString(e.getLeft()) + " " + e.getOperatorToken().getValue() + " " + exprToString(e.getRight());
        } else if (expr instanceof UnaryExprNode e) {
            return e.getOperatorToken().getValue() + exprToString(e.getOperand());
        } else if (expr instanceof CallExprNode e) {
            StringBuilder builder = new StringBuilder(exprToString(e.getCallee())).append("(");
            for (int i = 0; i < e.getArgs().size(); i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(exprToString(e.getArgs().get(i)));
            }
            return builder.append(")").toString();
        } else if (expr instanceof IndexExprNode e) {
            return exprToString(e.getCollection()) + "[" + (e.getIndex() != null ? exprToString(e.getIndex()) : "") + "]";
        } else if (expr instanceof SliceExprNode e) {
            return exprToString(e.getCollection()) + "[" + (e.getLow() != null ? exprToString(e.getLow()) : "")
                    + ":" + (e.getHigh() != null ? exprToString(e.getHigh()) : "") + "]";
        } else if (expr instanceof FieldAccessExprNode e) {
            return exprToString(e.getTarget()) + "." + e.getFieldToken().getValue();
        }

        return "";
    }

    public int getErrorLine() {
        return errorLine;
    }

    public List<Integer> getErrorLines() {
        return Collections.unmodifiableList(errorLines);
    }
}
