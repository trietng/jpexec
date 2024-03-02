package org.trietng;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class JavaParallelExecutor {
    private enum ConsoleBackgroundColor {

        RESET("\033[0m"),
        BLACK("\033[40m"),   // BLACK
        RED("\033[41m"),     // RED
        GREEN("\033[42m"),   // GREEN
        YELLOW("\033[43m"),  // YELLOW
        BLUE("\033[44m"),    // BLUE
        MAGENTA("\033[45m"), // MAGENTA
        CYAN("\033[46m"),    // CYAN
        WHITE("\033[47m");

        private final String value;

        ConsoleBackgroundColor(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    private static class CommandExecutor implements Runnable {
        private final ConsoleBackgroundColor backgroundColor;
        private final String prefix;
        private final String command;

        private Process process;

        public CommandExecutor(String backgroundColor, String prefix, String command) {
            this.backgroundColor = ConsoleBackgroundColor.valueOf(backgroundColor);
            this.prefix = prefix.isEmpty() ? ConsoleBackgroundColor.RESET.toString() : prefix + ConsoleBackgroundColor.RESET + " ";
            this.command = command;
            this.process = null;
        }

        public void destroy() {
            this.process.descendants().forEach(ProcessHandle::destroy);
            this.process.destroy();
        }

        public void run() {
            try {
                var processBuilder = new ProcessBuilder(command.split(" "));
                processBuilder.redirectErrorStream(true);
                process = processBuilder.start();
                var reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                System.out.print(ConsoleBackgroundColor.RESET);
                while ((line = reader.readLine()) != null) {
                    System.out.println(backgroundColor + prefix + line);
                }
                process.waitFor();
                this.destroy();
            } catch (IOException | InterruptedException e) {
                System.err.println(e.getMessage());
            }
        }
    }

    private static final String MESSAGE_COMMANDS_LIMIT = "Only support up to 4 commands";
    private static final String MESSAGE_SHUTTING_DOWN = "Shutting down...";
    private static final String ERROR_INVALID_COMMAND = "ERROR Invalid command: ";
    private static final String ERROR_INVALID_BACKGROUND_COLOR = "ERROR Invalid background color: ";
    private static final String ERROR_COMMANDS_ZERO = "ERROR No command to execute";
    private static final String WARNING_INVALID_PRE_HOOK = "WARNING Invalid pre-hook command: ";

    private static final HashSet<String> ALLOWED_SEPARATORS = new HashSet<>() {{
        add(";");
        add(",");
        add(":");
        add("|");
    }};

    private static String SEPARATOR = ";";

    private static boolean isValidSeparator(String sep) {
        return sep.length() == 1 && ALLOWED_SEPARATORS.contains(sep);
    }

    private static void printAllowedOptions() {
        System.out.println("Allowed options: ");
        System.out.println("-h, --help                      show help message");
        System.out.println("-s, --separator, --delimiter    set the separator");
        System.out.println("-pre, --pre, --startup          add a startup command");
    }

    private static void printUsage() {
        System.out.println("Usage: jpexec [options] " +
                "\"<background-color>;<prefix>;<command>\" " +
                "[\"<background-color>;<prefix>;<command>\"...]");
    }

    private static void printAllowedSeparators() {
        System.out.println("Allowed separators: ; , : |");
    }

    private static void printAllowedBackgroundColors() {
        System.out.print("Allowed background colors: ");
        for (var BGC : ConsoleBackgroundColor.values()) {
            System.out.print(BGC + BGC.name() + ConsoleBackgroundColor.RESET + " ");
        }
        System.out.println();
    }

    private static void help() {
        printUsage();
        printAllowedOptions();
        printAllowedSeparators();
        printAllowedBackgroundColors();
    }

    private static void executePreHook(TreeMap<String, String> opts) {
        var value = opts.get("pre");
        if (value != null) {
            var command = value.split(SEPARATOR);
            CommandExecutor ce;
            if (command.length == 1) {
                ce = new CommandExecutor("RESET", "", command[0]);
            }
            else if (command.length == 3) {
                ce = new CommandExecutor(command[0], command[1], command[2]);
            }
            else {
                System.out.print(WARNING_INVALID_PRE_HOOK);
                return;
            }
            ce.run();
            ce.destroy();
        }
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            help();
        }
        else if (args.length > 4) {
            System.out.println(MESSAGE_COMMANDS_LIMIT);
        }
        else {
            var cmds = new ArrayList<String>();
            var opts = new TreeMap<String, String>();
            for (int i = 0; i < args.length; i++) {
                if (args[i].startsWith("-")) {
                    int j = 1;
                    for (; j < args[i].length(); j++) {
                        if (args[i].charAt(j) != '-') {
                            break;
                        }
                    }
                    var opt = args[i].substring(j);
                    if (i + 1 < args.length) {
                        opts.put(opt.toLowerCase(), args[i + 1]);
                        i++;
                    }
                    else if (opt.equals("h") || opt.equals("help")) {
                        help();
                        return;
                    }
                    else {
                        System.out.println("Invalid option: " + opt + " requires a value");
                    }
                }
                else {
                    cmds.add(args[i]);
                }
            }
            if (cmds.isEmpty()) {
                System.out.println(ERROR_COMMANDS_ZERO);
                return;
            }
            for (var opt : opts.entrySet()) {
                var key = opt.getKey();
                switch (key) {
                    case "pre", "startup" -> {
                    }
                    case "s", "separator", "delimiter" -> {
                        var value = opt.getValue();
                        if (isValidSeparator(value)) {
                            SEPARATOR = value;
                        }
                        else {
                            System.out.println("ERROR Invalid separator: " + value);
                            return;
                        }
                    }
                    default -> System.out.println("Invalid option: " + key);
                }
            }
            String[][] commands = new String[cmds.size()][3];
            for (int i = 0; i < cmds.size(); i++) {
                var temp = cmds.get(i).split(SEPARATOR);
                if (temp.length != 3) {
                    System.out.print(ERROR_INVALID_COMMAND);
                    System.out.println(cmds.get(i));
                    return;
                }
                commands[i] = temp;
            }
            ExecutorService executor = Executors.newFixedThreadPool(4);
            CommandExecutor[] ces = new CommandExecutor[cmds.size()];
            for (var i = 0; i < cmds.size(); i++) {
                try {
                    ces[i] = new CommandExecutor(commands[i][0], commands[i][1], commands[i][2]);
                }
                catch (IllegalArgumentException e) {
                    System.out.print(ERROR_INVALID_BACKGROUND_COLOR);
                    System.out.println(commands[i][0]);
                    printAllowedBackgroundColors();
                    return;
                }
            }
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println(MESSAGE_SHUTTING_DOWN);
                for (var ce : ces) {
                    ce.destroy();
                }
            }));
            executePreHook(opts);
            for (var ce : ces) {
                executor.execute(ce);
            }
            executor.shutdown();
        }
    }
}