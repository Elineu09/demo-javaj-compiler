package model.ast;

import model.entities.Token;

public class AssignStmtNode implements StmtNode {
    private final ExprNode target;
    private final Token operatorToken;
    private final ExprNode value;

    public AssignStmtNode(ExprNode target, Token operatorToken, ExprNode value) {
        this.target = target;
        this.operatorToken = operatorToken;
        this.value = value;
    }

    public ExprNode getTarget() {
        return target;
    }

    public Token getOperatorToken() {
        return operatorToken;
    }

    public ExprNode getValue() {
        return value;
    }
}
