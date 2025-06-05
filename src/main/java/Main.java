import Controller.GitDataExtractor;
import Controller.JiraDataExtractor;
import Model.JavaMethod;
import Model.Release;
import Model.Ticket;
import org.eclipse.jgit.revwalk.RevCommit;
import utils.PrintUtils;

import java.util.List;



public class Main {

    public static void main(String[] args) throws Exception {

        String print;
        String project1 = "SYNCOPE";
        String project2 = "SYNCOPE";


        JiraDataExtractor jiraExtractor = new JiraDataExtractor(project1);
        List<Release> fullReleaseList = jiraExtractor.getReleases();
        print = project1 +": releases extracted.";
        System.out.println(print);

        List<Ticket> ticketList = jiraExtractor.getFinalTickets(fullReleaseList, true);
        PrintUtils.printTickets(project1, ticketList);
        print = project1 +": tickets extracted.";
        System.out.println(print);

        GitDataExtractor gitExtractor = new GitDataExtractor(project1, fullReleaseList, ticketList);
        List<RevCommit> commitList = gitExtractor.getAllCommitsAndAssignToReleases();
        fullReleaseList = gitExtractor.getFullReleaseList();
        List<Release> releaseList = gitExtractor.getReleaseList();
        PrintUtils.printCommits(project1, commitList, "AllCommits.csv");
        print = project1 +": commits extracted.";
        System.out.println(print);

        List<RevCommit> filteredCommitList = gitExtractor.filterCommitsOfIssues();
        //need to update the ticket list
        ticketList = gitExtractor.getTicketList();
        PrintUtils.printCommits(project1, filteredCommitList, "FilteredCommits.csv");
        print = project1+": commits filtered.";
        System.out.println(print);

        PrintUtils.printReleases(project1, fullReleaseList, "AllReleases.csv");
        PrintUtils.printReleases(project1, releaseList, "AnalysisReleases.csv");
        print = project1 +": removed 66% releases.";
        System.out.println(print);

        List<JavaMethod> methodList = gitExtractor.getMethodsFromReleases();
        print = project1+": methods extracted.";
        System.out.println(print);

        PrintUtils.printMethods(project1, methodList, "AllMethods.csv");

        gitExtractor.setMethodBuggyness(methodList);

        PrintUtils.printMethodsDataset(project1, methodList);
        print = project1+": Dataset CSV created";
        System.out.println(print);

        System.out.println("Processing for " + project1 + " completed.");
    }
}