package leaves_ident;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Union-Find Algorithm Tests")
class UnionAlgorithmTest {

    private UnionFind uf;

    @BeforeEach
    void setUp() {
        uf = new UnionFind(100);
    }

    // ============================================================
    // UNION-FIND CORE TESTS
    // ============================================================

    @Test
    @DisplayName("1. Each element starts as its own parent")
    void testInitialState() {
        for (int i = 0; i < 10; i++) {
            assertEquals(i, uf.find(i), "Element " + i + " should point to itself");
            assertEquals(1, uf.getSize(i), "Each set should have size 1");
        }
    }

    @Test
    @DisplayName("2. Union connects two separate elements")
    void testUnionTwoElements() {
        uf.union(5, 10);
        assertEquals(uf.find(5), uf.find(10), "Elements should be in same set");
        assertEquals(2, uf.getSize(5), "Set size should be 2");
    }

    @Test
    @DisplayName("3. Chain of unions connects multiple elements")
    void testUnionChain() {
        for (int i = 0; i < 5; i++) {
            uf.union(i, i + 1);
        }
        int root = uf.find(0);
        for (int i = 1; i <= 5; i++) {
            assertEquals(root, uf.find(i), "Element " + i + " should be connected");
        }
        assertEquals(6, uf.getSize(0), "Set should have 6 elements");
    }

    @Test
    @DisplayName("4. Path compression flattens the tree")
    void testPathCompression() {
        for (int i = 99; i > 0; i--) {
            uf.union(i, i - 1);
        }
        int root = uf.find(99);
        for (int i = 0; i < 100; i++) {
            assertEquals(root, uf.find(i), "All elements should share same root");
        }
    }

    @Test
    @DisplayName("5. Union by size - smaller attaches to larger")
    void testUnionBySize() {
        uf.union(0, 1);
        uf.union(1, 2);
        int rootLarge = uf.find(0);

        uf.union(3, 4);
        uf.union(0, 3);

        assertEquals(rootLarge, uf.find(3), "Smaller set should attach to larger");
        assertEquals(5, uf.getSize(0), "Combined size should be 5");
    }

    @Test
    @DisplayName("6. Union same set does nothing")
    void testUnionSameSet() {
        uf.union(10, 20);
        int originalRoot = uf.find(10);
        int originalSize = uf.getSize(10);

        uf.union(10, 20);

        assertEquals(originalRoot, uf.find(10), "Root should not change");
        assertEquals(originalSize, uf.getSize(10), "Size should not change");
    }

    @Test
    @DisplayName("7. Large scale test - 1000 elements")
    void testLargeScale() {
        UnionFind large = new UnionFind(1000);
        for (int i = 0; i < 999; i++) {
            large.union(i, i + 1);
        }
        int root = large.find(0);
        for (int i = 1; i < 1000; i++) {
            assertEquals(root, large.find(i), "All 1000 elements should be connected");
        }
        assertEquals(1000, large.getSize(0), "Set size should be 1000");
    }

    @Test
    @DisplayName("8. Get size returns correct value after unions")
    void testGetSize() {
        assertEquals(1, uf.getSize(0));
        uf.union(0, 1);
        assertEquals(2, uf.getSize(0));
        assertEquals(2, uf.getSize(1));
        uf.union(2, 3);
        uf.union(0, 2);
        assertEquals(4, uf.getSize(0));
    }

    @Test
    @DisplayName("9. Find with path compression works on deep tree")
    void testDeepTreeCompression() {
        // Create chain: 0->1->2->3->4
        for (int i = 0; i < 4; i++) {
            uf.union(i, i + 1);
        }
        // Find compresses path
        uf.find(4);
        // All should have same root
        int root = uf.find(0);
        assertEquals(root, uf.find(1));
        assertEquals(root, uf.find(2));
        assertEquals(root, uf.find(3));
        assertEquals(root, uf.find(4));
    }

    @Test
    @DisplayName("10. Multiple disconnected sets")
    void testDisconnectedSets() {
        uf.union(0, 1);
        uf.union(1, 2);
        uf.union(10, 11);
        uf.union(11, 12);

        int root1 = uf.find(0);
        int root2 = uf.find(10);

        assertNotEquals(root1, root2, "Disconnected sets should have different roots");
        assertEquals(3, uf.getSize(0));
        assertEquals(3, uf.getSize(10));
    }
}