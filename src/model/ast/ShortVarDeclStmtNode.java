package model.ast;

import model.entities.Token;

public class ShortVarDeclStmtNode implements StmtNode {
    private final Token nameToken;
    private final ExprNode value;

    public ShortVarDeclStmtNode(Token nameToken, ExprNode value) {
        this.nameToken = nameToken;
        this.value = value;
    }

    public Token getNameToken() {
        return nameToken;
    }

    public ExprNode getValue() {
        return value;
    }
}
