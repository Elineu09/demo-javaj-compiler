package model.ast;

import java.util.List;

public class ProgramNode {
    private final String packageName;
    private final List<String> imports;
    private final List<DeclNode> declarations;

    public ProgramNode(String packageName, List<String> imports, List<DeclNode> declarations) {
        this.packageName = packageName;
        this.imports = imports;
        this.declarations = declarations;
    }

    public String getPackageName() {
        return packageName;
    }

    public List<String> getImports() {
        return imports;
    }

    public List<DeclNode> getDeclarations() {
        return declarations;
    }
}
