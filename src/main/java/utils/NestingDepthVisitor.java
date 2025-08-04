package utils;

import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

/**
 * A visitor that calculates the maximum nesting depth of control structures
 * within a visited AST node (typically a method body).
 * An instance of this visitor can be reused by calling the reset() method.
 */
public class NestingDepthVisitor extends VoidVisitorAdapter<Void> {
    private int currentDepth = 0;
    private int maxDepth = 0;

    private void enterNode() {
        currentDepth++;
        if (currentDepth > maxDepth) {
            maxDepth = currentDepth;
        }
    }

    private void exitNode() {
        currentDepth--;
    }

    // Override per i costrutti che aumentano il livello di nidificazione
    @Override public void visit(IfStmt n, Void arg) { enterNode(); super.visit(n, arg); exitNode(); }
    @Override public void visit(ForStmt n, Void arg) { enterNode(); super.visit(n, arg); exitNode(); }
    @Override public void visit(ForEachStmt n, Void arg) { enterNode(); super.visit(n, arg); exitNode(); }
    @Override public void visit(WhileStmt n, Void arg) { enterNode(); super.visit(n, arg); exitNode(); }
    @Override public void visit(DoStmt n, Void arg) { enterNode(); super.visit(n, arg); exitNode(); }
    @Override public void visit(SwitchStmt n, Void arg) { enterNode(); super.visit(n, arg); exitNode(); }
    @Override public void visit(TryStmt n, Void arg) { enterNode(); super.visit(n, arg); exitNode(); }
    @Override public void visit(ConditionalExpr n, Void arg) { enterNode(); super.visit(n, arg); exitNode(); }
    @Override public void visit(LambdaExpr n, Void arg) { enterNode(); super.visit(n, arg); exitNode(); }
    @Override public void visit(CatchClause n, Void arg) { enterNode(); super.visit(n, arg); exitNode(); }


    public int getMaxDepth() {
        return maxDepth;
    }

    /**
     * Resets the visitor's state to allow it to be reused for another method.
     */
    public void reset() {
        currentDepth = 0;
        maxDepth = 0;
    }
}
