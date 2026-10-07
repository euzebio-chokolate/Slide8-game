import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * Compila MainActivity e PuzzleLogicTest e executa os testes de lógica.
 * Uso, a partir da raiz do projeto: {@code java tests/RunLogicTests.java}.
 * Execute {@code ./gradlew :app:assembleDebug} antes, para gerar a classe R.
 */
public final class RunLogicTests {
    public static void main(String[] args) throws Exception {
        Path root = Paths.get("").toAbsolutePath();
        Path sdk = findSdk(root);
        Path androidJar = findLast(sdk.resolve("platforms"), "android.jar");
        Path rJar = findR(root.resolve("app/build/intermediates"));
        if (androidJar == null || rJar == null) {
            fail("SDK ou R.jar ausente. Execute ./gradlew :app:assembleDebug primeiro.");
        }

        Path output = Files.createTempDirectory("slide8-tests");
        try {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                fail("Compilador Java indisponível. Use um JDK, não um JRE.");
            }
            String classpath = androidJar + java.io.File.pathSeparator + rJar;
            int result = compiler.run(null, null, null,
                    "-encoding", "UTF-8", "-cp", classpath, "-d", output.toString(),
                    root.resolve("app/src/main/java/com/example/slide8/MainActivity.java").toString(),
                    root.resolve("app/src/main/java/com/example/slide8/GameSounds.java").toString(),
                    root.resolve("app/src/main/java/com/example/slide8/GameHistory.java").toString(),
                    root.resolve("tests/PuzzleLogicTest.java").toString());
            if (result != 0) {
                fail("Falha na compilação dos testes.");
            }
            URL[] urls = {output.toUri().toURL(), androidJar.toUri().toURL(), rJar.toUri().toURL()};
            try (URLClassLoader loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
                Class<?> test = loader.loadClass("com.example.slide8.PuzzleLogicTest");
                test.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                System.err.println("FALHA: " + cause);
                System.exit(1);
            }
        } finally {
            deleteRecursively(output);
        }
    }

    private static Path findSdk(Path root) throws IOException {
        String sdk = Optional.ofNullable(System.getenv("ANDROID_HOME"))
                .orElse(System.getenv("ANDROID_SDK_ROOT"));
        Path localProperties = root.resolve("local.properties");
        if (sdk == null && Files.isRegularFile(localProperties)) {
            Properties properties = new Properties();
            try (var reader = Files.newBufferedReader(localProperties)) {
                properties.load(reader);
            }
            sdk = properties.getProperty("sdk.dir");
        }
        if (sdk == null) {
            fail("Defina ANDROID_HOME ou sdk.dir em local.properties.");
        }
        return Paths.get(sdk);
    }

    /** Retorna o android.jar da plataforma de maior versão instalada. */
    private static Path findLast(Path dir, String name) throws IOException {
        if (!Files.isDirectory(dir)) {
            return null;
        }
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(p -> p.getFileName().toString().equals(name))
                    .max(Comparator.comparing(RunLogicTests::platformVersion))
                    .orElse(null);
        }
    }

    private static Integer platformVersion(Path jar) {
        String digits = jar.getParent().getFileName().toString().replaceAll("\\D", "");
        return digits.isEmpty() ? 0 : Integer.parseInt(digits.substring(0, Math.min(digits.length(), 9)));
    }

    private static Path findR(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return null;
        }
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(p -> p.getFileName().toString().equals("R.jar"))
                    .filter(p -> p.toString().contains("debug"))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        List<Path> paths = new ArrayList<>();
        try (Stream<Path> files = Files.walk(dir)) {
            files.forEach(paths::add);
        }
        paths.sort(Comparator.reverseOrder());
        for (Path path : paths) {
            try {
                Files.delete(path);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static void fail(String message) {
        System.err.println(message);
        System.exit(1);
    }
}
