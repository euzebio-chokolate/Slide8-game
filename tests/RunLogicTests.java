import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/** Testes do modelo, gestos e cronômetro. Requer apenas JDK, sem SDK Android ou Gradle. */
public final class RunLogicTests {
    public static void main(String[] args) throws Exception {
        Path root = Paths.get("").toAbsolutePath();
        Path output = Files.createTempDirectory(Files.createDirectories(root.resolve("build")), "logic-tests-");
        try {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) throw new IllegalStateException("Use um JDK, não um JRE.");
            Path source = root.resolve("app/src/main/java/com/example/slide8");
            int result = compiler.run(null, null, null,
                    "-encoding", "UTF-8", "--release", "11", "-d", output.toString(),
                    source.resolve("PuzzleGame.java").toString(),
                    source.resolve("DragGesture.java").toString(),
                    source.resolve("GameTimer.java").toString(),
                    root.resolve("tests/PuzzleLogicTest.java").toString(),
                    root.resolve("tests/GameStateTest.java").toString());
            if (result != 0) throw new AssertionError("Falha na compilação dos testes.");
            try (URLClassLoader loader = new URLClassLoader(new URL[]{output.toUri().toURL()},
                    ClassLoader.getPlatformClassLoader())) {
                for (String name : new String[]{"PuzzleLogicTest", "GameStateTest"}) {
                    loader.loadClass("com.example.slide8." + name).getMethod("main", String[].class)
                            .invoke(null, (Object) new String[0]);
                }
            } catch (InvocationTargetException error) {
                throw new AssertionError("Falha nos testes", error.getCause());
            }
        } finally {
            try (Stream<Path> files = Files.walk(output)) {
                Path[] paths = files.sorted(Comparator.reverseOrder()).toArray(Path[]::new);
                for (Path path : paths) Files.delete(path);
            }
        }
    }
}
