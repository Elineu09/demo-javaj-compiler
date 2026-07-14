package model.ast;

import java.util.List;

import model.entities.Token;

public class CallExprNode implements ExprNode {
    private final ExprNode callee;
    private final List<ExprNode> args;
    private final Token callToken;

    public CallExprNode(ExprNode callee, List<ExprNode> args, Token callToken) {
        this.callee = callee;
        this.args = args;
        this.callToken = callToken;
    }

    public ExprNode getCallee() {
        return callee;
    }

    public List<ExprNode> getArgs() {
        return args;
    }

    public Token getCallToken() {
        return callToken;
    }
}
