package model.ast;

import java.util.List;

import model.entities.Token;

public class ReturnStmtNode implements StmtNode {
    private final Token returnToken;
    private final List<ExprNode> values;

    public ReturnStmtNode(Token returnToken, List<ExprNode> values) {
        this.returnToken = returnToken;
        this.values = values;
    }

    public Token getReturnToken() {
        return returnToken;
    }

    public List<ExprNode> getValues() {
        return values;
    }
}
