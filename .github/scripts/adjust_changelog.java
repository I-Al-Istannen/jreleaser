/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2020-2023 The JReleaser authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static java.lang.String.join;
import static java.lang.System.err;
import static java.lang.System.exit;
import static java.lang.System.lineSeparator;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.readAllLines;
import static java.nio.file.Files.write;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.stream.Collectors.toList;

/**
 * @author Andres Almiray
 * @since 1.5.0
 */
public class adjust_changelog {
    private static final Pattern PATTERN_SHA256 = Pattern.compile("sha256:([\\w\\.\\-]+)");

    public static void main(String[] args) {
        if (null == args || args.length != 2) {
            err.println("Usage: java adjust_changelog <checksumDirectory> <changelogFile>");
            exit(1);
        }

        var checksumDirectory = Path.of(args[0]);
        var changelogFile = Path.of(args[1]);

        if (!exists(checksumDirectory)) {
            err.printf("Checksum directory does not exist. %s%n", checksumDirectory.toAbsolutePath());
            exit(1);
        }

        var checksumsFile = checksumDirectory.resolve("checksums_sha256.txt");
        if (!exists(changelogFile)) {
            err.printf("Checksums file does not exist. %s%n", checksumsFile.toAbsolutePath());
            exit(1);
        }

        if (!exists(changelogFile)) {
            err.printf("Changelog file does not exist. %s%n", changelogFile.toAbsolutePath());
            exit(1);
        }

        exit(process(checksumsFile, changelogFile));
    }

    private static int process(Path checksumsFile, Path changelogFile) {
        var checksums = new LinkedHashMap<String, String>();

        try {
            readAllLines(checksumsFile).forEach(line -> {
                var checksum = line.substring(0, 65).trim();
                var filename = line.substring(66).trim();
                checksums.put(filename, checksum);
            });
        } catch (IOException e) {
            err.printf("Unexpected error reading checksums. %s%n", e.getMessage());
            return 1;
        }

        try {
            List<String> lines = readAllLines(changelogFile).stream()
                .map(line -> replaceChecksums(line, checksums))
                .collect(toList());
            write(changelogFile, join(lineSeparator(), lines).getBytes(), WRITE, TRUNCATE_EXISTING);
        } catch (IOException e) {
            err.printf("Unexpected error replacing checksums. %s%n", e.getMessage());
            return 1;
        }

        return 0;
    }

    private static String replaceChecksums(String line, Map<String, String> checksums) {
        var matcher = PATTERN_SHA256.matcher(line);

        while (matcher.find()) {
            var filename = matcher.group(1);
            if (checksums.containsKey(filename)) {
                line = line.replace("sha256:" + filename, "sha256:`" + checksums.get(filename) + "`");
            } else {
                line = line.replace("sha256:" + filename, "");
            }
        }

        return line;
    }
}