import java.nio.file.*;
import java.util.*;

/**
 * Console interface for Task 4: Search Frequency.
 *
 * Usage:  java Main [path-to-csv-folder-or-file]
 * Default data path: ./data
 */
public class Main {

    public static void main(String[] args) throws Exception {
        Path dataPath = Paths.get(args.length > 0 ? args[0] : "data");
        SearchFrequencyTracker tracker = new SearchFrequencyTracker(Paths.get("search_log.txt"));

        int files = tracker.loadData(dataPath);
        System.out.println("Loaded " + files + " file(s) from " + dataPath.toAbsolutePath()
                + " | vocabulary: " + tracker.vocabularySize() + " words");
        int restored = tracker.restoreFromLog();
        if (restored > 0) {
            System.out.println("Restored " + restored + " past searches from search_log.txt");
        }

        Scanner in = new Scanner(System.in);
        while (true) {
            System.out.println();
            System.out.println("===== Search Frequency Tracker =====");
            System.out.println("1. Search a word / phrase");
            System.out.println("2. Show top N searches");
            System.out.println("3. Show search log");
            System.out.println("4. Check how often a query was searched");
            System.out.println("5. Tree statistics");
            System.out.println("0. Exit");
            System.out.print("Choose: ");
            if (!in.hasNextLine()) break;
            String choice = in.nextLine().trim();

            switch (choice) {
                case "1": {
                    System.out.print("Enter search query: ");
                    String q = in.hasNextLine() ? in.nextLine() : "";
                    SearchFrequencyTracker.SearchResult r = tracker.search(q);
                    if (r == null) { System.out.println("Empty query ignored."); break; }
                    System.out.println("Query \"" + r.query + "\" has now been searched "
                            + r.timesSearched + " time(s).");
                    if (r.occurrencesInData == 0) {
                        System.out.println("Not found in the crawled data.");
                    } else {
                        System.out.println("Appears " + r.occurrencesInData + " time(s) in the data, in "
                                + r.sources.size() + " source(s):");
                        int shown = 0;
                        for (String s : r.sources) {
                            if (shown++ == 10) {
                                System.out.println("  ... and " + (r.sources.size() - 10) + " more");
                                break;
                            }
                            System.out.println("  - " + s);
                        }
                    }
                    break;
                }
                case "2": {
                    System.out.print("How many top searches? ");
                    int n;
                    try { n = Integer.parseInt(in.nextLine().trim()); }
                    catch (Exception e) { n = 10; }
                    printTop(tracker, n);
                    break;
                }
                case "3": {
                    List<String> log = tracker.getLog();
                    if (log.isEmpty()) System.out.println("No searches yet.");
                    else log.forEach(e -> System.out.println("  " + e));
                    break;
                }
                case "4": {
                    System.out.print("Query: ");
                    String q = in.hasNextLine() ? in.nextLine() : "";
                    System.out.println("Searched " + tracker.frequencyOf(q) + " time(s).");
                    break;
                }
                case "5":
                    System.out.println("Distinct queries: " + tracker.distinctQueries()
                            + " | AVL tree height: " + tracker.treeHeight());
                    break;
                case "0":
                    System.out.println("Goodbye.");
                    return;
                default:
                    System.out.println("Invalid option.");
            }
        }
    }

    static void printTop(SearchFrequencyTracker tracker, int n) {
        List<AVLTree.Node> top = tracker.topSearches(n);
        if (top.isEmpty()) { System.out.println("No searches yet."); return; }
        System.out.printf("%-5s %-30s %s%n", "Rank", "Query", "Searches");
        int rank = 1;
        for (AVLTree.Node node : top) {
            System.out.printf("%-5d %-30s %d%n", rank++, node.query, node.frequency);
        }
    }
}
