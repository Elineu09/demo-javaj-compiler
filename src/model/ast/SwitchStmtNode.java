package model.ast;

import java.util.List;

import model.entities.Token;

public class SwitchStmtNode implements StmtNode {
    private final Token switchToken;
    private final ExprNode tagExpr;
    private final List<CaseClauseNode> cases;

    public SwitchStmtNode(Token switchToken, ExprNode tagExpr, List<CaseClauseNode> cases) {
        this.switchToken = switchToken;
        this.tagExpr = tagExpr;
        this.cases = cases;
    }

    public Token getSwitchToken() {
        return switchToken;
    }

    public ExprNode getTagExpr() {
        return tagExpr;
    }

    public List<CaseClauseNode> getCases() {
        return cases;
    }
}
