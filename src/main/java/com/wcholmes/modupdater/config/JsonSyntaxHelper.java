package com.wcholmes.modupdater.config;

import com.google.gson.JsonSyntaxException;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Helps diagnose and provide helpful error messages for JSON syntax errors.
 */
public class JsonSyntaxHelper {

    /**
     * Analyzes a JSON syntax error and provides helpful feedback.
     */
    public static void diagnoseJsonError(File jsonFile, JsonSyntaxException error, Logger logger) {
        logger.error("=".repeat(70));
        logger.error("JSON SYNTAX ERROR IN CONFIG FILE");
        logger.error("File: {}", jsonFile.getAbsolutePath());
        logger.error("=".repeat(70));

        // Parse the error message for line number
        String errorMsg = error.getMessage();
        logger.error("Parse Error: {}", errorMsg);

        // Try to extract line number from error message
        Integer lineNumber = extractLineNumber(errorMsg);

        // Read the file and show context around the error
        try {
            List<String> fileLines = readFileLines(jsonFile);

            if (lineNumber != null && lineNumber > 0 && lineNumber <= fileLines.size()) {
                logger.error("");
                logger.error("Error occurred around line {}", lineNumber);
                logger.error("");

                // Show context: 3 lines before and after
                int start = Math.max(1, lineNumber - 3);
                int end = Math.min(fileLines.size(), lineNumber + 3);

                for (int i = start; i <= end; i++) {
                    String line = fileLines.get(i - 1);
                    if (i == lineNumber) {
                        logger.error(">>> Line {}: {}", i, line);
                    } else {
                        logger.error("    Line {}: {}", i, line);
                    }
                }
            }

            // Analyze common JSON mistakes
            analyzeCommonMistakes(fileLines, lineNumber, logger);

        } catch (IOException e) {
            logger.error("Could not read file to show context: {}", e.getMessage());
        }

        logger.error("");
        logger.error("COMMON JSON MISTAKES TO CHECK:");
        logger.error("  1. Missing comma between properties or array elements");
        logger.error("  2. Trailing comma after last property (not allowed in JSON)");
        logger.error("  3. Missing quotes around property names");
        logger.error("  4. Mismatched brackets: {{ }} or [ ]");
        logger.error("  5. Using single quotes ' instead of double quotes \"");
        logger.error("  6. Comments (// or /* */) are not allowed in JSON");
        logger.error("");
        logger.error("TIP: Validate your JSON using an online tool like jsonlint.com");
        logger.error("=".repeat(70));
    }

    /**
     * Reads all lines from a file.
     */
    private static List<String> readFileLines(File file) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    /**
     * Attempts to extract line number from Gson error message.
     */
    private static Integer extractLineNumber(String errorMessage) {
        if (errorMessage == null) {
            return null;
        }

        // Gson typically reports "at line X column Y"
        try {
            if (errorMessage.contains("line ")) {
                String[] parts = errorMessage.split("line ");
                if (parts.length > 1) {
                    String numberPart = parts[1].split(" ")[0];
                    return Integer.parseInt(numberPart);
                }
            }
        } catch (Exception e) {
            // Couldn't parse line number, that's ok
        }

        return null;
    }

    /**
     * Analyzes the file for common JSON mistakes.
     */
    private static void analyzeCommonMistakes(List<String> lines, Integer errorLine, Logger logger) {
        logger.error("");
        logger.error("ANALYSIS OF POSSIBLE ISSUES:");

        boolean foundIssues = false;

        // Check for trailing commas
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();

            // Trailing comma before closing brace or bracket
            if (line.matches(".*,\\s*[}\\]].*")) {
                logger.error("  - Line {}: Trailing comma before closing brace/bracket", i + 1);
                foundIssues = true;
            }

            // Comments (common mistake)
            if (line.contains("//") || line.contains("/*")) {
                logger.error("  - Line {}: Contains comment markers (not allowed in JSON)", i + 1);
                foundIssues = true;
            }

            // Single quotes instead of double quotes (check for key-value pairs)
            if (line.matches(".*'[^']*'\\s*:.*") || line.matches(".*:\\s*'[^']*'.*")) {
                logger.error("  - Line {}: May be using single quotes ' instead of double quotes \"", i + 1);
                foundIssues = true;
            }
        }

        // Check for mismatched brackets
        int braceCount = 0;
        int bracketCount = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            for (char c : line.toCharArray()) {
                if (c == '{') braceCount++;
                if (c == '}') braceCount--;
                if (c == '[') bracketCount++;
                if (c == ']') bracketCount--;
            }
        }

        if (braceCount != 0) {
            logger.error("  - Mismatched braces {{ }}: {} unclosed", braceCount);
            foundIssues = true;
        }

        if (bracketCount != 0) {
            logger.error("  - Mismatched brackets [ ]: {} unclosed", bracketCount);
            foundIssues = true;
        }

        // Check for missing commas (two lines ending with } or ] or value followed by line starting with ")
        for (int i = 0; i < lines.size() - 1; i++) {
            String currentLine = lines.get(i).trim();
            String nextLine = lines.get(i + 1).trim();

            // Line ends with } or ] or " and next line starts with "
            if ((currentLine.endsWith("}") || currentLine.endsWith("]") || currentLine.endsWith("\""))
                && !currentLine.endsWith(",")
                && nextLine.startsWith("\"")
                && !nextLine.equals("}")) {
                logger.error("  - Line {}: May be missing comma at end", i + 1);
                foundIssues = true;
            }
        }

        if (!foundIssues) {
            logger.error("  - No obvious common mistakes detected");
            logger.error("  - The error may be more subtle - try validating with jsonlint.com");
        }
    }
}
