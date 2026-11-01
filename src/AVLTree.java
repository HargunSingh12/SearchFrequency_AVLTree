import java.util.ArrayList;
import java.util.List;

/**
 * Self-balancing AVL tree that stores each search query and how many
 * times it has been searched.
 *
 * Key   = normalized search query (lower-case, single spaces)
 * Value = search frequency
 *
 * All operations (insert / update / lookup) are O(log n) because the
 * tree is rebalanced after every insertion.
 */
public class AVLTree {

    /** One node of the tree: a query, its frequency, and child links. */
    static class Node {
        String query;
        int frequency;
        int height;
        Node left, right;

        Node(String query) {
            this.query = query;
            this.frequency = 1;
            this.height = 1;
        }
    }

    private Node root;
    private int size; // number of distinct queries

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Records one search for the given query.
     * If the query already exists its count is incremented,
     * otherwise a new node is inserted with count 1.
     */
    public void recordSearch(String query) {
        root = insertOrIncrement(root, query);
    }

    /**
     * Removes one search for the given query (used when an old search
     * expires). The count never goes below 0.
     */
    public void removeSearch(String query) {
        Node n = root;
        while (n != null) {
            int cmp = query.compareTo(n.query);
            if (cmp == 0) {
                if (n.frequency > 0) n.frequency--;
                return;
            }
            n = (cmp < 0) ? n.left : n.right;
        }
    }

    /** Returns how many times the query has been searched (0 if never). */
    public int getFrequency(String query) {
        Node n = root;
        while (n != null) {
            int cmp = query.compareTo(n.query);
            if (cmp == 0) return n.frequency;
            n = (cmp < 0) ? n.left : n.right;
        }
        return 0;
    }

    /** Number of distinct queries stored. */
    public int size() {
        return size;
    }

    /** Height of the tree (useful to show the tree stays balanced). */
    public int height() {
        return height(root);
    }

    /** Returns all nodes in alphabetical (in-order) order. */
    public List<Node> inOrder() {
        List<Node> out = new ArrayList<>();
        inOrder(root, out);
        return out;
    }

    // ------------------------------------------------------------------
    // Insertion with rebalancing
    // ------------------------------------------------------------------

    private Node insertOrIncrement(Node node, String query) {
        // 1. Normal BST insertion
        if (node == null) {
            size++;
            return new Node(query);
        }
        int cmp = query.compareTo(node.query);
        if (cmp < 0) {
            node.left = insertOrIncrement(node.left, query);
        } else if (cmp > 0) {
            node.right = insertOrIncrement(node.right, query);
        } else {
            node.frequency++;   // query already exists: update its count
            return node;        // structure unchanged, no rebalancing needed
        }

        // 2. Update height
        updateHeight(node);

        // 3. Rebalance if needed
        int balance = balanceFactor(node);

        // Left-Left case
        if (balance > 1 && query.compareTo(node.left.query) < 0) {
            return rotateRight(node);
        }
        // Right-Right case
        if (balance < -1 && query.compareTo(node.right.query) > 0) {
            return rotateLeft(node);
        }
        // Left-Right case
        if (balance > 1 && query.compareTo(node.left.query) > 0) {
            node.left = rotateLeft(node.left);
            return rotateRight(node);
        }
        // Right-Left case
        if (balance < -1 && query.compareTo(node.right.query) < 0) {
            node.right = rotateRight(node.right);
            return rotateLeft(node);
        }
        return node;
    }

    // ------------------------------------------------------------------
    // Rotations and helpers
    // ------------------------------------------------------------------

    private Node rotateRight(Node y) {
        Node x = y.left;
        Node t2 = x.right;
        x.right = y;
        y.left = t2;
        updateHeight(y);
        updateHeight(x);
        return x;
    }

    private Node rotateLeft(Node x) {
        Node y = x.right;
        Node t2 = y.left;
        y.left = x;
        x.right = t2;
        updateHeight(x);
        updateHeight(y);
        return y;
    }

    private int height(Node n) {
        return (n == null) ? 0 : n.height;
    }

    private void updateHeight(Node n) {
        n.height = 1 + Math.max(height(n.left), height(n.right));
    }

    private int balanceFactor(Node n) {
        return (n == null) ? 0 : height(n.left) - height(n.right);
    }

    private void inOrder(Node n, List<Node> out) {
        if (n == null) return;
        inOrder(n.left, out);
        out.add(n);
        inOrder(n.right, out);
    }
}