package com.chatvsdoc.api.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.chatvsdoc.api.support.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Context ở profile {@code cli} khởi động được và chạy CliRunner (không có lệnh → in hướng dẫn).
 */
@SpringBootTest(args = "--spring.profiles.active=cli")
@Import(TestcontainersConfig.class)
@ActiveProfiles({"test", "cli"})
class CliProfileContextTest {

    @Autowired CliRunner cli;

    @Test
    void cliRunnerIsWiredAndReportsUsageExitCode() {
        assertThat(cli.getExitCode()).isEqualTo(2);
    }
}
