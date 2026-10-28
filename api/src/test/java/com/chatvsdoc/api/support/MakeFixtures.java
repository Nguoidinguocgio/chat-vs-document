package com.chatvsdoc.api.support;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * Sinh PDF mẫu cho test bằng PDFBox (T08 mục 5) thay vì commit file nhị phân. Chữ tiếng Việt dùng
 * font DejaVu Sans (giấy phép tự do, {@code src/test/resources/fonts/LICENSE_DEJAVU}).
 */
public final class MakeFixtures {

    private MakeFixtures() {}

    /** Đoạn văn mẫu tiếng Việt có dấu, gắn mã trang để kiểm tra chunk đúng trang nguồn. */
    public static String viParagraph(int page, int n) {
        return "TRANG-"
                + page
                + " đoạn "
                + n
                + ". Điều khoản thanh toán: bên mua thanh toán trong vòng ba mươi ngày kể từ ngày nhận"
                + " hóa đơn hợp lệ. Mọi khoản phạt chậm trả được tính theo lãi suất ngân hàng nhà nước"
                + " công bố tại thời điểm thanh toán; tiền tệ là đồng Việt Nam.";
    }

    public static byte[] okVi5p() throws IOException {
        List<List<String>> pages = new ArrayList<>();
        for (int p = 1; p <= 5; p++) {
            List<String> paras = new ArrayList<>();
            for (int i = 1; i <= 4; i++) {
                paras.add(viParagraph(p, i));
            }
            pages.add(paras);
        }
        return textPdf(pages);
    }

    public static byte[] okEn() throws IOException {
        return textPdf(
                List.of(
                        List.of(
                                "Payment terms: the buyer shall pay within thirty days of receiving a valid"
                                        + " invoice. Late payment interest applies at the central bank rate.")));
    }

    public static byte[] pages(int count) throws IOException {
        List<List<String>> pages = new ArrayList<>();
        for (int p = 1; p <= count; p++) {
            pages.add(List.of(viParagraph(p, 1), viParagraph(p, 2)));
        }
        return textPdf(pages);
    }

    public static byte[] encrypted() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            writeTextPage(doc, List.of(viParagraph(1, 1)));
            StandardProtectionPolicy policy =
                    new StandardProtectionPolicy(
                            "owner-secret", "user-secret", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            doc.protect(policy);
            return save(doc);
        }
    }

    public static byte[] scannedImagesOnly() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < 3; i++) {
                writeImagePage(doc);
            }
            return save(doc);
        }
    }

    /** 13 trang: trang 2, 5, 9 là ảnh, còn lại có chữ. */
    public static byte[] mixed3Scan10Text() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            for (int p = 1; p <= 13; p++) {
                if (p == 2 || p == 5 || p == 9) {
                    writeImagePage(doc);
                } else {
                    writeTextPage(doc, List.of(viParagraph(p, 1)));
                }
            }
            return save(doc);
        }
    }

    /** Đầu file {@code %PDF-} hợp lệ nhưng phần còn lại là rác. */
    public static byte[] corrupted() {
        byte[] junk = new byte[4096];
        new Random(42).nextBytes(junk);
        byte[] head = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
        byte[] out = new byte[head.length + junk.length];
        System.arraycopy(head, 0, out, 0, head.length);
        System.arraycopy(junk, 0, out, head.length, junk.length);
        return out;
    }

    public static byte[] textPdf(List<List<String>> pages) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            for (List<String> paragraphs : pages) {
                writeTextPage(doc, paragraphs);
            }
            return save(doc);
        }
    }

    private static void writeTextPage(PDDocument doc, List<String> paragraphs) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        PDType0Font font;
        try (InputStream ttf = MakeFixtures.class.getResourceAsStream("/fonts/DejaVuSans.ttf")) {
            font = PDType0Font.load(doc, ttf);
        }
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            cs.beginText();
            cs.setFont(font, 11);
            cs.setLeading(14);
            cs.newLineAtOffset(50, 790);
            for (String para : paragraphs) {
                for (String line : wrap(para, 90)) {
                    cs.showText(line);
                    cs.newLine();
                }
                cs.newLine(); // dòng trống giữa các đoạn
            }
            cs.endText();
        }
    }

    private static void writeImagePage(PDDocument doc) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        BufferedImage img = new BufferedImage(200, 280, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 200, 280);
        g.setColor(Color.BLACK);
        for (int y = 20; y < 260; y += 12) {
            g.fillRect(20, y, 160, 4); // giả dòng chữ in trong ảnh scan
        }
        g.dispose();
        PDImageXObject x = LosslessFactory.createFromImage(doc, img);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            cs.drawImage(x, 50, 200, 400, 560);
        }
    }

    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() + word.length() + 1 > width && !line.isEmpty()) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(word);
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    private static byte[] save(PDDocument doc) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.save(out);
        return out.toByteArray();
    }
}
