package leaves_ident;

import org.openjdk.jmh.annotations.*;
        import org.openjdk.jmh.Main;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 2)
@Measurement(iterations = 3)
@Fork(value = 1)
public class UnionFindBenchmark {

    private UnionFind uf;

    @Param({"100", "1000", "10000"})
    private int size;

    @Setup(Level.Invocation)
    public void setup() {
        uf = new UnionFind(size);
    }

    @Benchmark
    public void testSequentialUnion() {
        for (int i = 0; i < size - 1; i++) {
            uf.union(i, i + 1);
        }
    }

    @Benchmark
    public void testFindAllElements() {
        for (int i = 0; i < size; i++) {
            uf.find(i);
        }
    }

    @Benchmark
    public void testMixedOperations() {
        for (int i = 0; i < size / 2; i++) {
            uf.union(i, i + (size / 2));
            uf.find(i);
        }
    }

    @Benchmark
    public void testPathCompression() {
        for (int i = size - 1; i > 0; i--) {
            uf.union(i, i - 1);
        }
        uf.find(size - 1);
    }

    public static void main(String[] args) throws Exception {
        Main.main(args);
    }
}