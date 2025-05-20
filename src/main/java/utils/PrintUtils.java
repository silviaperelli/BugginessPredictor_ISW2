package utils;

import Model.JavaMethod;
import Model.Release;
import Model.Ticket;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

public class PrintUtils {

    private PrintUtils(){}

    public static final String DELIMITER = "\n";
    public static final String CLASS = PrintUtils.class.getName();
    private static final Logger logger = Logger.getLogger(CLASS);
    private static final String MAINDIR = "reportFiles/";
    public static final String SLASH = "/";
    public static final String ERROR = "Error in writeOnReportFiles when trying to create directory";

    public static void printCommits(String project, List<RevCommit> commitList, String name) throws IOException {
        project = project.toLowerCase();
        File file = new File(MAINDIR + project);
        if (!file.exists()) {
            boolean created = file.mkdirs();
            if (!created) {
                throw new IOException();
            }
        }

        file = new File(MAINDIR + project+ SLASH + name);
        try(FileWriter fileWriter = new FileWriter(file)) {

            fileWriter.append("id,committer,creationDate\n");
            for (RevCommit commit: commitList){
                fileWriter.append(commit.getName()).append(",")
                        .append(commit.getCommitterIdent().getName()).append(",")
                        .append(String.valueOf(LocalDate.parse((new SimpleDateFormat("yyyy-MM-dd").format(commit.getCommitterIdent().getWhen()))))).append(DELIMITER);
            }

            flushAndCloseFW(fileWriter, logger, CLASS);
        } catch (IOException e) {
            logger.info(ERROR);
        }
    }

    public static void printTickets(String project, List<Ticket> ticketList) throws IOException {
        project = project.toLowerCase();
        File file = new File(MAINDIR + project);
        if (!file.exists()) {
            boolean created = file.mkdirs();
            if (!created) {
                throw new IOException();
            }
        }

        file = new File(MAINDIR + project+ SLASH+ "AllTickets.csv");
        try(FileWriter fileWriter = new FileWriter(file)) {

            fileWriter.append("key,creationDate,resolutionDate,injectedVersion,openingVersion,fixedVersion,affectedVersion,numOfCommits\n");

            List<Ticket> ticketOrderedByCreation = new ArrayList<>(ticketList);
            ticketOrderedByCreation.sort(Comparator.comparing(Ticket::getCreationDate));
            for (Ticket ticket : ticketOrderedByCreation) {
                List<String> iDs = new ArrayList<>();
                for(Release release : ticket.getAv()) {
                    iDs.add(release.getName());
                }
                fileWriter.append(ticket.getTicketID()).append(",")
                        .append(String.valueOf(ticket.getCreationDate())).append(",")
                        .append(String.valueOf(ticket.getResolutionDate())).append(",")
                        .append(ticket.getIv().getName()).append(",")
                        .append(ticket.getOv().getName()).append(",")
                        .append(ticket.getFv().getName()).append(",")
                        .append(String.valueOf(iDs)).append(",").append(DELIMITER);
                        //.append(String.valueOf(ticket.getCommitList().size())).append(DELIMITER);
            }

            flushAndCloseFW(fileWriter, logger, CLASS);
        } catch (IOException e) {
            logger.info(ERROR);
        }
    }

    private static void flushAndCloseFW(FileWriter fileWriter, Logger logger, String className) {
        try {
            fileWriter.flush();
            fileWriter.close();
        } catch (IOException e) {
            logger.info("Error in " + className + " while flushing/closing fileWriter !!!");
        }
    }

    public static void printReleases(String project, List<Release> releaseList, String name) throws IOException {
        project = project.toLowerCase();
        File file = new File(MAINDIR + project);
        if (!file.exists()) {
            boolean created = file.mkdirs();
            if (!created) {
                throw new IOException();
            }
        }

        file = new File(MAINDIR + project + SLASH+ name);
        try(FileWriter fileWriter = new FileWriter(file)) {

            fileWriter.append("id,releaseName,releaseDate,numOfCommits\n");

            for (Release release : releaseList) {
                fileWriter.append(String.valueOf(release.getId())).append(",")
                        .append(release.getName()).append(",")
                        .append(String.valueOf(release.getDate())).append(",")
                        .append(String.valueOf(release.getCommitList().size())).append(DELIMITER);
            }

            flushAndCloseFW(fileWriter, logger, CLASS);
        } catch (IOException e) {
            logger.info(ERROR);
        }
    }

        /*
            fileWriter.append("ProjectName").append(SEPARATOR)
                    .append("MethodFullyQualifiedName").append(SEPARATOR)
                    .append("ReleaseID").append(SEPARATOR)
                    .append("LOC").append(SEPARATOR)
                    .append("NumCommitsOnMethodVersion").append(SEPARATOR) // Commit che hanno formato QUESTA versione del metodo
                    .append("NumRevisionsTotal").append(SEPARATOR)      // Revisioni totali del metodo (se tracciate)
                    .append("NumAuthors").append(SEPARATOR)           // Autori totali del metodo (se tracciati)
                    .append("Bugginess").append(DELIMITER);             // yes/no

                fileWriter.append(project).append(SEPARATOR)
                        .append(escapeCsvString(method.getName())).append(SEPARATOR)
                        .append(method.getRelease() != null ? String.valueOf(method.getRelease().id()) : "N/A").append(SEPARATOR)
                        .append(String.valueOf(method.getLoc())).append(SEPARATOR)
                        .append(String.valueOf(method.getCommits() != null ? method.getCommits().size() : 0)).append(SEPARATOR)
                        .append(String.valueOf(method.getNumRevisions())).append(SEPARATOR) // Assumendo che JavaMethod abbia questo getter
                        .append(String.valueOf(method.getNumAuthors())).append(SEPARATOR)   // Assumendo che JavaMethod abbia questo getter
                        .append(method.isBuggy() ? "yes" : "no").append(DELIMITER);

    }*/


    public static void printMethods(String project, List<JavaMethod> methods, String name) throws IOException {
        project = project.toLowerCase();
        File file = new File(MAINDIR + project);
        if (!file.exists()) {
            boolean created = file.mkdirs();
            if (!created) {
                throw new IOException();
            }
        }

        file = new File(MAINDIR + project + SLASH + name);
        try (FileWriter fileWriter = new FileWriter(file)) {

            fileWriter.append("fullyQualifiedName, methodName, className,firstCommit,#Commits\n");

            for (JavaMethod m : methods) {
                String firstCommit;
                if (m.getCommits().isEmpty()) {
                    firstCommit = "";
                } else {
                    firstCommit = m.getCommits().get(0).toString();
                }

                fileWriter.append(m.getName()).append(",")
                        .append(m.getMethodName()).append(",")
                        .append(m.getClassName()).append(",")
                        .append(firstCommit).append(",")
                        .append(String.valueOf(m.getCommits().size()))
                        .append(DELIMITER);
            }

            flushAndCloseFW(fileWriter, logger, CLASS);
        } catch (IOException e) {
            logger.info(ERROR);
        }
    }

}
