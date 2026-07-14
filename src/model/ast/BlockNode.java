package model.ast;

import java.util.List;

public class BlockNode implements StmtNode {
    private final List<StmtNode> statements;

    public BlockNode(List<StmtNode> statements) {
        this.statements = statements;
    }

    public List<StmtNode> getStatements() {
        return statements;
    }
}
