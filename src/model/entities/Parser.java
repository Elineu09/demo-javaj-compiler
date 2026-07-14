package model.entities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

public class Parser {

    private int errorLine = 0;

    private List<String> errors;
    private List<Integer> errorLines;

    private List<Token> tokens;
    private int currentTokenIndex;
    private Token currentToken;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
        this.currentTokenIndex = 0;
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

    public ProgramNode parse() {
        ProgramNode program = parseProgram();

        if (errors.isEmpty()) {
            System.out.println("\nAnalise Sintatica concluida com sucesso!\n");
        } else {
            System.out.println("\nForam encontrados " + errors.size() + " erros sintaticos.\n");
            errors.forEach(System.err::println);
        }

        return program;
    }

    private ProgramNode parseProgram() {
        String packageName = null;
        try {
            match(TokenType.PACKAGE);
            packageName = match(TokenType.IDENTIFIER).getValue();
        } catch (SyntaxException e) {
            reportError(e);
            recoverDeclaration();
        }

        List<String> imports = parseImports();
        List<DeclNode> declarations = parseTopLevelDeclarations();

        try {
            match(TokenType.EOF);
        } catch (SyntaxException e) {
            reportError(e);
        }

        return new ProgramNode(packageName, imports, declarations);
    }

    private List<String> parseImports() {
        List<String> imports = new ArrayList<>();

        while (currentToken.getType() == TokenType.IMPORT) {
            try {
                advance();
                if (currentToken.getType() == TokenType.LPAREN) {
                    advance();
                    while (currentToken.getType() == TokenType.STRING_LITERAL) {
                        addImportName(imports, currentToken);
                        advance();
                    }
                    match(TokenType.RPAREN);
                } else {
                    addImportName(imports, match(TokenType.STRING_LITERAL));
                }
            } catch (SyntaxException e) {
                reportError(e);
                recoverDeclaration();
            }
        }

        return imports;
    }

    private void addImportName(List<String> imports, Token importToken) {
        String value = importToken.getValue().replace("\"", "").replace("`", "");
        if (value.isBlank()) {
            return;
        }

        int slashIndex = value.lastIndexOf("/");
        if (slashIndex >= 0 && slashIndex < value.length() - 1) {
            value = value.substring(slashIndex + 1);
        }

        imports.add(value);
    }

    private List<DeclNode> parseTopLevelDeclarations() {
        List<DeclNode> declarations = new ArrayList<>();

        while (currentToken.getType() != TokenType.EOF) {
            try {
                if (currentToken.getType() == TokenType.SEMICOLON) {
                    advance();
                    continue;
                }

                TokenType type = currentToken.getType();
                if (type == TokenType.VAR || type == TokenType.CONST) {
                    declarations.add(parseVarOrConstDeclaration());
                } else if (type == TokenType.TYPE) {
                    declarations.add(parseTypeDeclaration());
                } else if (type == TokenType.FUNC) {
                    declarations.add(parseFuncDeclaration());
                } else {
                    throw syntaxError("Declaracao de topo invalida.");
                }
            } catch (SyntaxException e) {
                reportError(e);
                recoverDeclaration();
            }
        }

        return declarations;
    }

    private VarDeclNode parseVarOrConstDeclaration() {
        boolean isConst = currentToken.getType() == TokenType.CONST;
        advance();

        Token idToken = match(TokenType.IDENTIFIER);
        TypeNode declaredType = null;

        if (currentToken.getType() != TokenType.ASSIGN) {
            declaredType = parseType();
        }

        ExprNode initializer = null;
        if (currentToken.getType() == TokenType.ASSIGN) {
            advance();
            initializer = parseExpression();
        }

        return new VarDeclNode(idToken, declaredType, initializer, isConst);
    }

    private TypeDeclNode parseTypeDeclaration() {
        match(TokenType.TYPE);
        Token idToken = match(TokenType.IDENTIFIER);

        if (currentToken.getType() == TokenType.STRUCT) {
            advance();
            match(TokenType.LBRACE);
            List<TypeNode> fieldTypes = new ArrayList<>();
            while (currentToken.getType() == TokenType.IDENTIFIER) {
                advance();
                fieldTypes.add(parseType());
            }
            match(TokenType.RBRACE);
            return new TypeDeclNode(idToken, TypeDeclNode.TypeDeclKind.STRUCT, fieldTypes, null, null);
        } else if (currentToken.getType() == TokenType.INTERFACE) {
            advance();
            match(TokenType.LBRACE);
            List<MethodSigNode> methods = new ArrayList<>();
            while (currentToken.getType() == TokenType.IDENTIFIER) {
                advance();
                match(TokenType.LPAREN);
                List<ParamNode> params = parseOptionalParameters();
                match(TokenType.RPAREN);

                List<TypeNode> returnTypes = new ArrayList<>();
                if (currentToken.getType() != TokenType.RBRACE) {
                    returnTypes = parseFuncReturnTypes();
                }

                List<TypeNode> paramTypes = new ArrayList<>();
                for (ParamNode param : params) {
                    paramTypes.add(param.getType());
                }
                methods.add(new MethodSigNode(paramTypes, returnTypes));
            }
            match(TokenType.RBRACE);
            return new TypeDeclNode(idToken, TypeDeclNode.TypeDeclKind.INTERFACE, null, methods, null);
        } else {
            TypeNode aliasedType = parseType();
            return new TypeDeclNode(idToken, TypeDeclNode.TypeDeclKind.ALIAS, null, null, aliasedType);
        }
    }

    private FuncDeclNode parseFuncDeclaration() {
        match(TokenType.FUNC);

        Token receiverNameToken = null;
        TypeNode receiverType = null;
        if (currentToken.getType() == TokenType.LPAREN) {
            advance();
            receiverNameToken = match(TokenType.IDENTIFIER);
            receiverType = parseType();
            match(TokenType.RPAREN);
        }

        Token idToken = match(TokenType.IDENTIFIER);

        match(TokenType.LPAREN);
        List<ParamNode> params = parseOptionalParameters();
        match(TokenType.RPAREN);

        List<TypeNode> returnTypes = new ArrayList<>();
        if (currentToken.getType() != TokenType.LBRACE) {
            returnTypes = parseFuncReturnTypes();
        }

        BlockNode body = parseBlock();

        return new FuncDeclNode(receiverNameToken, receiverType, idToken, params, returnTypes, body);
    }

    private List<ParamNode> parseOptionalParameters() {
        List<ParamNode> parameters = new ArrayList<>();

        if (currentToken.getType() == TokenType.IDENTIFIER) {
            Token parameterToken = match(TokenType.IDENTIFIER);
            TypeNode parameterType = parseType();
            parameters.add(new ParamNode(parameterToken, parameterType));

            while (currentToken.getType() == TokenType.COMMA) {
                advance();
                parameterToken = match(TokenType.IDENTIFIER);
                parameterType = parseType();
                parameters.add(new ParamNode(parameterToken, parameterType));
            }
        }

        return parameters;
    }

    private List<TypeNode> parseFuncReturnTypes() {
        List<TypeNode> returnTypes = new ArrayList<>();

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

    private TypeNode parseType() {
        if (currentToken.getType() == TokenType.MULTIPLY) {
            advance();
            return new PointerTypeNode(parseType());
        } else if (currentToken.getType() == TokenType.LBRACKET) {
            advance();
            if (currentToken.getType() == TokenType.RBRACKET) {
                advance();
                return new SliceTypeNode(parseType());
            } else {
                ExprNode dimension = parseExpression();
                match(TokenType.RBRACKET);
                return new ArrayTypeNode(dimension, parseType());
            }
        } else if (currentToken.getType() == TokenType.MAP) {
            advance();
            match(TokenType.LBRACKET);
            TypeNode keyType = parseType();
            match(TokenType.RBRACKET);
            return new MapTypeNode(keyType, parseType());
        } else if (currentToken.getType() == TokenType.CHAN) {
            advance();
            return new ChanTypeNode(parseType());
        } else {
            Token typeToken = match(TokenType.IDENTIFIER);
            return new NamedTypeNode(typeToken);
        }
    }

    private BlockNode parseBlock() {
        match(TokenType.LBRACE);
        List<StmtNode> statements = new ArrayList<>();

        while (currentToken.getType() != TokenType.RBRACE && currentToken.getType() != TokenType.EOF) {
            try {
                statements.add(parseStatement());
                if (currentToken.getType() == TokenType.SEMICOLON) {
                    advance();
                }
            } catch (SyntaxException e) {
                reportError(e);
                recoverStatement();
            }
        }

        match(TokenType.RBRACE);
        return new BlockNode(statements);
    }

    private StmtNode parseStatement() {
        validateToken();
        TokenType type = currentToken.getType();

        if (type == TokenType.VAR || type == TokenType.CONST) {
            return parseVarOrConstDeclaration();
        } else if (type == TokenType.IF) {
            return parseIfStatement();
        } else if (type == TokenType.FOR) {
            return parseForStatement();
        } else if (type == TokenType.SWITCH) {
            return parseSwitchStatement();
        } else if (type == TokenType.RETURN) {
            return parseReturnStatement();
        } else if (type == TokenType.BREAK || type == TokenType.CONTINUE || type == TokenType.FALLTHROUGH) {
            Token keywordToken = currentToken;
            advance();
            return new KeywordStmtNode(keywordToken);
        } else if (type == TokenType.GO || type == TokenType.DEFER) {
            Token keywordToken = currentToken;
            advance();
            ExprNode expr = parseExpression();
            return new ExprStmtNode(keywordToken, expr);
        } else if (type == TokenType.ELSE) {
            throw syntaxError("'else' sem um 'if' correspondente.");
        } else if (type == TokenType.IDENTIFIER) {
            return parseIdentifierStatement();
        } else if (type == TokenType.MULTIPLY) {
            ExprNode baseExpr = parseExpression();
            return parseStatementTail(baseExpr);
        } else {
            return new ExprStmtNode(null, parseExpression());
        }
    }

    private ReturnStmtNode parseReturnStatement() {
        Token returnToken = currentToken;
        advance();

        List<ExprNode> values = new ArrayList<>();
        if (currentToken.getType() != TokenType.RBRACE && currentToken.getType() != TokenType.SEMICOLON) {
            values.add(parseExpression());
            while (currentToken.getType() == TokenType.COMMA) {
                advance();
                values.add(parseExpression());
            }
        }

        return new ReturnStmtNode(returnToken, values);
    }

    private StmtNode parseIdentifierStatement() {
        Token idToken = currentToken;
        TokenType nextType = peekType(1);

        if (isDirectAssignmentOperator(nextType)) {
            advance();
            Token operatorToken = currentToken;
            advance();
            ExprNode valueExpr = parseExpression();

            if (operatorToken.getType() == TokenType.DEFINE) {
                return new ShortVarDeclStmtNode(idToken, valueExpr);
            }

            return new AssignStmtNode(new IdentifierExprNode(idToken), operatorToken, valueExpr);
        }

        if (nextType == TokenType.INCREMENT || nextType == TokenType.DECREMENT) {
            advance();
            Token operatorToken = currentToken;
            advance();
            return new IncDecStmtNode(new IdentifierExprNode(idToken), operatorToken);
        }

        ExprNode baseExpr = parseExpression();
        return parseStatementTail(baseExpr);
    }

    private StmtNode parseStatementTail(ExprNode baseExpr) {
        if (isAssignmentOperator(currentToken.getType())) {
            Token operatorToken = currentToken;
            advance();
            ExprNode valueExpr = parseExpression();
            return new AssignStmtNode(baseExpr, operatorToken, valueExpr);
        } else if (currentToken.getType() == TokenType.INCREMENT || currentToken.getType() == TokenType.DECREMENT) {
            Token operatorToken = currentToken;
            advance();
            return new IncDecStmtNode(baseExpr, operatorToken);
        }

        return new ExprStmtNode(null, baseExpr);
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

    private IfStmtNode parseIfStatement() {
        Token ifToken = match(TokenType.IF);
        ExprNode condition = parseExpression();
        BlockNode thenBlock = parseBlock();

        StmtNode elseBranch = null;
        if (currentToken.getType() == TokenType.ELSE) {
            advance();
            if (currentToken.getType() == TokenType.IF) {
                elseBranch = parseIfStatement();
            } else {
                elseBranch = parseBlock();
            }
        }

        return new IfStmtNode(ifToken, condition, thenBlock, elseBranch);
    }

    private SwitchStmtNode parseSwitchStatement() {
        Token switchToken = match(TokenType.SWITCH);
        ExprNode tagExpr = null;
        if (currentToken.getType() != TokenType.LBRACE) {
            tagExpr = parseExpression();
        }
        match(TokenType.LBRACE);

        List<CaseClauseNode> cases = new ArrayList<>();
        while (currentToken.getType() == TokenType.CASE || currentToken.getType() == TokenType.DEFAULT) {
            List<ExprNode> values = new ArrayList<>();
            boolean isDefault = currentToken.getType() == TokenType.DEFAULT;

            if (!isDefault) {
                advance();
                values.add(parseExpression());
                match(TokenType.COLON);
            } else {
                advance();
                match(TokenType.COLON);
            }

            List<StmtNode> body = new ArrayList<>();
            while (currentToken.getType() != TokenType.CASE && currentToken.getType() != TokenType.DEFAULT
                    && currentToken.getType() != TokenType.RBRACE && currentToken.getType() != TokenType.EOF) {
                try {
                    body.add(parseStatement());
                    if (currentToken.getType() == TokenType.SEMICOLON) {
                        advance();
                    }
                } catch (SyntaxException e) {
                    reportError(e);
                    recoverSwitchCase();
                }
            }

            cases.add(new CaseClauseNode(values, isDefault, body));
        }
        match(TokenType.RBRACE);

        return new SwitchStmtNode(switchToken, tagExpr, cases);
    }

    private StmtNode parseForStatement() {
        Token forToken = match(TokenType.FOR);

        if (currentToken.getType() == TokenType.LBRACE) {
            BlockNode body = parseBlock();
            return new ForStmtNode(forToken, null, null, null, body);
        }

        if (isRangeForStart()) {
            return parseRangeForStatement();
        }

        if (currentToken.getType() == TokenType.SEMICOLON) {
            advance();
            ExprNode condition = parseExpression();
            match(TokenType.SEMICOLON);
            StmtNode post = parseStatement();
            BlockNode body = parseBlock();
            return new ForStmtNode(forToken, null, condition, post, body);
        }

        if (currentToken.getType() == TokenType.IDENTIFIER && isDirectAssignmentOperator(peekType(1))) {
            StmtNode init = parseIdentifierStatement();
            if (currentToken.getType() != TokenType.SEMICOLON) {
                throw syntaxError("Declaracao 'for' invalida ou faltando ';'.");
            }

            advance();
            ExprNode condition = parseExpression();
            match(TokenType.SEMICOLON);
            StmtNode post = parseStatement();
            BlockNode body = parseBlock();
            return new ForStmtNode(forToken, init, condition, post, body);
        }

        ExprNode condition = parseExpression();
        BlockNode body = parseBlock();
        return new ForStmtNode(forToken, null, condition, null, body);
    }

    private boolean isRangeForStart() {
        return currentToken.getType() == TokenType.RANGE
                || (currentToken.getType() == TokenType.IDENTIFIER
                        && peekType(1) == TokenType.DEFINE && peekType(2) == TokenType.RANGE)
                || (currentToken.getType() == TokenType.IDENTIFIER
                        && peekType(1) == TokenType.COMMA && peekType(2) == TokenType.IDENTIFIER
                        && peekType(3) == TokenType.DEFINE && peekType(4) == TokenType.RANGE);
    }

    private RangeForStmtNode parseRangeForStatement() {
        if (currentToken.getType() == TokenType.RANGE) {
            Token rangeToken = currentToken;
            advance();
            ExprNode rangeExpr = parseExpression();
            BlockNode body = parseBlock();
            return new RangeForStmtNode(rangeToken, null, null, rangeExpr, body);
        }

        Token firstToken = match(TokenType.IDENTIFIER);
        Token secondToken = null;

        if (currentToken.getType() == TokenType.COMMA) {
            advance();
            secondToken = match(TokenType.IDENTIFIER);
        }

        match(TokenType.DEFINE);
        Token rangeToken = match(TokenType.RANGE);
        ExprNode rangeExpr = parseExpression();
        BlockNode body = parseBlock();

        return new RangeForStmtNode(rangeToken, firstToken, secondToken, rangeExpr, body);
    }

    private ExprNode parseExpression() {
        return parseLogicalOr();
    }

    private ExprNode parseLogicalOr() {
        ExprNode left = parseLogicalAnd();
        while (currentToken.getType() == TokenType.LOGICAL_OR) {
            Token operatorToken = currentToken;
            advance();
            ExprNode right = parseLogicalAnd();
            left = new BinaryExprNode(left, operatorToken, right);
        }
        return left;
    }

    private ExprNode parseLogicalAnd() {
        ExprNode left = parseRelational();
        while (currentToken.getType() == TokenType.LOGICAL_AND) {
            Token operatorToken = currentToken;
            advance();
            ExprNode right = parseRelational();
            left = new BinaryExprNode(left, operatorToken, right);
        }
        return left;
    }

    private ExprNode parseRelational() {
        ExprNode left = parseSum();
        while (isRelationalOperator(currentToken.getType())) {
            Token operatorToken = currentToken;
            advance();
            ExprNode right = parseSum();
            left = new BinaryExprNode(left, operatorToken, right);
        }
        return left;
    }

    private ExprNode parseSum() {
        ExprNode left = parseMult();
        while (currentToken.getType() == TokenType.PLUS || currentToken.getType() == TokenType.MINUS) {
            Token operatorToken = currentToken;
            advance();
            ExprNode right = parseMult();
            left = new BinaryExprNode(left, operatorToken, right);
        }
        return left;
    }

    private ExprNode parseMult() {
        ExprNode left = parseUnary();
        while (currentToken.getType() == TokenType.MULTIPLY || currentToken.getType() == TokenType.DIVIDE
                || currentToken.getType() == TokenType.MOD) {
            Token operatorToken = currentToken;
            advance();
            ExprNode right = parseUnary();
            left = new BinaryExprNode(left, operatorToken, right);
        }
        return left;
    }

    private ExprNode parseUnary() {
        TokenType type = currentToken.getType();
        if (type == TokenType.MINUS || type == TokenType.LOGICAL_NOT || type == TokenType.BITWISE_AND
                || type == TokenType.MULTIPLY || type == TokenType.ARROW) {
            Token operatorToken = currentToken;
            advance();
            ExprNode operand = parseUnary();
            return new UnaryExprNode(operatorToken, operand);
        }

        return parseFactor();
    }

    private ExprNode parseFactor() {
        validateToken();
        TokenType type = currentToken.getType();

        if (isLiteral(type) || type == TokenType.NIL) {
            Token literalToken = currentToken;
            advance();
            return new LiteralExprNode(literalToken);
        } else if (type == TokenType.LPAREN) {
            advance();
            ExprNode expr = parseExpression();
            match(TokenType.RPAREN);
            return expr;
        } else if (type == TokenType.IDENTIFIER) {
            return parseIdentifierFactor();
        }

        throw syntaxError("Fator inesperado na expressao.");
    }

    private ExprNode parseIdentifierFactor() {
        Token idToken = match(TokenType.IDENTIFIER);
        ExprNode result = new IdentifierExprNode(idToken);

        while (true) {
            if (currentToken.getType() == TokenType.DOT) {
                advance();
                Token fieldToken = match(TokenType.IDENTIFIER);
                result = new FieldAccessExprNode(result, fieldToken);
            } else if (currentToken.getType() == TokenType.LPAREN) {
                Token callToken = currentToken;
                advance();
                List<ExprNode> args = parseOptionalArguments();
                match(TokenType.RPAREN);
                result = new CallExprNode(result, args, callToken);
            } else if (currentToken.getType() == TokenType.LBRACKET) {
                result = parseIndexOrSlice(result);
            } else {
                break;
            }
        }

        return result;
    }

    private ExprNode parseIndexOrSlice(ExprNode collection) {
        Token bracketToken = currentToken;
        advance();

        ExprNode first = null;
        if (currentToken.getType() != TokenType.COLON) {
            first = parseExpression();
        }

        if (currentToken.getType() == TokenType.COLON) {
            advance();
            ExprNode second = null;
            if (currentToken.getType() != TokenType.RBRACKET) {
                second = parseExpression();
            }
            match(TokenType.RBRACKET);
            return new SliceExprNode(collection, first, second, bracketToken);
        }

        match(TokenType.RBRACKET);
        return new IndexExprNode(collection, first, bracketToken);
    }

    private List<ExprNode> parseOptionalArguments() {
        List<ExprNode> args = new ArrayList<>();

        if (currentToken.getType() != TokenType.RPAREN) {
            args.add(parseExpression());
            while (currentToken.getType() == TokenType.COMMA) {
                advance();
                args.add(parseExpression());
            }
        }

        return args;
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

    public int getErrorLine() {
        return errorLine;
    }

    public List<Integer> getErrorLines() {
        return Collections.unmodifiableList(errorLines);
    }
}
