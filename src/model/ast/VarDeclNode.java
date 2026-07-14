package model.ast;

import model.entities.Token;

public class VarDeclNode implements DeclNode, StmtNode {
    private final Token nameToken;
    private final TypeNode declaredType;
    private final ExprNode initializer;
    private final boolean isConst;

    public VarDeclNode(Token nameToken, TypeNode declaredType, ExprNode initializer, boolean isConst) {
        this.nameToken = nameToken;
        this.declaredType = declaredType;
        this.initializer = initializer;
        this.isConst = isConst;
    }

    public Token getNameToken() {
        return nameToken;
    }

    public TypeNode getDeclaredType() {
        return declaredType;
    }

    public ExprNode getInitializer() {
        return initializer;
    }

    public boolean isConst() {
        return isConst;
    }
}
