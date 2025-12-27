package utils;

import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

/**
 * Implementazione del pattern Visitor per calcolare la massima profondità di annidamento (Nesting Depth)
 * dei blocchi di controllo all'interno di un nodo dell'AST
 */
public class NestingDepthVisitor extends VoidVisitorAdapter<Void> {
    // Contatore per la profondità di annidamento corrente durante l'attraversamento dell'AST
    private int currentDepth = 0;
    // Memorizza la massima profondità raggiunta finora
    private int maxDepth = 0;

    /**
     * Metodo helper chiamato quando il visitor entra in un blocco che aumenta l'annidamento.
     * Incrementa la profondità corrente e aggiorna la massima se necessario
     */
    private void enterNode() {
        currentDepth++;
        if (currentDepth > maxDepth) {
            maxDepth = currentDepth;
        }
    }

    /**
     * Metodo helper chiamato quando il visitor esce da un blocco, decrementando il contatore di profondità
     */
    private void exitNode() {
        currentDepth--;
    }

    // Vengono sovrascritti i metodi `visit` per tutti i costrutti di JavaParser che rappresentano un nuovo livello di annidamento logico
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
     * Resetta lo stato interno del visitor (profondità corrente e massima) a zero
     */
    public void reset() {
        currentDepth = 0;
        maxDepth = 0;
    }
}
