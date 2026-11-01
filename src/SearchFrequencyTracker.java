import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Task 4: Search Frequency.
 *
 * - Loads the combined CSV/txt data from Assignment 1 so a search can report
 *   how often (and in which files/rows) a word appears.
 * - Every user search is stored in an AVL tree (query -> search count).
 * - Every search is appended to a log file with a timestamp.
 * - Top searches are produced by heap-sorting the tree's nodes by frequency.
 */
public class SearchFrequencyTracker {

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Searches older than this are forgotten (removed from counts and log). */
    public static final Duration RETENTION = Duration.ofHours(1);

    private final AVLTree searchTree = new AVLTree();
    private final List<String> searchLog = new ArrayList<>();
    private final Path logFile;

    /** word -> number of occurrences in the crawled data */
    private final Map<String, Integer> corpusCounts = new HashMap<>();
    /** word -> set of source lines (e.g. URLs / rows) where it appears */
    private final Map<String, Set<String>> corpusSources = new HashMap<>();

    public SearchFrequencyTracker(Path logFile) {
        this.logFile = logFile;
    }

    // ------------------------------------------------------------------
    // Loading data
    // ------------------------------------------------------------------

    /** Loads every .csv and .txt file from a folder (or a single file). */
    public int loadData(Path path) throws IOException {
        List<Path> files = new ArrayList<>();
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(path, "*.{csv,txt}")) {
                for (Path p : ds) files.add(p);
            }
        } else if (Files.exists(path)) {
            files.add(path);
        }
        Collections.sort(files);
        for (Path f : files) loadFile(f);
        return files.size();
    }

    private void loadFile(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        int rowNo = 0;
        for (String line : lines) {
            rowNo++;
            String source = file.getFileName() + " (row " + rowNo + ")";
            // If the row contains a URL, use it as the source label
            for (String cell : line.split(",")) {
                String c = cell.trim().replace("\"", "");
                if (c.startsWith("http://") || c.startsWith("https://")) {
                    source = c;
                    break;
                }
            }
            for (String w : tokenize(line)) {
                corpusCounts.merge(w, 1, Integer::sum);
                corpusSources.computeIfAbsent(w, k -> new LinkedHashSet<>()).add(source);
            }
        }
    }

    /** Lower-case alphabetic tokens of length >= 2; URLs are skipped. */
    static List<String> tokenize(String text) {
        List<String> out = new ArrayList<>();
        for (String raw : text.split("[\\s,;]+")) {
            if (raw.startsWith("http")) continue;
            for (String w : raw.toLowerCase().split("[^a-z0-9]+")) {
                if (w.length() >= 2 && !w.chars().allMatch(Character::isDigit)) out.add(w);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Search handling (Task 4 - Search Query Collection A & B)
    // ------------------------------------------------------------------

    /** Normalizes a raw query: trim, lower-case, collapse whitespace. */
    static String normalize(String query) {
        return query == null ? "" : query.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    /**
     * Handles one search query from the user:
     *  1. normalizes it,
     *  2. updates its count in the AVL tree,
     *  3. appends it to the search log,
     *  4. returns the result of looking it up in the crawled data.
     */
    public SearchResult search(String rawQuery) {
        String query = normalize(rawQuery);
        if (query.isEmpty()) return null;

        pruneExpired();                                  // forget searches older than 1 hour
        searchTree.recordSearch(query);                // update count in tree
        String entry = LocalDateTime.now().format(TS) + " | " + query;
        searchLog.add(entry);                            // in-memory log
        appendToLogFile(entry);                          // persistent log

        // Look up each word of the query in the corpus
        int occurrences = 0;
        Set<String> sources = new LinkedHashSet<>();
        for (String w : tokenize(query)) {
            occurrences += corpusCounts.getOrDefault(w, 0);
            sources.addAll(corpusSources.getOrDefault(w, Collections.emptySet()));
        }
        return new SearchResult(query, searchTree.getFrequency(query), occurrences, sources);
    }

    private void appendToLogFile(String entry) {
        try {
            Files.writeString(logFile, entry + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Warning: could not write to log file: " + e.getMessage());
        }
    }

    /**
     * Re-reads the previous log so counts survive between program runs.
     * Only searches from the last hour are restored; older ones are
     * dropped and removed from the log file.
     */
    public int restoreFromLog() throws IOException {
        if (!Files.exists(logFile)) return 0;
        int n = 0;
        for (String line : Files.readAllLines(logFile, StandardCharsets.UTF_8)) {
            int bar = line.indexOf(" | ");
            if (bar < 0) continue;
            String q = normalize(line.substring(bar + 3));
            if (q.isEmpty() || isExpired(line)) continue;
            searchTree.recordSearch(q);
            searchLog.add(line);
            n++;
        }
        rewriteLogFile();   // file now holds only the recent searches
        return n;
    }

    /** True if the log entry's timestamp is older than RETENTION. */
    private boolean isExpired(String logLine) {
        try {
            LocalDateTime t = LocalDateTime.parse(logLine.substring(0, 19), TS);
            return t.isBefore(LocalDateTime.now().minus(RETENTION));
        } catch (Exception e) {
            return true;    // unreadable timestamp: treat as expired
        }
    }

    /**
     * Removes searches older than RETENTION from the tree counts and the log.
     * The log is in time order, so we only need to check from the front.
     */
    private void pruneExpired() {
        boolean removed = false;
        while (!searchLog.isEmpty() && isExpired(searchLog.get(0))) {
            String old = searchLog.remove(0);
            searchTree.removeSearch(normalize(old.substring(old.indexOf(" | ") + 3)));
            removed = true;
        }
        if (removed) rewriteLogFile();
    }

    private void rewriteLogFile() {
        try {
            Files.write(logFile, searchLog, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Warning: could not write to log file: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Top searches (Task 4 - Search Frequency Update B)
    // ------------------------------------------------------------------

    /**
     * Returns the top N searches, sorted by frequency (descending),
     * ties broken alphabetically. Uses heap sort on the tree's nodes.
     */
    public List<AVLTree.Node> topSearches(int n) {
        pruneExpired();
        List<AVLTree.Node> nodes = new ArrayList<>();
        for (AVLTree.Node node : searchTree.inOrder()) {
            if (node.frequency > 0) nodes.add(node);   // skip fully expired queries
        }
        AVLTree.Node[] arr = nodes.toArray(new AVLTree.Node[0]);
        heapSort(arr);
        List<AVLTree.Node> top = new ArrayList<>();
        for (int i = 0; i < Math.min(n, arr.length); i++) top.add(arr[i]);
        return top;
    }

    /** "Comes before" for descending frequency, then alphabetical. */
    private static boolean before(AVLTree.Node a, AVLTree.Node b) {
        if (a.frequency != b.frequency) return a.frequency > b.frequency;
        return a.query.compareTo(b.query) < 0;
    }

    /** Heap sort that puts the most-searched queries first. */
    static void heapSort(AVLTree.Node[] a) {
        int n = a.length;
        // Build a heap whose root is the element that should come LAST
        for (int i = n / 2 - 1; i >= 0; i--) siftDown(a, n, i);
        for (int end = n - 1; end > 0; end--) {
            swap(a, 0, end);       // move "last" element to the end
            siftDown(a, end, 0);
        }
    }

    private static void siftDown(AVLTree.Node[] a, int n, int i) {
        while (true) {
            int l = 2 * i + 1, r = l + 1, target = i;
            // the heap root is the element that sorts last, i.e. "!before"
            if (l < n && before(a[target], a[l])) target = l;
            if (r < n && before(a[target], a[r])) target = r;
            if (target == i) return;
            swap(a, i, target);
            i = target;
        }
    }

    private static void swap(AVLTree.Node[] a, int i, int j) {
        AVLTree.Node t = a[i]; a[i] = a[j]; a[j] = t;
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    public List<String> getLog() { pruneExpired(); return Collections.unmodifiableList(searchLog); }
    public int treeHeight() { return searchTree.height(); }
    public int vocabularySize() { return corpusCounts.size(); }
    public int frequencyOf(String q) { pruneExpired(); return searchTree.getFrequency(normalize(q)); }

    /** Number of different queries searched in the last hour. */
    public int distinctQueries() {
        pruneExpired();
        int count = 0;
        for (AVLTree.Node node : searchTree.inOrder()) if (node.frequency > 0) count++;
        return count;
    }

    /** Result of one search, returned to the user interface. */
    public static class SearchResult {
        public final String query;
        public final int timesSearched;
        public final int occurrencesInData;
        public final Set<String> sources;

        SearchResult(String query, int timesSearched, int occurrencesInData, Set<String> sources) {
            this.query = query;
            this.timesSearched = timesSearched;
            this.occurrencesInData = occurrencesInData;
            this.sources = sources;
        }
    }
}