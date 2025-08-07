package controller;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.InstanceOfExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.stmt.*;
import model.JavaMethod;
import model.Release;
import model.Ticket;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import utils.GitUtils;
import utils.NestingDepthVisitor;
import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

public class GitDataExtractor {
    private List<Ticket> ticketList;
    private List<Release> releaseList; // Solo il 34% per l'analisi
    private final List<Release> fullReleaseList;
    private final List<RevCommit> commitList;
    private final Git git;
    private final Repository repository;
    private final NestingDepthVisitor nestingVisitor;

    public GitDataExtractor(String projName, List<Release> allReleases, List<Ticket> ticketList) throws IOException {
        File repoDir = new File("/Users/silviaperelli/Desktop/" + projName.toLowerCase() + "_isw2");
        if (!repoDir.exists() || !new File(repoDir, ".git").exists()) {
            throw new IOException("La cartella del repository Git non è valida: " + repoDir.getAbsolutePath());
        }

        try {
            this.git = Git.open(repoDir);
        } catch (IOException e) {
            throw new IOException("Impossibile aprire il repository Git in " + repoDir.getAbsolutePath(), e);
        }

        this.repository = git.getRepository();
        this.fullReleaseList = new ArrayList<>(allReleases);
        this.fullReleaseList.sort(Comparator.comparing(Release::getDate));
        this.releaseList = new ArrayList<>();
        this.ticketList = ticketList;
        this.commitList = new ArrayList<>();
        this.nestingVisitor = new NestingDepthVisitor();
    }

    public List<Ticket> getTicketList() {
        return ticketList;
    }

    public List<Release> getReleaseList() {
        return releaseList;
    }

    public List<Release> getFullReleaseList() {
        return fullReleaseList;
    }

    public void setTicketList(List<Ticket> ticketList) {
        this.ticketList = ticketList;
    }

    public void setReleaseListForAnalysis() {
        if (this.fullReleaseList == null || this.fullReleaseList.isEmpty()) return;
        int releasesToConsider = (int) Math.ceil(this.fullReleaseList.size() * 0.34);
        if (releasesToConsider == 0 && !this.fullReleaseList.isEmpty()) releasesToConsider = 1;
        this.releaseList = new ArrayList<>(this.fullReleaseList.subList(0, releasesToConsider));
        for (int i = 0; i < this.releaseList.size(); i++) {
            this.releaseList.get(i).setId(i + 1);
        }
    }

    public List<RevCommit> getAllCommitsAndAssignToReleases() throws GitAPIException, IOException {
        if (!commitList.isEmpty()) return commitList;
        Iterable<RevCommit> commitsIterable = git.log().all().call();
        commitsIterable.forEach(commitList::add);
        commitList.sort(Comparator.comparing(c -> c.getCommitterIdent().getWhen()));

        for (RevCommit commit : commitList) {
            Date commitDate = commit.getCommitterIdent().getWhen();
            Date lowerBoundDate = new Date(0);
            for (Release release : this.fullReleaseList) {
                Date releaseDate = Date.from(release.getDate().atStartOfDay(ZoneId.systemDefault()).toInstant());
                if (!commitDate.before(lowerBoundDate) && !commitDate.after(releaseDate)) {
                    release.addCommit(commit);
                }
                lowerBoundDate = releaseDate;
            }
        }
        filterAndRenumberReleases();
        setReleaseListForAnalysis();
        return commitList;
    }

    public List<RevCommit> filterCommitsOfIssues() {
        List<RevCommit> filteredCommits = new ArrayList<>();
        if (commitList.isEmpty()) {
            System.err.println("Lista commit vuota. Chiamare prima getAllCommitsAndAssignToReleases().");
            return filteredCommits;
        }

        for (RevCommit commit : commitList) {
            for (Ticket ticket : this.ticketList) {
                String commitMessage = commit.getFullMessage();
                String ticketKey = ticket.getTicketID();

                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
                LocalDate commitDate = LocalDate.parse(formatter.format(commit.getCommitterIdent().getWhen()));

                if (ticketKey != null && !ticketKey.isEmpty() &&
                        commitMessage.contains(ticketKey) &&
                        ticket.getResolutionDate() != null && !commitDate.isAfter(ticket.getResolutionDate()) &&
                        ticket.getCreationDate() != null && !commitDate.isBefore(ticket.getCreationDate())) {

                    if (!filteredCommits.contains(commit)) {
                        filteredCommits.add(commit);
                    }
                    ticket.addCommit(commit);
                }
            }
        }

        //remove tickets that don't have associated commit
        this.ticketList.removeIf(ticket -> ticket.getCommitList().isEmpty());
        return filteredCommits;
    }

    public List<JavaMethod> getMethodsFromReleases() throws IOException, GitAPIException {
        List<JavaMethod> allMethods = new ArrayList<>();
        Set<String> processedMethodsForRelease = new HashSet<>();

        for (Release release : this.releaseList) {
            processedMethodsForRelease.clear();
            List<RevCommit> releaseCommits = release.getCommitList();
            if (releaseCommits.isEmpty()) continue;

            releaseCommits.sort(Comparator.comparing(c -> c.getCommitterIdent().getWhen()));
            RevCommit lastCommitOfRelease = releaseCommits.get(releaseCommits.size() - 1);

            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(lastCommitOfRelease.getTree());
                treeWalk.setRecursive(true);

                while (treeWalk.next()) {
                    String filePath = treeWalk.getPathString();
                    if (filePath.endsWith(".java") && !filePath.contains("/test/")) {
                        String fileContent = new String(repository.open(treeWalk.getObjectId(0)).getBytes(), StandardCharsets.UTF_8);
                        try {
                            CompilationUnit cu = StaticJavaParser.parse(fileContent);
                            cu.findAll(MethodDeclaration.class).forEach(md -> {
                                String fqn = filePath + "/" + JavaMethod.getSignature(md);
                                if (!processedMethodsForRelease.contains(fqn)) {
                                    JavaMethod javaMethod = new JavaMethod(fqn, release);

                                    // 1. Calcola le metriche di base UNA SOLA VOLTA
                                    int loc = calculateLOC(md);
                                    int numParams = md.getParameters().size();
                                    int numBranches = calculateNumBranches(md); // Aggiungiamo qui il calcolo di numBranches
                                    int cyclomaticComplexity = numBranches + 1; // La complessità ciclomatica
                                    int nestingDepth = calculateNestingDepth(md);
                                    int numLocalVars = calculateNumLocalVariables(md);

                                    // 2. Imposta le metriche sull'oggetto JavaMethod
                                    javaMethod.setLoc(loc);
                                    javaMethod.setNumParameters(numParams);
                                    javaMethod.setNumBranches(numBranches); // Impostiamo anche i rami
                                    javaMethod.setNestingDepth(nestingDepth);
                                    javaMethod.setNumLocalVariables(numLocalVars);

                                    // 3. Calcola i code smells passando le metriche già calcolate
                                    int codeSmells = calculateCodeSmells(md, cyclomaticComplexity, loc, nestingDepth, numParams);
                                    javaMethod.setNumCodeSmells(codeSmells);

                                    allMethods.add(javaMethod);
                                    release.addMethod(javaMethod);
                                    javaMethod.setBodyHash(calculateBodyHash(md));
                                    processedMethodsForRelease.add(fqn);
                                }
                            });
                        } catch (Exception e) {
                            // Logga l'errore di parsing se necessario, ma non bloccare l'esecuzione
                        }
                    }
                }
            }
        }

        // Calcolo di tutte le metriche storiche
        addCommitsToMethods(allMethods, this.commitList);
        calculateNFix(allMethods);
        return allMethods;
    }

    // --- METODI PER IL CALCOLO DELLE METRICHE ---

    private int calculateLOC(MethodDeclaration md) {
        if (!md.getBody().isPresent()) {
            return 0;
        }

        BlockStmt body = md.getBody().get();
        if (body.getStatements().isEmpty()) {
            return 0;
        }

        // Usiamo un Set per evitare di contare più volte la stessa riga
        // se contiene più statement (es. if (c) a++; else b++;)
        Set<Integer> countedLines = new HashSet<>();

        // Itera su ogni statement nel corpo del metodo
        for (Statement stmt : body.getStatements()) {
            // Ottieni la riga di inizio e di fine dello statement
            // L'uso di .get() è sicuro perché uno statement nel corpo ha sempre una posizione
            int startLine = stmt.getBegin().get().line;
            int endLine = stmt.getEnd().get().line;

            // Itera su tutte le righe occupate da questo statement
            for (int lineNum = startLine; lineNum <= endLine; lineNum++) {
                countedLines.add(lineNum);
            }
        }

        // La dimensione del set è il nostro LOC
        return countedLines.size();
    }

    private int calculateNestingDepth(MethodDeclaration md) {
        if (!md.getBody().isPresent()) return 0;
        this.nestingVisitor.reset();
        md.getBody().get().accept(this.nestingVisitor, null);
        return this.nestingVisitor.getMaxDepth();
    }

    private int calculateNumBranches(MethodDeclaration md) {
        if (md == null || !md.getBody().isPresent()) {
            return 0;
        }
        int branches = 0;
        // Conditionals (if, ?: operator)
        branches += md.findAll(IfStmt.class).size();
        branches += md.findAll(ConditionalExpr.class).size();

        // Loops (for, foreach, while, do-while)
        branches += md.findAll(ForStmt.class).size();
        branches += md.findAll(ForEachStmt.class).size();
        branches += md.findAll(WhileStmt.class).size();
        branches += md.findAll(DoStmt.class).size();

        // Switch cases
        for (SwitchStmt switchStmt : md.findAll(SwitchStmt.class)) {
            // Ogni SwitchEntry (case X: o default:) è un ramo potenziale.
            branches += switchStmt.getEntries().size();
        }

        // Exception handling (try-catch blocks)
        branches += md.findAll(CatchClause.class).size();

        return branches;
    }

    private int calculateNumLocalVariables(MethodDeclaration md) {
        if (md == null || !md.getBody().isPresent()) {
            return 0;
        }
        // findAll(VariableDeclarator.class) trova tutte le dichiarazioni di variabili.
        // Esempi: "int x", "int y=0, z", "String s"
        return md.getBody().get().findAll(VariableDeclarator.class).size();
    }

    private int calculateCodeSmells(MethodDeclaration md, int cyclomaticComplexity, int loc, int nestingDepth, int numParameters) {
        if (md == null || !md.getBody().isPresent()) {
            return 0;
        }

        int smellCount = 0;
        BlockStmt body = md.getBody().get();

        // --- GRUPPO 1: Complessità e Dimensione (usiamo i parametri) ---
        final int COMPLEXITY_THRESHOLD = 10;
        if (cyclomaticComplexity > COMPLEXITY_THRESHOLD) {
            smellCount++;
        }

        final int LOC_THRESHOLD = 50;
        if (loc > LOC_THRESHOLD) {
            smellCount++;
        }

        final int NESTING_THRESHOLD = 4;
        if (nestingDepth > NESTING_THRESHOLD) {
            smellCount++;
        }

        final int PARAMETER_THRESHOLD = 5;
        if (numParameters > PARAMETER_THRESHOLD) {
            smellCount++;
        }

        // --- GRUPPO 2: Problemi di Design e Robustezza ---
        // SMELL 5: Switch senza `default`
        for (SwitchStmt switchStmt : body.findAll(SwitchStmt.class)) {
            boolean hasDefault = switchStmt.getEntries().stream()
                    .anyMatch(entry -> entry.getLabels().isEmpty());
            if (!hasDefault) {
                smellCount++;
            }
        }

        // SMELL 6: Blocco `catch` vuoto
        for (CatchClause catchClause : body.findAll(CatchClause.class)) {
            if (catchClause.getBody().getStatements().isEmpty()) {
                smellCount++;
            }
        }

        // SMELL 7: Abuso di `instanceof`
        final int INSTANCEOF_THRESHOLD = 2;
        if (body.findAll(InstanceOfExpr.class).size() > INSTANCEOF_THRESHOLD) {
            smellCount++;
        }

        // SMELL 8: Mancanza di `@Override`
        String methodName = md.getNameAsString();
        if (methodName.equals("equals") || methodName.equals("hashCode") || methodName.equals("toString")) {
            if (md.getAnnotations().stream().noneMatch(a -> a.getNameAsString().equals("Override"))) {
                smellCount++;
            }
        }

        // --- GRUPPO 3: Problemi di Leggibilità e Manutenzione ---
        // SMELL 9 (Migliorato): Uso di "Magic Numbers"
        long magicNumberCount = body.findAll(IntegerLiteralExpr.class).stream()
                .filter(n -> {
                    try {
                        int value = n.asInt();
                        return value != 0 && value != 1 && value != -1;
                    } catch (Exception e) { return true; }
                })
                .filter(n -> n.getParentNode().map(parent ->
                                !(parent instanceof VariableDeclarator) &&
                                        !(parent instanceof ArrayAccessExpr && ((ArrayAccessExpr) parent).getIndex() == n) &&
                                        !(parent instanceof ForStmt)
                        ).orElse(true)
                ).count();

        if (magicNumberCount > 0) {
            smellCount++;
        }

        return smellCount;
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private String calculateBodyHash(MethodDeclaration md) {
        if (md == null) return null;
        String normalizedBody = normalizeMethodBody(md);
        if (normalizedBody.isEmpty()) return "EMPTY_BODY_HASH"; // O un altro placeholder
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(normalizedBody.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(encodedhash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 Hashing error", e);
        }
    }

    private String normalizeMethodBody(MethodDeclaration md) {
        if (md == null || !md.getBody().isPresent()) {
            return "";
        }
        // Rimuovi commenti, spazi bianchi eccessivi, ecc.
        // Questo è un esempio MOLTO SEMPLICE. Una normalizzazione robusta è complessa.
        String body = md.getBody().get().toString();
        body = body.replaceAll("//.*|/\\*(?s:.*?)\\*/", ""); // Rimuovi commenti
        body = body.replaceAll("\\s+", " "); // Sostituisci spazi multipli con uno singolo
        return body.trim();
    }

    public void calculateNFix(List<JavaMethod> allMethods) {
        Map<String, Ticket> commitNameToTicketMap = new HashMap<>();
        for (Ticket ticket : this.ticketList) {
            for (RevCommit commit : ticket.getCommitList()) {
                commitNameToTicketMap.put(commit.getName(), ticket);
            }
        }
        for (JavaMethod method : allMethods) {
            Release currentMethodRelease = method.getRelease();
            for (RevCommit commit : method.getCommits()) {
                if (commitNameToTicketMap.containsKey(commit.getName())) {
                    Release commitRelease = GitUtils.getReleaseOfCommit(commit, this.fullReleaseList);
                    if (commitRelease != null && commitRelease.getId() < currentMethodRelease.getId()) {
                        method.incrementNFix();
                    }
                }
            }
        }
    }

    public void addCommitsToMethods(List<JavaMethod> allMethods, List<RevCommit> allCommits) throws IOException, GitAPIException {

        Map<String, List<JavaMethod>> methodMap = allMethods.stream()
                .collect(Collectors.groupingBy(JavaMethod::getFullyQualifiedName));

        List<RevCommit> sortedCommits = new ArrayList<>(allCommits);
        sortedCommits.sort(Comparator.comparing(RevCommit::getCommitTime));

        for (RevCommit commit : sortedCommits) {
            if (commit.getParentCount() == 0) continue;
            RevCommit parent = commit.getParent(0);
            List<DiffEntry> diffs = getDiffEntries(parent, commit);
            int currentChgSetSize = (int) diffs.stream().filter(d -> d.getNewPath().endsWith(".java")).count();

            Map<String, String> oldFileContents = getFileContents(parent, diffs, true);
            Map<String, String> newFileContents = getFileContents(commit, diffs, false);

            for (DiffEntry diff : diffs) {
                String filePath = diff.getChangeType() == DiffEntry.ChangeType.DELETE ? diff.getOldPath() : diff.getNewPath();
                if (!filePath.endsWith(".java") || filePath.contains("/test/")) continue;

                Map<String, MethodDeclaration> oldMethods = parseMethods(oldFileContents.getOrDefault(diff.getOldPath(), ""));
                Map<String, MethodDeclaration> newMethods = parseMethods(newFileContents.getOrDefault(diff.getNewPath(), ""));

                for (Map.Entry<String, MethodDeclaration> newMethodEntry : newMethods.entrySet()) {
                    String signature = newMethodEntry.getKey();
                    MethodDeclaration newMd = newMethodEntry.getValue();
                    MethodDeclaration oldMd = oldMethods.get(signature);

                    String newBodyHash = calculateBodyHash(newMd);
                    String oldBodyHash = (oldMd != null) ? calculateBodyHash(oldMd) : null;

                    if (oldMd == null || !newBodyHash.equals(oldBodyHash)) { // Se il metodo è nuovo o cambiato
                        String fqn = filePath + "/" + signature;
                        if (methodMap.containsKey(fqn)) {
                            List<JavaMethod> methodsToUpdate = methodMap.get(fqn);
                            updateMethodMetricsForCommit(methodsToUpdate, commit, newMd, oldMd, newBodyHash, currentChgSetSize);
                        }
                    }
                }
            }
        }

        // Il calcolo delle metriche aggregate finali rimane identico
        for (JavaMethod method : allMethods) {
            if (!method.getCommits().isEmpty()) {
                Set<String> authors = method.getCommits().stream()
                        .map(c -> c.getAuthorIdent().getName())
                        .collect(Collectors.toSet());
                method.setNumAuthors(authors.size());
            }
            method.computeFinalMetrics();
        }
    }

    // --- INIZIO MODIFICA: Logica di calcolo del churn ripristinata come da specifiche ---
    private void updateMethodMetricsForCommit(List<JavaMethod> methodsToUpdate, RevCommit commit, MethodDeclaration currentMdAst, MethodDeclaration oldMdAst, String newBodyHash, int chgSetSize) {
        Release releaseOfCommit = GitUtils.getReleaseOfCommit(commit, this.fullReleaseList);
        if (releaseOfCommit == null) return;

        for (JavaMethod projectMethod : methodsToUpdate) {
            if (projectMethod.getRelease().getId() >= releaseOfCommit.getId()) {
                projectMethod.addCommit(commit);
                projectMethod.incrementNumRevisions();
                projectMethod.setBodyHash(newBodyHash);

                // Calcolo di StmtAdded/Deleted e Churn basato sulla differenza netta di LOC
                // Questo approccio è in linea con le definizioni delle slide e del "vecchio codice"
                int currentCommitStmtAdded = 0;
                int currentCommitStmtDeleted = 0;

                if (oldMdAst != null) { // Il metodo esisteva già (MODIFICA)
                    int locOld = calculateLOC(oldMdAst);
                    int locNew = calculateLOC(currentMdAst);

                    if (locNew > locOld) {
                        currentCommitStmtAdded = locNew - locOld;
                    } else if (locOld > locNew) {
                        currentCommitStmtDeleted = locOld - locNew;
                    }
                } else { // Il metodo è nuovo (AGGIUNTA)
                    currentCommitStmtAdded = calculateLOC(currentMdAst);
                }

                projectMethod.addStmtAdded(currentCommitStmtAdded);
                projectMethod.addStmtDeleted(currentCommitStmtDeleted);

                // Il churn per questo commit è la somma delle differenze (una sarà sempre 0)
                int currentCommitChurn = currentCommitStmtAdded + currentCommitStmtDeleted;
                if (currentCommitChurn > projectMethod.getMaxChurn()) {
                    projectMethod.setMaxChurn(currentCommitChurn);
                }
            }
        }
    }
    // --- FINE MODIFICA ---

    private List<DiffEntry> getDiffEntries(RevCommit parent, RevCommit commit) throws IOException {
        try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            diffFormatter.setRepository(repository);
            return diffFormatter.scan(parent.getTree(), commit.getTree());
        }
    }

    private Map<String, String> getFileContents(RevCommit commit, List<DiffEntry> diffs, boolean useOldPath) throws IOException {
        Map<String, String> contents = new HashMap<>();
        try (ObjectReader reader = repository.newObjectReader()) {
            for (DiffEntry diff : diffs) {
                String path = useOldPath ? diff.getOldPath() : diff.getNewPath();
                ObjectId id = useOldPath ? diff.getOldId().toObjectId() : diff.getNewId().toObjectId();
                if (DiffEntry.DEV_NULL.equals(path)) continue;
                try {
                    contents.put(path, new String(reader.open(id).getBytes(), StandardCharsets.UTF_8));
                } catch (Exception e) {
                    // Ignora file non trovati o illeggibili
                }
            }
        }
        return contents;
    }

    private Map<String, MethodDeclaration> parseMethods(String content) {
        Map<String, MethodDeclaration> methods = new HashMap<>();
        if (content == null || content.isEmpty()) return methods;
        try {
            StaticJavaParser.parse(content).findAll(MethodDeclaration.class)
                    .forEach(md -> methods.put(JavaMethod.getSignature(md), md));
        } catch (Exception e) {
            // Ignora errori di parsing
        }
        return methods;
    }

    public void setMethodBuggyness(List<JavaMethod> allProjectMethods) {
        if (this.ticketList == null) {
            System.err.println("Ticket list non inizializzata per setMethodBuggyness.");
            return;
        }

        for (JavaMethod projectMethod : allProjectMethods) {
            projectMethod.setBuggy(false); // Reset iniziale
        }

        for (Ticket ticket : this.ticketList) {
            Release injectedVersion = ticket.getIv();
            if (injectedVersion == null) continue; // IV non definito per questo ticket

            for (RevCommit fixCommit : ticket.getCommitList()) {
                Release fixedVersion = GitUtils.getReleaseOfCommit(fixCommit, this.fullReleaseList);
                if (fixedVersion == null) continue; // Commit di fix non appartiene a una release tracciata

                try {
                    if (fixCommit.getParentCount() == 0) continue;
                    RevCommit parentOfFix = fixCommit.getParent(0);
                    List<DiffEntry> diffs = getDiffEntries(parentOfFix, fixCommit);

                    Map<String, String> newFileContentsInFix = getFileContents(fixCommit, diffs, false);

                    for (DiffEntry diff : diffs) {
                        String filePath = diff.getNewPath();
                        if (!filePath.endsWith(".java") || filePath.contains("/test/")) continue;

                        String newContent = newFileContentsInFix.getOrDefault(filePath, "");
                        Map<String, MethodDeclaration> newMethodsInFix = parseMethods(newContent);

                        // Per determinare quali metodi sono stati *effettivamente* modificati dal fix
                        String oldContentInFix = getFileContents(parentOfFix, Collections.singletonList(diff), true).getOrDefault(diff.getOldPath(), "");
                        Map<String, MethodDeclaration> oldMethodsInFix = parseMethods(oldContentInFix);


                        for (Map.Entry<String, MethodDeclaration> fixedMethodEntry : newMethodsInFix.entrySet()) {
                            String signature = fixedMethodEntry.getKey();
                            MethodDeclaration fixedMd = fixedMethodEntry.getValue();
                            MethodDeclaration preFixMd = oldMethodsInFix.get(signature);

                            String hashFixed = calculateBodyHash(fixedMd);
                            String hashPreFix = calculateBodyHash(preFixMd);

                            boolean actuallyChangedByFix = (preFixMd == null && fixedMd != null) ||
                                    (hashPreFix != null && hashFixed != null && !hashPreFix.equals(hashFixed)) ||
                                    (hashPreFix == null && hashFixed != null && preFixMd != null) ||
                                    (hashPreFix != null && hashFixed == null && fixedMd != null);

                            if (actuallyChangedByFix) {
                                String fqn = filePath + "/" + signature;
                                labelBuggyMethods(fqn, injectedVersion, fixedVersion, fixCommit, allProjectMethods);
                            }
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Errore durante l'analisi del commit di fix " + fixCommit.getName() + ": " + e.getMessage());
                }
            }
        }
    }

    private void labelBuggyMethods(String fixedMethodFQN, Release injectedVersion, Release fixedVersion, RevCommit fixCommit, List<JavaMethod> allProjectMethods) {
        for (JavaMethod projectMethod : allProjectMethods) {
            if (projectMethod.getFullyQualifiedName().equals(fixedMethodFQN)) {
                // Aggiungi il commit di fix se il metodo appartiene alla FV e il commit lo ha toccato
                if (projectMethod.getRelease().getId() == fixedVersion.getId() && projectMethod.getCommits().contains(fixCommit) ) { // Assumiamo che getCommits contenga tutti i commit che hanno toccato il metodo in quella release
                    projectMethod.addFixCommit(fixCommit);
                }
                // Etichetta come buggy se la release del metodo è tra IV (inclusa) e FV (esclusa)
                if (projectMethod.getRelease().getId() >= injectedVersion.getId() && projectMethod.getRelease().getId() < fixedVersion.getId()) {
                    projectMethod.setBuggy(true);
                }
            }
        }
    }

    private void filterAndRenumberReleases() {
        this.fullReleaseList.removeIf(release -> release.getCommitList().isEmpty());
        for (int i = 0; i < this.fullReleaseList.size(); i++) {
            this.fullReleaseList.get(i).setId(i + 1);
        }
    }

    // Questo metodo non viene più utilizzato con la nuova logica, ma lo lascio per completezza
    // nel caso servisse per altri scopi.
    private int[] calculateLineLevelChurn(String oldBody, String newBody) {
        if (oldBody == null || oldBody.isEmpty()) {
            return (newBody == null || newBody.isEmpty()) ? new int[]{0, 0} : new int[]{newBody.split("\r\n|\r|\n").length, 0};
        }
        if (newBody == null || newBody.isEmpty()) {
            return new int[]{0, oldBody.split("\r\n|\r|\n").length};
        }
        List<String> oldLines = Arrays.asList(oldBody.split("\r\n|\r|\n"));
        List<String> newLines = Arrays.asList(newBody.split("\r\n|\r|\n"));
        int linesAdded = 0;
        int linesDeleted = 0;
        try {
            Patch<String> patch = DiffUtils.diff(oldLines, newLines);
            for (AbstractDelta<String> delta : patch.getDeltas()) {
                switch (delta.getType()) {
                    case INSERT:
                        linesAdded += delta.getTarget().getLines().size();
                        break;
                    case DELETE:
                        linesDeleted += delta.getSource().getLines().size();
                        break;
                    case CHANGE:
                        linesDeleted += delta.getSource().getLines().size();
                        linesAdded += delta.getTarget().getLines().size();
                        break;
                    default:
                        break;
                }
            }
        } catch (Exception e) {
            return new int[]{0, 0};
        }
        return new int[]{linesAdded, linesDeleted};
    }
}