package model.ast;

import model.entities.Token;

public class IncDecStmtNode implements StmtNode {
    private final ExprNode target;
    private final Token operatorToken;

    public IncDecStmtNode(ExprNode target, Token operatorToken) {
        this.target = target;
        this.operatorToken = operatorToken;
    }

    public ExprNode getTarget() {
        return target;
    }

    public Token getOperatorToken() {
        return operatorToken;
    }
}
