package controller;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import model.JavaMethod;
import model.Release;
import model.Ticket;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import utils.GitUtils;
import utils.NestingDepthVisitor;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class GitDataExtractor {
    private static final Logger LOGGER = Logger.getLogger(GitDataExtractor.class.getName());
    private static String directoryTest = "/test/";
    private static String javaExtension = ".java";

    private List<Ticket> ticketList;
    private List<Release> releaseList;
    private List<Release> fullReleaseList;
    private List<RevCommit> commitList;
    private final Git git;
    private final Repository repository;
    private final NestingDepthVisitor nestingVisitor;

    public GitDataExtractor(String projName, List<Release> allReleases, List<Ticket> ticketList) throws IOException {
        // Configurazioni iniziali
        ParserConfiguration parserConfiguration = new ParserConfiguration();
        StaticJavaParser.setConfiguration(parserConfiguration);

        File repoDir = new File("/Users/silviaperelli/Desktop/" + projName.toLowerCase() + "_isw2");
        File gitDir = new File(repoDir, ".git");

        if (!gitDir.exists()) {
            String errorMessage = "Git directory does not exist: " + repoDir.getAbsolutePath();
            LOGGER.severe(errorMessage);
            throw new IOException(errorMessage);
        }

        try {
            this.git = Git.open(repoDir);
        } catch (IOException e) {
            throw new IOException("Impossible to open the repository Git in " + repoDir.getAbsolutePath(), e);
        }

        this.repository = git.getRepository();
        this.fullReleaseList = new ArrayList<>(allReleases);
        this.fullReleaseList.sort(Comparator.comparing(Release::getDate));
        this.releaseList = new ArrayList<>();
        this.ticketList = ticketList;
        this.commitList = new ArrayList<>();
        this.nestingVisitor = new NestingDepthVisitor();
    }

    // --- GETTERS E SETTERS ---
    public List<Ticket> getTicketList() { return ticketList; }
    public List<Release> getReleaseList() { return releaseList; }
    public List<Release> getFullReleaseList() { return fullReleaseList; }
    public void setTicketList(List<Ticket> ticketList) { this.ticketList = ticketList; }

    /**
     * Filtra la lista completa delle release per considerare solo la prima porzione (34%) per l'analisi.
     */
    public void setReleaseListForAnalysis() {
        if (this.fullReleaseList == null || this.fullReleaseList.isEmpty()) {
            return;
        }
        int releasesToConsider = (int) Math.ceil(this.fullReleaseList.size() * 0.34);
        if (releasesToConsider == 0 && !this.fullReleaseList.isEmpty()) {
            releasesToConsider = 1;
        }
        this.releaseList = new ArrayList<>(this.fullReleaseList.subList(0, releasesToConsider));
        int i = 0;
        for (Release release : this.releaseList) {
            release.setId(++i);
        }
    }

    /**
     * Recupera tutti i commit dal repository e li assegna alle rispettive release.
     */
    public List<RevCommit> getAllCommitsAndAssignToReleases() throws GitAPIException, IOException {
        if (this.ticketList == null) {
            LOGGER.warning("Ticket list non inizializzata.");
            return Collections.emptyList();
        }
        if (!commitList.isEmpty()) {
            return commitList;
        }

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        Iterable<RevCommit> commitsIterable = git.log().all().call();
        commitsIterable.forEach(commitList::add);
        commitList.sort(Comparator.comparing(c -> c.getCommitterIdent().getWhen()));

        for (RevCommit commit : commitList) {
            LocalDate commitDate = LocalDate.parse(formatter.format(commit.getCommitterIdent().getWhen()));
            LocalDate lowerBoundDate = LocalDate.parse(formatter.format(new Date(0)));

            for (Release release : this.fullReleaseList) {
                LocalDate releaseDate = release.getDate();
                if (!commitDate.isBefore(lowerBoundDate) && !commitDate.isAfter(releaseDate)) {
                    release.addCommit(commit);
                }
                lowerBoundDate = releaseDate;
            }
        }

        filterAndRenumberReleases();
        setReleaseListForAnalysis();
        return commitList;
    }

    /**
     * Associa i commit ai ticket e rimuove i ticket che non hanno commit associati.
     */
    public List<RevCommit> filterCommitsOfIssues() {
        List<RevCommit> filteredCommits = new ArrayList<>();
        if (commitList.isEmpty()) {
            LOGGER.warning("Lista commit vuota. Chiamare prima getAllCommitsAndAssignToReleases().");
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
        this.ticketList.removeIf(ticket -> ticket.getCommitList().isEmpty());
        return filteredCommits;
    }

    /**
     * Estrae tutti i metodi Java dalle release selezionate e calcola le loro metriche.
     */
    public List<JavaMethod> getMethodsFromReleases() throws IOException {
        List<JavaMethod> allMethods = new ArrayList<>();
        Map<String, JavaMethod> methodCache = new HashMap<>(); // Cache per FQN@ReleaseID -> JavaMethod

        for (Release release : this.releaseList) {
            List<RevCommit> releaseCommits = release.getCommitList();
            if (releaseCommits.isEmpty()) continue;

            releaseCommits.sort(Comparator.comparing(c -> c.getCommitterIdent().getWhen()));
            RevCommit lastCommitOfRelease = releaseCommits.get(releaseCommits.size() - 1);

            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(lastCommitOfRelease.getTree());
                treeWalk.setRecursive(true);

                while (treeWalk.next()) {
                    if (treeWalk.getPathString().endsWith(javaExtension) && !treeWalk.getPathString().contains(directoryTest)) {
                        processJavaFile(treeWalk, release, methodCache);
                    }
                }
            }
        }

        allMethods.addAll(methodCache.values());
        addCommits(allMethods, this.commitList);
        calculateHasFixHistory(allMethods);
        return allMethods;
    }

    /**
     * Processa un singolo file Java per estrarre metodi e calcolare metriche statiche.
     */
    private void processJavaFile(TreeWalk treeWalk, Release release, Map<String, JavaMethod> methodCache) throws IOException {
        String filePath = treeWalk.getPathString();
        ObjectLoader loader = repository.open(treeWalk.getObjectId(0));
        String fileContent;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            loader.copyTo(output);
            fileContent = output.toString(StandardCharsets.UTF_8.name());
        }

        try {
            CompilationUnit cu = StaticJavaParser.parse(fileContent);
            cu.findAll(MethodDeclaration.class).forEach(md -> {
                String methodSignature = JavaMethod.getSignature(md);
                String fqn = filePath + "/" + methodSignature;
                String cacheKey = fqn + "@" + release.getId();

                if (!methodCache.containsKey(cacheKey)) {
                    JavaMethod javaMethod = new JavaMethod(fqn, release);

                    int loc = calculateLOC(md);
                    int numParams = md.getParameters().size();
                    int numBranches = calculateNumBranches(md);
                    int cyclomaticComplexity = numBranches + 1;
                    int nestingDepth = calculateNestingDepth(md);
                    int numLocalVars = calculateNumLocalVariables(md);

                    javaMethod.setLoc(loc);
                    javaMethod.setNumParameters(numParams);
                    javaMethod.setNumBranches(numBranches);
                    javaMethod.setNestingDepth(nestingDepth);
                    javaMethod.setNumLocalVariables(numLocalVars);

                    int codeSmells = calculateCodeSmells(md, cyclomaticComplexity, loc, nestingDepth, numParams);
                    javaMethod.setNumCodeSmells(codeSmells);

                    methodCache.put(cacheKey, javaMethod);
                }
            });
        } catch (ParseProblemException | StackOverflowError e) {
            LOGGER.log(Level.WARNING, "Errore di parsing per il file: " + filePath + ". " + e.getMessage());
        }
    }

    /**
     * Analizza la storia dei commit per calcolare le metriche di processo (churn, autori, revisioni) per ogni metodo.
     */
    public void addCommits(List<JavaMethod> allMethods, List<RevCommit> allCommits) throws IOException {
        Map<String, List<JavaMethod>> methodMap = allMethods.stream()
                .collect(Collectors.groupingBy(JavaMethod::getFullyQualifiedName));

        List<RevCommit> sortedCommits = new ArrayList<>(allCommits);
        sortedCommits.sort(Comparator.comparing(RevCommit::getCommitTime));

        for (RevCommit commit : sortedCommits) {
            if (commit.getParentCount() == 0) continue;
            processCommitForMethodMetrics(commit, methodMap);
        }

        // Calcola NAuth e AvgChurn dopo aver processato tutti i commit.
        calculateFinalMethodMetrics(allMethods);
    }

    /**
     * Processa un singolo commit per aggiornare le metriche dei metodi.
     */
    private void processCommitForMethodMetrics(RevCommit commit, Map<String, List<JavaMethod>> methodMap) throws IOException {
        RevCommit parent = commit.getParent(0);

        List<DiffEntry> diffs;
        try {
            diffs = getDiffEntries(parent, commit);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Impossibile calcolare diff per commit " + commit.getName(), e);
            return;
        }

        Map<String, String> oldFileContents = getFileContents(parent, diffs, true);
        Map<String, String> newFileContents = getFileContents(commit, diffs, false);

        for (DiffEntry diff : diffs) {
            processDiffEntryForMethodMetrics(diff, commit, methodMap, oldFileContents, newFileContents);
        }
    }

    /**
     * Processa una singola voce di diff per aggiornare le metriche dei metodi.
     */
    private void processDiffEntryForMethodMetrics(DiffEntry diff, RevCommit commit, Map<String, List<JavaMethod>> methodMap, Map<String, String> oldFileContents, Map<String, String> newFileContents) {
        String filePath = diff.getChangeType() == DiffEntry.ChangeType.DELETE ? diff.getOldPath() : diff.getNewPath();
        if (!filePath.endsWith(javaExtension) || filePath.contains(directoryTest)) return;

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
                    updateMethodMetricsForCommit(methodsToUpdate, commit, newMd, oldMd, newBodyHash);
                }
            }
        }
    }

    /**
     * Calcola le metriche finali (NAuth, AvgChurn) per tutti i metodi dopo che tutti i commit sono stati processati.
     */
    private void calculateFinalMethodMetrics(List<JavaMethod> allMethods) {
        for (JavaMethod method : allMethods) {
            // Calcolo NAuth
            if (!method.getCommits().isEmpty()) {
                Set<String> authors = method.getCommits().stream()
                        .map(c -> c.getAuthorIdent().getName())
                        .collect(Collectors.toSet());
                method.setNumAuthors(authors.size());
            } else {
                method.setNumAuthors(0);
            }

            // Calcolo AvgChurn
            if (method.getNumRevisions() > 0) {
                double avgChurn = (double) (method.getTotalStmtAdded() + method.getTotalStmtDeleted()) / method.getNumRevisions();
                method.setAvgChurn(avgChurn);
            } else {
                method.setAvgChurn(0.0);
            }
        }
    }


    /**
     * Aggiorna le metriche di un metodo basate su un singolo commit.
     * NOTA: La logica di calcolo del churn è stata allineata a quella del secondo codice.
     */
    // --- MODIFICA 1: LOGICA DI `updateMethodMetricsForCommit` ALLINEATA ---
    private void updateMethodMetricsForCommit(List<JavaMethod> methodsToUpdate, RevCommit commit, MethodDeclaration currentMdAst, MethodDeclaration oldMdAst, String newBodyHash) {
        Release releaseOfCommit = GitUtils.getReleaseOfCommit(commit, this.fullReleaseList);
        if (releaseOfCommit == null) return;

        for (JavaMethod projectMethod : methodsToUpdate) {
            if (projectMethod.getRelease().getId() >= releaseOfCommit.getId()) {
                projectMethod.addCommit(commit);
                projectMethod.incrementNumRevisions();
                projectMethod.setBodyHash(newBodyHash);

                int currentCommitStmtAdded = 0;
                int currentCommitStmtDeleted = 0;

                // Logica di calcolo churn allineata a quella del secondo codice
                if (oldMdAst != null) { // MODIFICA
                    int locOld = calculateLOC(oldMdAst);
                    int locNew = calculateLOC(currentMdAst);
                    if (locNew > locOld) {
                        currentCommitStmtAdded = locNew - locOld;
                        projectMethod.addStmtAdded(currentCommitStmtAdded); // Aggiunta qui
                    } else if (locOld > locNew) {
                        currentCommitStmtDeleted = locOld - locNew;
                        projectMethod.addStmtDeleted(currentCommitStmtDeleted); // Aggiunta qui
                    }
                } else { // AGGIUNTA
                    currentCommitStmtAdded = calculateLOC(currentMdAst);
                    projectMethod.addStmtAdded(currentCommitStmtAdded); // Aggiunta qui
                }

                int currentCommitChurn = currentCommitStmtAdded + currentCommitStmtDeleted;
                if (currentCommitChurn > projectMethod.getMaxChurn()) {
                    projectMethod.setMaxChurn(currentCommitChurn);
                }
            }
        }
    }


    /**
     * Itera su tutti i metodi e imposta il flag 'hasFixHistory' se sono stati modificati da un commit
     * di fix in una release precedente a quella corrente.
     */
    public void calculateHasFixHistory(List<JavaMethod> allMethods) {
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
                        method.setHasFixHistory(1);
                        break; // Trovato un fix, non serve continuare a cercare per questo metodo
                    }
                }
            }
        }
    }

    /**
     * Itera su tutti i ticket di bug e etichetta i metodi appropriati come "buggy".
     */
    public void setMethodBuggyness(List<JavaMethod> allProjectMethods) {
        if (this.ticketList == null) {
            LOGGER.warning("Ticket list non inizializzata per setMethodBuggyness.");
            return;
        }

        for (Ticket ticket : this.ticketList) {
            Release injectedVersion = ticket.getIv();
            if (injectedVersion == null) continue;

            for (RevCommit fixCommit : ticket.getCommitList()) {
                Release fixedVersion = GitUtils.getReleaseOfCommit(fixCommit, this.fullReleaseList);
                if (fixedVersion == null) continue;

                try {
                    if (fixCommit.getParentCount() == 0) continue;
                    RevCommit parentOfFix = fixCommit.getParent(0);
                    List<DiffEntry> diffs = getDiffEntries(parentOfFix, fixCommit);

                    Map<String, String> newFileContentsInFix = getFileContents(fixCommit, diffs, false);
                    Map<String, String> oldFileContentsInFix = getFileContents(parentOfFix, diffs, true);

                    for (DiffEntry diff : diffs) {
                        String filePath = diff.getNewPath();
                        if (!filePath.endsWith(javaExtension) || filePath.contains(directoryTest)) continue;

                        String newContent = newFileContentsInFix.getOrDefault(filePath, "");
                        Map<String, MethodDeclaration> newMethodsInFix = parseMethods(newContent);

                        String oldContent = oldFileContentsInFix.getOrDefault(diff.getOldPath(), "");
                        Map<String, MethodDeclaration> oldMethodsInFix = parseMethods(oldContent);

                        for (Map.Entry<String, MethodDeclaration> newMethodEntry : newMethodsInFix.entrySet()) {
                            String signature = newMethodEntry.getKey();
                            MethodDeclaration newMd = newMethodEntry.getValue();
                            MethodDeclaration oldMd = oldMethodsInFix.get(signature);

                            String newHash = calculateBodyHash(newMd);
                            String oldHash = calculateBodyHash(oldMd);

                            if (oldMd == null || !newHash.equals(oldHash)) { // Metodo nuovo o cambiato
                                String fqn = filePath + "/" + signature;
                                labelBuggyMethods(fqn, injectedVersion, fixedVersion, allProjectMethods);
                            }
                        }
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Errore durante l'analisi del commit di fix " + fixCommit.getName(), e);
                }
            }
        }
    }

    /**
     * Metodo ausiliario per etichettare le istanze di un metodo come buggy.
     */
    private void labelBuggyMethods(String fixedMethodFQN, Release injectedVersion, Release fixedVersion, List<JavaMethod> allProjectMethods) {
        for (JavaMethod projectMethod : allProjectMethods) {
            if (projectMethod.getFullyQualifiedName().equals(fixedMethodFQN)) {
                if (projectMethod.getRelease().getId() >= injectedVersion.getId() && projectMethod.getRelease().getId() < fixedVersion.getId()) {
                    projectMethod.setBuggy(true);
                }
            }
        }
    }

    // --- METODI DI UTILITÀ PRIVATI ---

    private int calculateLOC(MethodDeclaration md) {
        if (md.getBody().isPresent()) {
            String[] lines = md.getBody().get().toString().split("\r\n|\r|\n");
            boolean inMultiLineComment = false;
            int locCount = 0;

            for (String line : lines) {
                String trimmedLine = line.trim();

                if (trimmedLine.startsWith("/*")) {
                    inMultiLineComment = true;
                    if (trimmedLine.endsWith("*/") && trimmedLine.length() > 2) {
                        inMultiLineComment = false;
                    }
                    continue;
                }

                if (trimmedLine.endsWith("*/")) {
                    inMultiLineComment = false;
                    continue;
                }

                if (inMultiLineComment) {
                    continue;
                }

                if (!trimmedLine.isEmpty() &&
                        !trimmedLine.startsWith("//") &&
                        !(trimmedLine.equals("{") || trimmedLine.equals("}"))) {
                    locCount++;
                }
            }
            return locCount;
        }
        return 0;
    }


    private int calculateNumBranches(MethodDeclaration md) {
        if (!md.getBody().isPresent()) return 0;
        int branches = 0;
        branches += md.findAll(IfStmt.class).size();
        branches += md.findAll(ConditionalExpr.class).size();
        branches += md.findAll(ForStmt.class).size();
        branches += md.findAll(ForEachStmt.class).size();
        branches += md.findAll(WhileStmt.class).size();
        branches += md.findAll(DoStmt.class).size();
        for (SwitchStmt switchStmt : md.findAll(SwitchStmt.class)) {
            branches += switchStmt.getEntries().size();
        }
        branches += md.findAll(CatchClause.class).size();
        return branches;
    }

    private int calculateNestingDepth(MethodDeclaration md) {
        if (!md.getBody().isPresent()) return 0;
        this.nestingVisitor.reset();
        md.getBody().get().accept(this.nestingVisitor, null);
        return this.nestingVisitor.getMaxDepth();
    }

    private int calculateNumLocalVariables(MethodDeclaration md) {
        if (!md.getBody().isPresent()) return 0;
        return md.getBody().get().findAll(VariableDeclarator.class).size();
    }

    private String calculateBodyHash(MethodDeclaration md) {
        if (md == null) return "NULL_METHOD_HASH";
        String normalizedBody = normalizeMethodBody(md);
        if (normalizedBody.isEmpty()) return "EMPTY_BODY_HASH";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(normalizedBody.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 Hashing error", e);
        }
    }

    private String normalizeMethodBody(MethodDeclaration md) {
        if (!md.getBody().isPresent()) return "";
        String body = md.getBody().get().toString();
        body = body.replaceAll("//.*|/\\*(?s:.*?)\\*/", ""); // Rimuovi commenti
        body = body.replaceAll("\\s+", " "); // Sostituisci spazi multipli con uno singolo
        return body.trim();
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private int calculateCodeSmells(MethodDeclaration md, int cyclomaticComplexity, int loc, int nestingDepth, int numParameters) {
        if (!md.getBody().isPresent()) return 0;
        int smellCount = 0;
        BlockStmt body = md.getBody().get();

        if (cyclomaticComplexity > 7) smellCount++;
        if (loc > 30) smellCount++;
        if (nestingDepth > 4) smellCount++;
        if (numParameters > 5) smellCount++;

        for (SwitchStmt switchStmt : body.findAll(SwitchStmt.class)) {
            if (switchStmt.getEntries().stream().noneMatch(entry -> entry.getLabels().isEmpty())) {
                smellCount++;
            }
        }
        for (CatchClause catchClause : body.findAll(CatchClause.class)) {
            if (catchClause.getBody().getStatements().isEmpty()) {
                smellCount++;
            }
        }
        if (body.findAll(InstanceOfExpr.class).size() > 2) {
            smellCount++;
        }
        String methodName = md.getNameAsString();
        if (methodName.equals("equals") || methodName.equals("hashCode") || methodName.equals("toString")) {
            if (md.getAnnotations().stream().noneMatch(a -> a.getNameAsString().equals("Override"))) {
                smellCount++;
            }
        }
        long magicNumberCount = body.findAll(IntegerLiteralExpr.class).stream()
                .filter(n -> { try { int val = n.asInt(); return val != 0 && val != 1 && val != -1; } catch (Exception e) { return true; } })
                .filter(n -> n.getParentNode().map(p -> !(p instanceof VariableDeclarator)).orElse(true))
                .count();
        if (magicNumberCount > 1) {
            smellCount++;
        }
        return smellCount;
    }

    private void filterAndRenumberReleases() {
        this.fullReleaseList.removeIf(release -> release.getCommitList().isEmpty());
        int idCounter = 1;
        for (Release r : this.fullReleaseList) {
            r.setId(idCounter++);
        }
    }

    private List<DiffEntry> getDiffEntries(RevCommit parent, RevCommit commit) throws IOException {
        try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            diffFormatter.setRepository(repository);
            diffFormatter.setDiffComparator(RawTextComparator.DEFAULT);
            diffFormatter.setContext(0);
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
                    ObjectLoader loader = reader.open(id);
                    contents.put(path, new String(loader.getBytes(), StandardCharsets.UTF_8));
                } catch (org.eclipse.jgit.errors.MissingObjectException e) {
                    LOGGER.log(Level.WARNING, "Missing object: " + id + " for path " + path, e);
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
}