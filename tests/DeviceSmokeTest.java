import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Teste funcional opcional, sem bibliotecas externas, em emulador Android já iniciado.
 * Uso: {@code java tests/DeviceSmokeTest.java --adb CAMINHO_DO_ADB [--serial emulator-5554] [--output DIR]}.
 */
public final class DeviceSmokeTest {
    private static final List<Integer> TARGET = List.of(1, 2, 3, 4, 5, 6, 7, 8, 0);
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    private static List<String> base;
    private static Path output;

    public static void main(String[] args) throws Exception {
        String adbPath = null;
        String serial = "emulator-5554";
        String outputDir = "/tmp/slide8-preview";
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--adb": adbPath = args[++i]; break;
                case "--serial": serial = args[++i]; break;
                case "--output": outputDir = args[++i]; break;
                default: usage("Argumento desconhecido: " + args[i]);
            }
        }
        if (adbPath == null) {
            usage("O argumento --adb é obrigatório.");
        }
        if (!serial.startsWith("emulator-")) {
            usage("Este teste altera a rotação e só deve rodar em um emulador.");
        }
        output = Paths.get(outputDir);
        Files.createDirectories(output);
        base = List.of(adbPath, "-s", serial);

        String rotation = text("shell", "settings", "get", "system", "user_rotation");
        String autoRotation = text("shell", "settings", "get", "system", "accelerometer_rotation");
        try {
            run();
        } finally {
            restore("user_rotation", rotation);
            restore("accelerometer_rotation", autoRotation);
        }
    }

    private static void run() throws Exception {
        adb("shell", "am", "force-stop", "com.example.slide8");
        adb("shell", "am", "start", "-n", "com.example.slide8/.MainActivity");
        sleep(700);
        Map<String, Map<String, String>> nodes = ui();
        check(nodes.containsKey("start_game") && !nodes.containsKey("board_grid"),
                "Abertura deve mostrar a tela inicial");
        screenshot("home");
        testPreferences();
        nodes = ui();
        tap(nodes.get("start_game"));
        nodes = ui();
        List<Integer> initial = board(nodes);
        check(nodes.get("moves").get("text").equals("0"), "Partida deve começar sem movimentos");
        int clicked = neighbors(initial).get(0);
        tap(nodes.get("tile" + clicked));
        nodes = ui();
        check(board(nodes).equals(initial) && nodes.get("moves").get("text").equals("0"),
                "Toque simples não deve mover");
        int[] start = center(nodes.get("tile" + clicked));
        int[] end = center(nodes.get("tile" + initial.indexOf(0)));
        swipe(start, new double[]{start[0] - (end[0] - start[0]) * 0.4, start[1] - (end[1] - start[1]) * 0.4});
        nodes = ui();
        check(board(nodes).equals(initial), "Arraste contrário não deve mover");
        start = center(nodes.get("tile" + clicked));
        end = center(nodes.get("tile" + initial.indexOf(0)));
        swipe(start, new double[]{start[0] + (end[0] - start[0]) * 0.1, start[1] + (end[1] - start[1]) * 0.1});
        nodes = ui();
        check(board(nodes).equals(initial), "Arraste curto não deve mover");
        swipe(center(nodes.get("tile" + clicked)), toDouble(center(nodes.get("tile" + initial.indexOf(0)))));
        nodes = ui();
        List<Integer> expected = new ArrayList<>(initial);
        expected.set(initial.indexOf(0), initial.get(clicked));
        expected.set(clicked, 0);
        check(board(nodes).equals(expected) && nodes.get("moves").get("text").equals("1"),
                "Arraste válido deve mover uma peça");
        screenshot("game");
        String beforeTheme = scrollTo("game_theme").get("text");
        tap(scrollTo("game_theme"));
        check(!scrollTo("game_theme").get("text").equals(beforeTheme), "Tema deve alternar durante a partida");
        scrollToTop();
        nodes = ui();
        check(board(nodes).equals(expected) && nodes.get("moves").get("text").equals("1"),
                "Trocar tema deve preservar tabuleiro e movimentos");
        check(!nodes.get("timer").get("text").equals("00:00"), "Trocar tema não deve zerar o tempo");
        screenshot("game-alternate-theme");
        tap(scrollTo("game_theme"));
        scrollToTop();
        nodes = ui();
        System.out.println("OK: tela inicial, toque sem movimento e arrastes inválidos/válido.");

        List<Integer> state = board(nodes);
        tap(nodes.get("pause_game"));
        nodes = ui();
        check(nodes.get("start_game").get("text").contains("Continuar"), "Pausa deve permitir continuar");
        sleep(1000);
        tap(nodes.get("start_game"));
        nodes = ui();
        check(board(nodes).equals(state) && nodes.get("moves").get("text").equals("1"),
                "Pausa não pode perder a partida");
        adb("shell", "input", "keyevent", "KEYCODE_BACK");
        nodes = ui();
        check(nodes.containsKey("start_game"), "Voltar deve abrir a tela inicial");
        tap(nodes.get("start_game"));
        adb("shell", "settings", "put", "system", "accelerometer_rotation", "0");
        rotate();
        nodes = ui();
        check(board(nodes).equals(state) && nodes.get("moves").get("text").equals("1"),
                "Rotação não pode perder a jogada");
        System.out.println("OK: pausar, continuar, voltar e recriar a Activity por rotação.");

        List<Integer> path = solve(state);
        System.out.println("Concluindo a partida por " + path.size() + " gestos reais...");
        List<int[]> positions = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            positions.add(center(nodes.get("tile" + i)));
        }
        List<Integer> current = new ArrayList<>(state);
        for (int move : path) {
            int empty = current.indexOf(0);
            swipe(positions.get(move), toDouble(positions.get(empty)));
            current.set(empty, current.get(move));
            current.set(move, 0);
        }
        nodes = ui();
        check(nodes.containsKey("victory_again"), "A última jogada deve abrir a vitória");
        String victoryStats = nodes.get("victory_stats").get("text");
        check(victoryStats.contains(String.valueOf(path.size() + 1)),
                "Vitória deve exibir o total de movimentos");
        rotate();
        nodes = ui();
        check(nodes.get("victory_stats").get("text").equals(victoryStats),
                "Vitória e tempo parado devem sobreviver à rotação");
        screenshot("victory");
        tap(nodes.get("victory_again"));
        nodes = ui();
        check(nodes.get("moves").get("text").equals("0") && !board(nodes).equals(TARGET),
                "Jogar de novo deve iniciar uma nova partida");
        System.out.println("OK: solução por gestos, diálogo de vitória e nova partida. Capturas em " + output);
    }

    private static void testPreferences() throws Exception {
        String originalTheme = scrollTo("home_theme").get("text");
        String originalSound = scrollTo("home_sound").get("text");
        tap(scrollTo("home_theme"));
        String changedTheme = scrollTo("home_theme").get("text");
        check(!changedTheme.equals(originalTheme), "Botão deve alternar o tema");
        screenshot("home-alternate-theme");
        tap(scrollTo("home_sound"));
        String changedSound = scrollTo("home_sound").get("text");
        check(!changedSound.equals(originalSound), "Botão deve alternar o som");
        tap(scrollTo("about"));
        Map<String, Map<String, String>> nodes = ui();
        check(nodes.containsKey("about_close"), "Sobre deve abrir com botão para fechar");
        screenshot("about");
        tap(nodes.get("about_close"));
        adb("shell", "am", "force-stop", "com.example.slide8");
        adb("shell", "am", "start", "-n", "com.example.slide8/.MainActivity");
        sleep(700);
        check(scrollTo("home_theme").get("text").equals(changedTheme), "Tema deve persistir após reabrir");
        check(scrollTo("home_sound").get("text").equals(changedSound), "Som deve persistir após reabrir");
        tap(scrollTo("home_theme"));
        tap(scrollTo("home_sound"));
        check(scrollTo("home_theme").get("text").equals(originalTheme), "Tema original deve ser restaurado");
        check(scrollTo("home_sound").get("text").equals(originalSound), "Som original deve ser restaurado");
        scrollToTop();
        System.out.println("OK: Sobre, temas, som e preferências persistentes.");
    }

    private static Map<String, String> scrollTo(String id) throws Exception {
        for (int attempt = 0; attempt < 6; attempt++) {
            Map<String, Map<String, String>> nodes = ui();
            Map<String, String> node = nodes.get(id);
            if (node != null && node.get("enabled").equals("true")) return node;
            scrollScreen(true);
        }
        throw new AssertionError("Controle não encontrado: " + id);
    }

    private static void scrollToTop() throws Exception {
        scrollScreen(false);
        scrollScreen(false);
    }

    private static void scrollScreen(boolean down) throws Exception {
        Map<String, Map<String, String>> nodes = ui();
        Map<String, String> screen = nodes.get(nodes.containsKey("home_screen") ? "home_screen" : "game_screen");
        Matcher matcher = NUMBER.matcher(screen.get("bounds"));
        int[] bounds = new int[4];
        for (int i = 0; i < 4 && matcher.find(); i++) bounds[i] = Integer.parseInt(matcher.group());
        int x = bounds[0] + 8; // Margem fora do tabuleiro, para não arrastar peças.
        int top = bounds[1] + (bounds[3] - bounds[1]) / 5;
        int bottom = bounds[3] - (bounds[3] - bounds[1]) / 5;
        swipe(new int[]{x, down ? bottom : top}, new double[]{x, down ? top : bottom});
    }

    private static byte[] adb(String... command) throws IOException, InterruptedException {
        List<String> full = new ArrayList<>(base);
        full.addAll(Arrays.asList(command));
        Process process = new ProcessBuilder(full).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        try (InputStream in = process.getInputStream()) {
            in.transferTo(result);
        }
        if (!process.waitFor(40, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("Tempo esgotado: " + full);
        }
        if (process.exitValue() != 0) {
            throw new IOException("Comando falhou (" + process.exitValue() + "): " + full);
        }
        return result.toByteArray();
    }

    private static String text(String... command) throws IOException, InterruptedException {
        return new String(adb(command), StandardCharsets.UTF_8).trim();
    }

    private static Map<String, Map<String, String>> ui() throws Exception {
        String result = text("shell", "uiautomator", "dump", "/sdcard/slide8-window.xml");
        check(result.contains("dumped to"), "A captura da hierarquia falhou; não reutilizar arquivo antigo");
        byte[] xml = adb("exec-out", "cat", "/sdcard/slide8-window.xml");
        NodeList list = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml)).getElementsByTagName("node");
        Map<String, Map<String, String>> nodes = new HashMap<>();
        for (int i = 0; i < list.getLength(); i++) {
            Element node = (Element) list.item(i);
            String id = node.getAttribute("resource-id");
            if (id.isEmpty()) {
                continue;
            }
            Map<String, String> attributes = new HashMap<>();
            for (int j = 0; j < node.getAttributes().getLength(); j++) {
                attributes.put(node.getAttributes().item(j).getNodeName(), node.getAttributes().item(j).getNodeValue());
            }
            nodes.put(id.substring(id.lastIndexOf(":id/") + 4), attributes);
        }
        return nodes;
    }

    private static int[] center(Map<String, String> node) {
        Matcher m = NUMBER.matcher(node.get("bounds"));
        int[] b = new int[4];
        for (int i = 0; i < 4 && m.find(); i++) {
            b[i] = Integer.parseInt(m.group());
        }
        return new int[]{(b[0] + b[2]) / 2, (b[1] + b[3]) / 2};
    }

    private static double[] toDouble(int[] point) {
        return new double[]{point[0], point[1]};
    }

    private static void tap(Map<String, String> node) throws Exception {
        int[] c = center(node);
        adb("shell", "input", "tap", String.valueOf(c[0]), String.valueOf(c[1]));
        sleep(400);
    }

    private static void swipe(int[] start, double[] end) throws Exception {
        adb("shell", "input", "swipe", String.valueOf(start[0]), String.valueOf(start[1]),
                String.valueOf((int) end[0]), String.valueOf((int) end[1]), "260");
        sleep(300);
    }

    private static void rotate() throws Exception {
        adb("shell", "settings", "put", "system", "user_rotation", "1");
        sleep(1000);
        adb("shell", "settings", "put", "system", "user_rotation", "0");
        sleep(1000);
    }

    private static List<Integer> board(Map<String, Map<String, String>> nodes) {
        List<Integer> state = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            String value = nodes.get("tile" + i).get("text");
            state.add(value == null || value.isEmpty() ? 0 : Integer.parseInt(value));
        }
        return state;
    }

    private static void screenshot(String name) throws Exception {
        Files.write(output.resolve(name + ".png"), adb("exec-out", "screencap", "-p"));
    }

    private static List<Integer> neighbors(List<Integer> state) {
        int empty = state.indexOf(0);
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            if (Math.abs(i / 3 - empty / 3) + Math.abs(i % 3 - empty % 3) == 1) {
                result.add(i);
            }
        }
        return result;
    }

    private static int distance(List<Integer> state) {
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            int v = state.get(i);
            if (v != 0) {
                sum += Math.abs(i / 3 - (v - 1) / 3) + Math.abs(i % 3 - (v - 1) % 3);
            }
        }
        return sum;
    }

    /** Busca A* pela menor sequência de posições clicadas até o objetivo. */
    private static List<Integer> solve(List<Integer> initial) {
        final class Entry {
            final int priority;
            final int cost;
            final List<Integer> state;
            final List<Integer> path;

            Entry(int cost, List<Integer> state, List<Integer> path) {
                this.priority = cost + distance(state);
                this.cost = cost;
                this.state = state;
                this.path = path;
            }
        }
        PriorityQueue<Entry> queue = new PriorityQueue<>((a, b) -> a.priority != b.priority
                ? Integer.compare(a.priority, b.priority) : Integer.compare(a.cost, b.cost));
        Map<List<Integer>, Integer> best = new HashMap<>();
        queue.add(new Entry(0, initial, List.of()));
        best.put(initial, 0);
        while (!queue.isEmpty()) {
            Entry entry = queue.poll();
            if (entry.state.equals(TARGET)) {
                return entry.path;
            }
            if (entry.cost != best.get(entry.state)) {
                continue;
            }
            int empty = entry.state.indexOf(0);
            for (int clicked : neighbors(entry.state)) {
                List<Integer> next = new ArrayList<>(entry.state);
                next.set(empty, next.get(clicked));
                next.set(clicked, 0);
                int cost = entry.cost + 1;
                if (cost < best.getOrDefault(next, Integer.MAX_VALUE)) {
                    best.put(next, cost);
                    List<Integer> path = new ArrayList<>(entry.path);
                    path.add(clicked);
                    queue.add(new Entry(cost, List.copyOf(next), path));
                }
            }
        }
        throw new AssertionError("Tabuleiro sem solução");
    }

    private static void restore(String key, String value) throws Exception {
        if (value.equals("null")) {
            adb("shell", "settings", "delete", "system", key);
        } else {
            adb("shell", "settings", "put", "system", key, value);
        }
    }

    private static void sleep(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void usage(String message) {
        System.err.println(message);
        System.err.println("Uso: java tests/DeviceSmokeTest.java --adb CAMINHO [--serial emulator-5554] [--output DIR]");
        System.exit(2);
    }
}
