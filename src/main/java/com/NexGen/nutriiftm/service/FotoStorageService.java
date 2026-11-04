package com.NexGen.nutriiftm.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
public class FotoStorageService {

    public static final String URL_PREFIX = "/uploads/avatars/";

    private static final int TAMANHO = 256;
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final int MAX_LADO_PX = 6000; // protege contra imagens "bomba"
    private static final Pattern NOME_VALIDO =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg$");

    private final Path pastaAvatars;

    public FotoStorageService(@Value("${app.uploads.dir:./uploads}") String raiz) throws IOException {
        this.pastaAvatars = Path.of(raiz).toAbsolutePath().normalize().resolve("avatars");
        Files.createDirectories(pastaAvatars);
        log.info("Avatares serão salvos em {}", pastaAvatars);
    }

    /** Valida, recorta, redimensiona e salva. Devolve a URL pública. */
    public String salvarAvatar(MultipartFile arquivo) throws IOException {
        if (arquivo == null || arquivo.isEmpty() || arquivo.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("Envie uma imagem JPG ou PNG de até 5 MB.");
        }

        BufferedImage original;
        try (ImageInputStream in = ImageIO.createImageInputStream(arquivo.getInputStream())) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("Formato de imagem não suportado. Use JPG ou PNG.");
            }
            ImageReader reader = readers.next();
            try {
                String formato = reader.getFormatName().toLowerCase();
                if (!formato.equals("jpeg") && !formato.equals("png")) {
                    throw new IllegalArgumentException("Formato de imagem não suportado. Use JPG ou PNG.");
                }
                reader.setInput(in);
                if (reader.getWidth(0) > MAX_LADO_PX || reader.getHeight(0) > MAX_LADO_PX) {
                    throw new IllegalArgumentException("Imagem com dimensões grandes demais.");
                }
                original = reader.read(0);
            } finally {
                reader.dispose();
            }
        }
        if (original == null) {
            throw new IllegalArgumentException("Não foi possível ler a imagem.");
        }

        // recorte quadrado centralizado + redução
        int lado = Math.min(original.getWidth(), original.getHeight());
        int x = (original.getWidth() - lado) / 2;
        int y = (original.getHeight() - lado) / 2;

        BufferedImage saida = new BufferedImage(TAMANHO, TAMANHO, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = saida.createGraphics();
        try {
            g.setColor(Color.WHITE); // PNG transparente vira fundo branco
            g.fillRect(0, 0, TAMANHO, TAMANHO);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(original, 0, 0, TAMANHO, TAMANHO, x, y, x + lado, y + lado, null);
        } finally {
            g.dispose();
        }

        String nome = UUID.randomUUID() + ".jpg"; // nome nunca vem do usuário
        Path destino = pastaAvatars.resolve(nome);
        if (!ImageIO.write(saida, "jpg", destino.toFile())) {
            throw new IOException("Nenhum encoder JPEG disponível.");
        }
        return URL_PREFIX + nome;
    }

    /** Apaga a foto antiga, mas só se ela for um arquivo nosso (ignora fotos do Google etc.). */
    public void remover(String url) {
        if (url == null || !url.startsWith(URL_PREFIX)) return;
        String nome = url.substring(URL_PREFIX.length());
        if (!NOME_VALIDO.matcher(nome).matches()) return; // barra "../" e afins
        try {
            Files.deleteIfExists(pastaAvatars.resolve(nome));
        } catch (IOException e) {
            log.warn("Não foi possível apagar a foto antiga {}", nome, e);
        }
    }
}
