package com.example.drools.dsl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
// import java.util.regex.Matcher;
// import java.util.regex.Pattern;
// import java.util.regex.Matcher;
// import java.util.regex.Pattern;


// public class DslExpander {
//     private final Path dslFile;

//     public DslExpander() throws IOException {
//         this.dslFile = Files.walk(Path.of("rules"))
//                 .filter(p -> p.toString().endsWith(".dsl"))
//                 .findFirst()
//                 .orElseThrow(() -> new IOException("No .dsl file found in rules directory"));
//     }

// public String expand(Path dslrPath) throws IOException {
//         List<String> dslLines = Files.readAllLines(dslFile, StandardCharsets.UTF_8);
//         List<String> dslrLines = Files.readAllLines(dslrPath, StandardCharsets.UTF_8);

//         System.out.println("Loaded DSL lines from: " + dslFile.getFileName());
//         dslLines.forEach(System.out::println);

//         String expanded = String.join("\n", dslrLines);

//         for (String line : dslLines) {
//         if (!line.contains("=")) continue;

//         String cleanLine = line.replaceAll("^\\[.*?\\]\\[.*?\\]", "").trim();
//         String[] parts = cleanLine.split("=", 2);
//         if (parts.length != 2) continue;

//         String key = parts[0].trim();
//         String value = parts[1].trim();

//         System.out.println("\nProcessing DSL line: " + line);
//         System.out.println("Key: " + key);
//         System.out.println("Value: " + value);

//         // Convert {placeholders} in the key to a general matching group
//         String regexKey = key.replaceAll("\\{\\w+}", "(\\\\d+|\\\\w+)");
//         Pattern pattern = Pattern.compile(regexKey);
//         Matcher matcher = pattern.matcher(expanded);
//         StringBuffer sb = new StringBuffer();
//         while (matcher.find()) {
//             // Assume there's only one {placeholder} per key for now
//             // String matchedValue = matcher.group(1); // e.g. "42"
            
//             // // Replace {age}, {name}, etc., with the matched value
//             // String substitutedValue = value.replaceAll("\\{\\w+}", Matcher.quoteReplacement(matchedValue));
            
//             // matcher.appendReplacement(sb, substitutedValue);
//                 if (matcher.groupCount() < 1) continue; // Avoid crash

//                     String matchedValue = matcher.group(1); // First captured group
//                     String substitutedValue = value.replaceAll("\\{\\w+}", Matcher.quoteReplacement(matchedValue));
//                     matcher.appendReplacement(sb, substitutedValue);
        

//             System.out.println("Matched value: " + matchedValue);
//             System.out.println("Substituted value: " + substitutedValue);

//         }
//         // Append the rest of the string after the last match
//         matcher.appendTail(sb);
//         expanded = sb.toString();

//         System.out.println("After applying key: " + key);
//         System.out.println("Expanded content after processing:\n" + expanded);
//     }


//         return expanded;
//     }

//     public String expand2(Path dslrPath) throws IOException {
//         List<String> dslLines = Files.readAllLines(dslFile, StandardCharsets.UTF_8);
//         List<String> dslrLines = Files.readAllLines(dslrPath, StandardCharsets.UTF_8);

//         String expanded = String.join("\n", dslrLines);
//         System.out.println("Expanding DSLR: " + dslrPath.getFileName());
//         System.out.println("Using DSL file: " + dslFile.getFileName());
//         System.out.println("DSL Lines: " + dslLines.size());
//         System.out.println("DSLR Lines: " + dslrLines.size());
//         System.out.println("Expanded content before processing: " + expanded);




//     for (String line : dslLines) {
//         //System.out.println("Processing DSL line: " + line);
        
//         if (!line.contains("=")) continue; // skip metadata headers like [condition][]

//         String[] parts = line.split("=",2);
//         System.out.println("Processing DSL line: " + line);
//         // Ensure we have exactly two parts: key and value
//         System.out.println("Parts: " + parts.length + " - " + String.join(", ", parts));
//         if (parts.length < 2) {
//             System.err.println("Invalid DSL line: " + line);
//             continue; // Skip invalid lines
//         }
//         if (parts.length == 2) {
//             String key = parts[0].trim();
//             key = key.replaceAll("^\\[.*?\\]\\[.*?\\]", "").trim(); // Remove DSL metadata prefix
//             key = key.replaceAll("\\$\\w+", "(.*)");

//             String value = parts[1].trim();

//             expanded = expanded.replaceAll(Pattern.quote(key), value);
//             //  Debug after each expansion
//             System.out.println("After applying key: " + key);
//             System.out.println("Expanded content after processing: \n" + expanded);
//         }
//     }

// System.out.println("Expanded content after processing: " + expanded);
//         return expanded;
//     }
// }

public class DslExpander {

    public static List<DslRule> parseDslRules(Path dslFile) throws IOException {
    List<String> lines = Files.readAllLines(dslFile, StandardCharsets.UTF_8);
    List<DslRule> rules = new ArrayList<>();

    for (String line : lines) {
        if (!line.contains("=")) continue;

        // Remove the DSL section metadata, e.g. [condition][]
        String cleanLine = line.replaceAll("^\\[.*?\\]\\[.*?\\]", "").trim();
        String[] parts = cleanLine.split("=", 2);
        if (parts.length != 2) continue;

        String type = line.contains("[condition]") ? "condition" :
                      line.contains("[consequence]") ? "consequence" :
                      "unknown";

        String key = parts[0].trim();
        String value = parts[1].trim();

        rules.add(new DslRule(type, key, value));
    }

    return rules;
}


    public String expand(String dslrContent, List<DslRule> rules) {
        StringBuilder expanded = new StringBuilder();

        // Track if we're inside the "when" or "then" part of the rule
        boolean inWhen = false;
        boolean inThen = false;

        for (String line : dslrContent.split("\n")) {
            String trimmed = line.trim();

            if (trimmed.startsWith("when")) {
                inWhen = true;
                inThen = false;
                expanded.append(line).append("\n");
                continue;
            } else if (trimmed.startsWith("then")) {
                inWhen = false;
                inThen = true;
                expanded.append(line).append("\n");
                continue;
            } else if (trimmed.startsWith("end")) {
                inWhen = false;
                inThen = false;
                expanded.append(line).append("\n");
                continue;
            }

            String processedLine = line;

            for (DslRule rule : rules) {
                if (inWhen && "condition".equals(rule.getType())) {
                    processedLine = applyRule(processedLine, rule);
                } else if (inThen && "consequence".equals(rule.getType())) {
                    processedLine = applyRule(processedLine, rule);
                }
            }

            expanded.append(processedLine).append("\n");
        }

        return expanded.toString();
    }

    private String applyRule(String line, DslRule rule) {
        String key = rule.getKey();
        String value = rule.getValue();

        if (!key.contains("{")) {
            return line.replace(key, value);
        }

        // Handle placeholder replacement
        String regexKey = key.replaceAll("\\{\\w+}", "(\\\\w+)");
        Pattern pattern = Pattern.compile(regexKey);
        Matcher matcher = pattern.matcher(line);

        if (matcher.find()) {
            String replacedValue = value;
            Matcher keyMatcher = Pattern.compile("\\{(\\w+)}").matcher(key);
            int groupIndex = 1;

            while (keyMatcher.find()) {
                String varName = keyMatcher.group(1);
                String varValue = matcher.group(groupIndex++);
                replacedValue = replacedValue.replace("{" + varName + "}", varValue);
            }

            return matcher.replaceAll(replacedValue);
        }

        return line;
    }
    
}

