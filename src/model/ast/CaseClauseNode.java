package model.ast;

import java.util.List;

public class CaseClauseNode {
    private final List<ExprNode> values;
    private final boolean isDefault;
    private final List<StmtNode> body;

    public CaseClauseNode(List<ExprNode> values, boolean isDefault, List<StmtNode> body) {
        this.values = values;
        this.isDefault = isDefault;
        this.body = body;
    }

    public List<ExprNode> getValues() {
        return values;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public List<StmtNode> getBody() {
        return body;
    }
}
