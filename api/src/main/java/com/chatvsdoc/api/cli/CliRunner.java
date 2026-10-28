package com.chatvsdoc.api.cli;

import com.chatvsdoc.api.domain.entity.User;
import com.chatvsdoc.api.domain.entity.UserRole;
import com.chatvsdoc.api.service.auth.UserAdminService;
import com.chatvsdoc.api.service.retrieval.ContextBlock;
import com.chatvsdoc.api.service.retrieval.Retriever;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Lệnh quản trị (profile {@code cli}):
 *
 * <pre>
 * create-user --email a@b.c --name "Tên" --role member|lead|admin --password ... [--temp]
 * disable-user --email a@b.c
 * </pre>
 *
 * Thiếu {@code --x} thì đọc biến môi trường {@code CLI_X} (ví dụ {@code CLI_NAME}, {@code
 * CLI_PASSWORD}): trên Windows JVM giải mã đối số dòng lệnh theo code page ANSI nên làm hỏng chữ
 * tiếng Việt, còn biến môi trường được đọc dạng Unicode; đồng thời mật khẩu không lộ trong danh
 * sách tiến trình.
 *
 * <p>{@code search "câu hỏi" [--k 8] [--min-score 0.3]} (T12): xem kết quả truy hồi để chỉnh tham
 * số.
 */
@Component
@Profile("cli")
public class CliRunner implements CommandLineRunner, ExitCodeGenerator {

    static final String USAGE =
            """
            Cách dùng:
              create-user --email <email> --name <tên> --role member|lead|admin --password <mk> [--temp]
              disable-user --email <email>
              search "<câu hỏi>" [--k 8] [--min-score 0.3]
            """;

    private final UserAdminService userAdmin;
    private final PrintStream out;
    private final UnaryOperator<String> env;
    private int exitCode;

    @Autowired
    public CliRunner(UserAdminService userAdmin, Retriever retriever) {
        this(userAdmin, System.out, System::getenv);
        this.retriever = retriever;
    }

    CliRunner(UserAdminService userAdmin, PrintStream out, UnaryOperator<String> env) {
        this.userAdmin = userAdmin;
        this.out = out;
        this.env = env;
    }

    private Retriever retriever;

    void setRetriever(Retriever retriever) {
        this.retriever = retriever;
    }

    /**
     * {@code search "câu hỏi" [--k 8] [--min-score 0.3]}: in n, điểm, tài liệu, trang, 120 ký tự
     * đầu.
     */
    private int search(List<String> args) {
        if (args.size() < 2 || args.get(1).startsWith("--")) {
            out.print(USAGE);
            return 2;
        }
        Parsed p = Parsed.parse(args.subList(2, args.size()), env);
        int k = Integer.parseInt(p.values().getOrDefault("k", "8"));
        double min = Double.parseDouble(p.values().getOrDefault("min-score", "0.3"));
        List<ContextBlock> hits = retriever.search(args.get(1), k, min);
        if (hits.isEmpty()) {
            out.println("(không có đoạn nào vượt ngưỡng " + min + ")");
        }
        for (ContextBlock b : hits) {
            String head = b.text().replaceAll("\\s+", " ");
            out.printf(
                    Locale.ROOT,
                    "[%d] %.3f  %s  tr.%d  %s%n",
                    b.n(),
                    b.score(),
                    b.documentName(),
                    b.page(),
                    head.length() > 120 ? head.substring(0, 120) + "…" : head);
        }
        return 0;
    }

    @Override
    public void run(String... args) {
        exitCode = execute(List.of(args));
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    int execute(List<String> rawArgs) {
        // Bỏ các tham số của Spring (--spring.profiles.active=cli ...)
        List<String> args = rawArgs.stream().filter(a -> !a.startsWith("--spring.")).toList();
        if (args.isEmpty()) {
            out.print(USAGE);
            return 2;
        }
        try {
            if ("search".equals(args.get(0))) {
                return search(args);
            }
            Parsed p = Parsed.parse(args.subList(1, args.size()), env);
            switch (args.get(0)) {
                case "create-user" -> {
                    UserRole role = UserRole.valueOf(p.require("role").toLowerCase(Locale.ROOT));
                    User u =
                            userAdmin.createUser(
                                    p.require("email"),
                                    p.require("name"),
                                    role,
                                    p.require("password"),
                                    p.flags.contains("temp"));
                    out.println("Đã tạo người dùng " + u.getId() + " (" + u.getEmail() + ")");
                }
                case "disable-user" -> {
                    User u = userAdmin.disableUser(p.require("email"));
                    out.println("Đã vô hiệu hóa người dùng " + u.getId());
                }
                default -> {
                    out.print(USAGE);
                    return 2;
                }
            }
            return 0;
        } catch (IllegalArgumentException e) {
            out.println("Lỗi: " + e.getMessage());
            return 1;
        }
    }

    /** {@code --key value} và cờ {@code --flag}; thiếu thì lấy biến môi trường {@code CLI_KEY}. */
    private record Parsed(
            Map<String, String> values, Set<String> flags, UnaryOperator<String> env) {

        static Parsed parse(List<String> args, UnaryOperator<String> env) {
            Map<String, String> values = new HashMap<>();
            Set<String> flags = new HashSet<>();
            for (int i = 0; i < args.size(); i++) {
                String a = args.get(i);
                if (!a.startsWith("--")) {
                    throw new IllegalArgumentException("tham số không hợp lệ: " + a);
                }
                String key = a.substring(2);
                int eq = key.indexOf('=');
                if (eq > 0) {
                    values.put(key.substring(0, eq), key.substring(eq + 1));
                } else if (i + 1 < args.size() && !args.get(i + 1).startsWith("--")) {
                    values.put(key, args.get(++i));
                } else {
                    flags.add(key);
                }
            }
            return new Parsed(values, flags, env);
        }

        String require(String key) {
            String v = values.get(key);
            if (v == null || v.isBlank()) {
                v = env.apply("CLI_" + key.toUpperCase(Locale.ROOT));
            }
            if (v == null || v.isBlank()) {
                throw new IllegalArgumentException("thiếu --" + key);
            }
            return v;
        }
    }
}
