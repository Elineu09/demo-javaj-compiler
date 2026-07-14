package model.ast;

import model.entities.Token;

public class BinaryExprNode implements ExprNode {
    private final ExprNode left;
    private final Token operatorToken;
    private final ExprNode right;

    public BinaryExprNode(ExprNode left, Token operatorToken, ExprNode right) {
        this.left = left;
        this.operatorToken = operatorToken;
        this.right = right;
    }

    public ExprNode getLeft() {
        return left;
    }

    public Token getOperatorToken() {
        return operatorToken;
    }

    public ExprNode getRight() {
        return right;
    }
}
