import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/** Gera os efeitos originais do jogo, sem dependências. Uso: java tests/GenerateSounds.java. */
public final class GenerateSounds {
    private static final int RATE = 22050;

    public static void main(String[] args) throws Exception {
        write("move", new double[]{660}, 0.075, 0.0);
        write("shuffle", new double[]{392, 523.25}, 0.085, 0.025);
        write("victory", new double[]{523.25, 659.25, 783.99, 1046.50}, 0.14, 0.025);
    }

    private static void write(String name, double[] notes, double duration, double gap) throws Exception {
        int noteFrames = (int) (duration * RATE);
        int step = noteFrames + (int) (gap * RATE);
        byte[] pcm = new byte[step * notes.length * 2];
        for (int n = 0; n < notes.length; n++) {
            for (int i = 0; i < noteFrames; i++) {
                double t = (double) i / RATE;
                double envelope = Math.min(1.0, t / 0.006)
                        * Math.min(1.0, (duration - t) / 0.025) * Math.exp(-3.0 * t / duration);
                double phase = 2 * Math.PI * notes[n] * t;
                short sample = (short) (12000 * envelope * (Math.sin(phase) + 0.18 * Math.sin(2 * phase)));
                int offset = (n * step + i) * 2;
                pcm[offset] = (byte) sample;
                pcm[offset + 1] = (byte) (sample >> 8);
            }
        }
        Path output = Path.of("app/src/main/res/raw", name + ".wav");
        Files.createDirectories(output.getParent());
        AudioFormat format = new AudioFormat(RATE, 16, 1, true, false);
        try (AudioInputStream audio = new AudioInputStream(new ByteArrayInputStream(pcm), format, pcm.length / 2)) {
            AudioSystem.write(audio, AudioFileFormat.Type.WAVE, output.toFile());
        }
        System.out.println(output + " (" + pcm.length / 2 + " amostras)");
    }
}
