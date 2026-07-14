package model.ast;

import model.entities.Token;

public class UnaryExprNode implements ExprNode {
    private final Token operatorToken;
    private final ExprNode operand;

    public UnaryExprNode(Token operatorToken, ExprNode operand) {
        this.operatorToken = operatorToken;
        this.operand = operand;
    }

    public Token getOperatorToken() {
        return operatorToken;
    }

    public ExprNode getOperand() {
        return operand;
    }
}
